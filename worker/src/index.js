// MyReels render worker. Claims queued videos from Supabase and renders them.
// Run: node src/index.js  (env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, GEMINI_API_KEY, PEXELS_API_KEY)
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import * as db from './supa.js';
import { generateScript, synthesize, fetchClip, styleHint } from './providers.js';
import { renderReel, duration } from './render.js';

const MAX_RUNTIME_MS = (Number(process.env.MAX_RUNTIME_MIN) || 20) * 60_000;
const RETENTION_DAYS = Number(process.env.RETENTION_DAYS) || 0; // 0 = never delete
const MAX_BYTES = 50 * 1024 * 1024; // Supabase free plan per-file limit

const setStatus = (id, status, progress, extra = {}) =>
  db.patch('videos', `id=eq.${id}`, { status, progress, updated_at: new Date().toISOString(), ...extra });

async function recoverStale() {
  const cutoff = new Date(Date.now() - 30 * 60_000).toISOString();
  await db.patch('videos',
    `status=in.(scripting,imaging,voicing,rendering)&locked_at=lt.${cutoff}`,
    { status: 'queued', progress: 0 });
}

async function cleanupOld() {
  if (!RETENTION_DAYS) return;
  const cutoff = new Date(Date.now() - RETENTION_DAYS * 86_400_000).toISOString();
  const old = await db.select('videos', `created_at=lt.${cutoff}&video_path=not.is.null&select=id,video_path,thumbnail_path&limit=100`);
  if (!old.length) return;
  await db.removeObjects('videos', old.flatMap((v) => [v.video_path, v.thumbnail_path]).filter(Boolean));
  await db.patch('videos', `id=in.(${old.map((v) => v.id).join(',')})`, { video_path: null, thumbnail_path: null, status: 'failed', error: 'Expired' });
  console.log(`Cleaned up ${old.length} expired videos`);
}

async function processJob(job) {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'reel-'));
  console.log(`[${job.id}] start`);
  try {
    const [series] = await db.select('series', `id=eq.${job.series_id}&select=*`);
    if (!series) throw new Error('Series not found');

    // 1. Script
    await setStatus(job.id, 'scripting', 10);
    const script = await generateScript(series);
    await setStatus(job.id, 'scripting', 25, { title: script.title, script });

    // 2. Voiceover per scene (gives exact scene lengths)
    await setStatus(job.id, 'voicing', 30);
    const scenes = [];
    for (let i = 0; i < script.scenes.length; i++) {
      const audio = path.join(dir, `voice_${i}.mp3`);
      await synthesize(script.scenes[i].narration, series.voice, audio);
      scenes.push({ audio, text: script.scenes[i].narration, dur: await duration(audio) });
      await setStatus(job.id, 'voicing', 30 + Math.round(((i + 1) / script.scenes.length) * 20));
    }

    // 3. Stock clips per scene
    await setStatus(job.id, 'imaging', 50);
    const used = new Set();
    const hint = styleHint(series);
    for (let i = 0; i < scenes.length; i++) {
      const q = script.scenes[i].search_query;
      scenes[i].clip = path.join(dir, `clip_${i}.mp4`);
      await fetchClip({
        queries: [`${q} ${hint}`.trim(), q, hint || 'nature landscape'],
        minDuration: scenes[i].dur + 0.35,
        used,
        outFile: scenes[i].clip,
      });
      await setStatus(job.id, 'imaging', 50 + Math.round(((i + 1) / scenes.length) * 20));
    }

    // 4. Render
    await setStatus(job.id, 'rendering', 72);
    const { video, thumb } = await renderReel({ dir, scenes, musicUrl: series.music_url });
    const videoBuf = await fs.readFile(video);
    if (videoBuf.length > MAX_BYTES) throw new Error('Rendered video is over the 50 MB storage limit');
    await setStatus(job.id, 'rendering', 92);

    // 5. Upload + finish
    const videoPath = `${job.user_id}/${job.id}.mp4`;
    const thumbPath = `${job.user_id}/${job.id}.jpg`;
    await db.upload('videos', videoPath, videoBuf, 'video/mp4');
    await db.upload('videos', thumbPath, await fs.readFile(thumb), 'image/jpeg');
    await setStatus(job.id, 'done', 100, { video_path: videoPath, thumbnail_path: thumbPath, error: null });
    console.log(`[${job.id}] done (${(videoBuf.length / 1e6).toFixed(1)} MB)`);
  } catch (e) {
    console.error(`[${job.id}] failed:`, e.message);
    await setStatus(job.id, 'failed', 0, { error: String(e.message).slice(0, 300) }).catch(() => {});
    if (job.used_credit) await refundCredit(job.user_id).catch(() => {});
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
}

async function refundCredit(userId) {
  const [p] = await db.select('profiles', `id=eq.${userId}&select=credits`);
  if (p) await db.patch('profiles', `id=eq.${userId}`, { credits: p.credits + 1 });
}

async function main() {
  for (const k of ['SUPABASE_URL', 'SUPABASE_SERVICE_ROLE_KEY']) {
    if (!process.env[k]) throw new Error(`${k} is not set`);
  }
  await recoverStale();
  const start = Date.now();
  while (Date.now() - start < MAX_RUNTIME_MS) {
    const [job] = (await db.rpc('claim_next_video')) || [];
    if (!job) break;
    await processJob(job);
  }
  await cleanupOld();
  console.log('Queue empty, exiting.');
}

main().catch((e) => { console.error(e); process.exit(1); });
