// Worker loop: claims queued jobs from Supabase, produces the video, uploads it.
import 'dotenv/config';
import fs from 'node:fs/promises';
import { createClient } from '@supabase/supabase-js';
import { produceVideo } from './pipeline.js';

for (const k of ['SUPABASE_URL', 'SUPABASE_SERVICE_ROLE_KEY', 'OPENAI_API_KEY']) {
  if (!process.env[k]) {
    console.error(`Missing env var ${k} (see .env.example)`);
    process.exit(1);
  }
}

const db = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});
const POLL_MS = Number(process.env.POLL_SECONDS || 5) * 1000;

async function update(id, fields) {
  const { error } = await db.from('videos').update({ ...fields, updated_at: new Date().toISOString() }).eq('id', id);
  if (error) throw new Error(`DB update failed: ${error.message}`);
}

async function processJob(job) {
  const { data: series, error } = await db.from('series').select('*').eq('id', job.series_id).single();
  if (error) throw new Error(`Series lookup failed: ${error.message}`);

  const { data: recent } = await db
    .from('videos').select('title').eq('series_id', series.id).not('title', 'is', null)
    .order('created_at', { ascending: false }).limit(20);

  const result = await produceVideo(job, series, {
    recentTitles: (recent || []).map((r) => r.title),
    setStatus: (status, progress, extra = {}) => update(job.id, { status, progress, ...extra }),
    downloadMusic: async (url, dest) => {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`Music download failed: HTTP ${res.status}`);
      await fs.writeFile(dest, Buffer.from(await res.arrayBuffer()));
    },
  });

  const base = `${job.user_id}/${job.id}`;
  for (const [file, key, type] of [
    [result.videoFile, `${base}.mp4`, 'video/mp4'],
    [result.thumbFile, `${base}.jpg`, 'image/jpeg'],
  ]) {
    const { error: upErr } = await db.storage.from('videos').upload(key, await fs.readFile(file), { contentType: type, upsert: true });
    if (upErr) throw new Error(`Upload failed: ${upErr.message}`);
  }
  await update(job.id, { status: 'done', progress: 100, video_path: `${base}.mp4`, thumbnail_path: `${base}.jpg`, error: null });
  await fs.rm(result.workDir, { recursive: true, force: true });
}

async function refundCredit(job) {
  if (!job.used_credit) return; // free mode: nothing was spent (failed videos don't count toward daily limits)
  const { data } = await db.from('profiles').select('credits').eq('id', job.user_id).single();
  if (data) await db.from('profiles').update({ credits: data.credits + 1 }).eq('id', job.user_id);
}

async function loop() {
  console.log('Worker started, polling every', POLL_MS / 1000, 's');
  for (;;) {
    const { data, error } = await db.rpc('claim_next_video');
    if (error) {
      console.error('Claim failed:', error.message);
    } else if (data && data.length) {
      const job = data[0];
      console.log(`[${job.id}] started`);
      try {
        await processJob(job);
        console.log(`[${job.id}] done`);
      } catch (e) {
        console.error(`[${job.id}] failed:`, e);
        await update(job.id, { status: 'failed', error: String(e.message || e).slice(0, 500) }).catch(() => {});
        await refundCredit(job).catch(() => {});
      }
      continue; // check for the next job immediately
    }
    await new Promise((r) => setTimeout(r, POLL_MS));
  }
}

loop();
