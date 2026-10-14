// Full pipeline for one video job.
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { writeScript, generateImage, speak, transcribeWords } from './ai.js';
import { buildAss, estimateWords } from './captions.js';
import { audioDuration, renderVideo } from './render.js';

/**
 * @param job      videos row
 * @param series   series row
 * @param deps     { setStatus(status, progress, extra?), recentTitles: string[], downloadMusic?(url, dest) }
 * @returns { videoFile, thumbFile, script }
 */
export async function produceVideo(job, series, deps) {
  const workDir = await fs.mkdtemp(path.join(os.tmpdir(), `job-${job.id}-`));

  // 1. Script
  await deps.setStatus('scripting', 5);
  const script = await writeScript(series, deps.recentTitles);
  await deps.setStatus('imaging', 15, { script, title: script.title });

  // 2. Images (parallel, small concurrency to respect rate limits)
  const images = new Array(script.scenes.length);
  let done = 0;
  const queue = script.scenes.map((s, i) => async () => {
    images[i] = await generateImage(s.image_prompt, series.art_style, path.join(workDir, `img_${i}.png`));
    done++;
    await deps.setStatus('imaging', 15 + Math.round((done / script.scenes.length) * 40));
  });
  await runLimited(queue, 3);

  // 3. Voice per scene (gives exact per-scene durations for cuts)
  await deps.setStatus('voicing', 60);
  const scenes = [];
  let t = 0;
  for (let i = 0; i < script.scenes.length; i++) {
    const audio = await speak(script.scenes[i].narration, series.voice, path.join(workDir, `vo_${i}.mp3`));
    const duration = await audioDuration(audio);
    scenes.push({ image: images[i], audio, duration, start: t, narration: script.scenes[i].narration });
    t += duration;
  }

  // 4. Captions
  await deps.setStatus('rendering', 75);
  let words;
  if ((process.env.CAPTIONS_MODE || 'whisper') === 'whisper') {
    const full = path.join(workDir, 'full.mp3');
    // concat narration once for transcription
    const list = path.join(workDir, 'vo.txt');
    await fs.writeFile(list, scenes.map((s) => `file '${s.audio}'`).join('\n'));
    const { run } = await import('./render.js');
    await run('ffmpeg', ['-y', '-f', 'concat', '-safe', '0', '-i', list, '-c:a', 'libmp3lame', full]);
    words = await transcribeWords(full, series.language);
  } else {
    words = estimateWords(scenes);
  }
  const assPath = path.join(workDir, 'captions.ass');
  await fs.writeFile(assPath, buildAss(words));

  // 5. Optional music
  let musicPath;
  if (series.music_url && deps.downloadMusic) {
    musicPath = path.join(workDir, 'music' + path.extname(new URL(series.music_url).pathname || '.mp3'));
    await deps.downloadMusic(series.music_url, musicPath);
  }

  // 6. Render
  const { video, thumbnail } = await renderVideo(scenes, {
    workDir,
    assPath,
    musicPath,
    outPath: path.join(workDir, 'final.mp4'),
  });
  await deps.setStatus('rendering', 95);
  return { videoFile: video, thumbFile: thumbnail, script, workDir };
}

async function runLimited(tasks, limit) {
  const running = new Set();
  for (const task of tasks) {
    const p = task().finally(() => running.delete(p));
    running.add(p);
    if (running.size >= limit) await Promise.race(running);
  }
  await Promise.all(running);
}
