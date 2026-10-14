// External providers, each behind one function so they can be swapped for paid ones later:
//   generateScript()  Gemini (free tier)         -> swap for any LLM
//   fetchClip()       Pexels/Pixabay stock video -> swap for Veo / Kling / Runway
//   synthesize()      edge-tts (free)            -> swap for OpenAI TTS / ElevenLabs
import fs from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { spawn } from 'node:child_process';
import path from 'node:path';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const NICHE_LABELS = {
  scary_stories: 'Scary stories (short, creepy, twist ending)',
  history: 'Surprising history facts',
  motivation: 'Motivation and discipline',
  fun_facts: 'Mind-blowing fun facts',
  bible_stories: 'Bible stories told respectfully',
  true_crime: 'Unsolved mysteries',
  philosophy: 'Stoic philosophy applied to daily life',
};

// Stock footage cannot do anime/pixel/etc, so art style only nudges the search toward a mood.
const STYLE_HINT = {
  cinematic: 'cinematic',
  dark_fantasy: 'dark moody',
  watercolor: 'soft pastel',
  sketch: 'black and white',
};

// edge-tts voices mapped from the app's voice ids.
const VOICES = {
  onyx: 'en-US-GuyNeural',
  echo: 'en-US-ChristopherNeural',
  nova: 'en-US-JennyNeural',
  shimmer: 'en-US-MichelleNeural',
  fable: 'en-GB-RyanNeural',
  alloy: 'en-US-AndrewNeural',
};

// ---------- Script (Gemini) ----------
export async function generateScript(series) {
  const key = process.env.GEMINI_API_KEY;
  if (!key) throw new Error('GEMINI_API_KEY is not set');
  const model = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
  const target = series.target_seconds || 45;
  const words = Math.round(target * 2.4);
  const sceneCount = Math.min(12, Math.max(3, Math.round(target / 6)));
  const niche = series.niche === 'custom'
    ? `Custom topic: ${series.custom_prompt || 'interesting facts'}`
    : NICHE_LABELS[series.niche] || series.niche;

  const prompt = `You are a viral short-form video writer for TikTok, YouTube Shorts and Instagram Reels.
Write a faceless video script.
Niche: ${niche}
${series.custom_prompt && series.niche !== 'custom' ? `Extra direction: ${series.custom_prompt}\n` : ''}Total narration length: about ${words} words (${target} seconds when spoken).
Split it into exactly ${sceneCount} scenes. The first scene must be a strong hook.
Return ONLY JSON:
{"title":"hook under 8 words","caption":"social caption ending with a question","hashtags":["4 to 6 tags without #"],
 "scenes":[{"narration":"spoken text only, no stage directions","search_query":"2-4 English words describing a real filmable scene"}]}
search_query rules: concrete, visual, filmable stock footage (e.g. "foggy forest night", "city skyline sunrise"); never names of people, brands or abstract ideas.`;

  let lastErr;
  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { temperature: 0.8, responseMimeType: 'application/json' },
        }),
      });
      if (res.status === 429 || res.status >= 500) throw new Error(`Gemini ${res.status}`);
      if (!res.ok) throw new Error(`Gemini ${res.status}: ${(await res.text()).slice(0, 200)}`);
      const data = await res.json();
      const text = data.candidates?.[0]?.content?.parts?.map((p) => p.text || '').join('') || '';
      return validateScript(JSON.parse(text.replace(/^```json|```$/gim, '').trim()));
    } catch (e) {
      lastErr = e;
      if (attempt < 4) await sleep(attempt * 4000);
    }
  }
  throw new Error(`Script generation failed: ${lastErr.message}`);
}

function validateScript(s) {
  const scenes = (s.scenes || [])
    .map((x) => ({ narration: String(x.narration || '').trim(), search_query: String(x.search_query || '').trim() }))
    .filter((x) => x.narration);
  if (!scenes.length) throw new Error('Script had no scenes');
  return {
    title: String(s.title || 'Untitled').slice(0, 100),
    caption: String(s.caption || '').slice(0, 500),
    hashtags: (Array.isArray(s.hashtags) ? s.hashtags : []).map((h) => String(h).replace(/^#/, '')).slice(0, 8),
    scenes,
  };
}

export const styleHint = (series) => STYLE_HINT[series.art_style] || '';

// ---------- Voice (edge-tts) ----------
export async function synthesize(text, voiceId, outFile) {
  const voice = VOICES[voiceId] || VOICES.onyx;
  const textFile = outFile + '.txt';
  await fs.writeFile(textFile, text);
  const bin = process.env.EDGE_TTS_BIN || 'edge-tts';
  let lastErr;
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      await run(bin, ['--file', textFile, '--voice', voice, '--write-media', outFile]);
      const st = await fs.stat(outFile);
      if (st.size > 0) return;
      throw new Error('empty audio');
    } catch (e) {
      lastErr = e;
      await sleep(attempt * 2000);
    }
  }
  throw new Error(`Voiceover failed: ${lastErr.message}`);
}

// ---------- Stock clips (Pexels or Pixabay) ----------
export function fetchClip(args) {
  // Pexels (portrait clips) when its key exists, otherwise Pixabay.
  return process.env.PEXELS_API_KEY ? fetchPexels(args) : fetchPixabay(args);
}

async function fetchPixabay({ queries, minDuration, used, outFile }) {
  const key = process.env.PIXABAY_API_KEY;
  if (!key) throw new Error('Set PEXELS_API_KEY or PIXABAY_API_KEY');
  for (const q of queries.filter(Boolean)) {
    const url = new URL('https://pixabay.com/api/videos/');
    url.searchParams.set('key', key);
    url.searchParams.set('q', q.slice(0, 100));
    url.searchParams.set('per_page', '20');
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Pixabay ${res.status}`);
    const { hits = [] } = await res.json();
    const fresh = hits.filter((v) => !used.has(v.id) && v.duration >= 3);
    const long = fresh.filter((v) => v.duration >= minDuration);
    const pool = (long.length ? long : fresh).slice(0, 8);
    if (!pool.length) continue;
    const pick = pool[Math.floor(Math.random() * pool.length)];
    const file = ['large', 'medium', 'small']
      .map((k) => pick.videos?.[k])
      .find((f) => f && f.url && (!f.size || f.size <= 60 * 1024 * 1024));
    if (!file) continue;
    used.add(pick.id);
    await download(file.url, outFile);
    return;
  }
  throw new Error(`No stock clip found for: ${queries.filter(Boolean).join(' | ')}`);
}

async function fetchPexels({ queries, minDuration, used, outFile }) {
  const key = process.env.PEXELS_API_KEY;

  for (const q of queries.filter(Boolean)) {
    for (const orientation of ['portrait', null]) {
      const url = new URL('https://api.pexels.com/videos/search');
      url.searchParams.set('query', q);
      url.searchParams.set('per_page', '15');
      url.searchParams.set('size', 'medium');
      if (orientation) url.searchParams.set('orientation', orientation);
      const res = await fetch(url, { headers: { Authorization: key } });
      if (!res.ok) throw new Error(`Pexels ${res.status}`);
      const { videos = [] } = await res.json();

      const fresh = videos.filter((v) => !used.has(v.id) && v.duration >= 3);
      const long = fresh.filter((v) => v.duration >= minDuration);
      const pool = (long.length ? long : fresh).slice(0, 8);
      if (!pool.length) continue;
      const pick = pool[Math.floor(Math.random() * pool.length)];
      const file = bestFile(pick.video_files || []);
      if (!file) continue;

      used.add(pick.id);
      await download(file.link, outFile);
      return;
    }
  }
  throw new Error(`No stock clip found for: ${queries.filter(Boolean).join(' | ')}`);
}

function bestFile(files) {
  const mp4 = files.filter((f) => f.file_type === 'video/mp4' && f.link);
  const portrait = mp4.filter((f) => f.height > f.width && f.height >= 1280).sort((a, b) => a.height - b.height);
  if (portrait.length) return portrait[0];
  const any = mp4.filter((f) => f.height >= 1080).sort((a, b) => a.height - b.height);
  return any[0] || mp4.sort((a, b) => b.height - a.height)[0];
}

export async function download(url, outFile) {
  const res = await fetch(url);
  if (!res.ok || !res.body) throw new Error(`Download failed ${res.status}`);
  await pipeline(Readable.fromWeb(res.body), createWriteStream(outFile));
}

export function run(cmd, args, opts = {}) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'], ...opts });
    let out = '', err = '';
    p.stdout.on('data', (d) => (out += d));
    p.stderr.on('data', (d) => (err += d));
    p.on('error', reject);
    p.on('close', (code) =>
      code === 0 ? resolve(out) : reject(new Error(`${path.basename(cmd)} exited ${code}: ${err.slice(-400)}`)));
  });
}
