// ffmpeg-based renderer: images + narration (+ optional music) + captions -> 1080x1920 MP4
import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';

const FPS = 30;

export function run(cmd, args) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    let err = '';
    p.stdout.on('data', (d) => (out += d));
    p.stderr.on('data', (d) => (err += d));
    p.on('error', reject);
    p.on('close', (code) =>
      code === 0 ? resolve(out) : reject(new Error(`${cmd} exited ${code}: ${err.slice(-1500)}`)),
    );
  });
}

export async function audioDuration(file) {
  const out = await run('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]);
  const d = parseFloat(out);
  if (!Number.isFinite(d) || d <= 0) throw new Error(`Could not read duration of ${file}`);
  return d;
}

/** One slow Ken Burns clip per scene; alternates zoom-in / zoom-out for variety. */
async function sceneClip(image, seconds, index, outPath) {
  const frames = Math.max(1, Math.round(seconds * FPS));
  const step = (0.15 / frames).toFixed(6);
  const zoom = index % 2 === 0 ? `min(zoom+${step},1.15)` : `if(eq(on,0),1.15,max(zoom-${step},1.0))`;
  const vf = [
    'scale=2160:3840:force_original_aspect_ratio=increase',
    'crop=2160:3840',
    `zoompan=z='${zoom}':d=${frames}:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=1080x1920:fps=${FPS}`,
    'format=yuv420p',
  ].join(',');
  await run('ffmpeg', [
    '-y', '-i', image, '-vf', vf, '-frames:v', String(frames),
    '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-an', outPath,
  ]);
  return outPath;
}

/**
 * scenes: [{image, audio, duration}] (duration = audio length in seconds)
 * opts: { workDir, assPath, musicPath?, outPath }
 */
export async function renderVideo(scenes, { workDir, assPath, musicPath, outPath }) {
  // 1. Per-scene video clips
  const clips = [];
  for (let i = 0; i < scenes.length; i++) {
    clips.push(await sceneClip(scenes[i].image, scenes[i].duration, i, path.join(workDir, `clip_${i}.mp4`)));
  }
  const vlist = path.join(workDir, 'clips.txt');
  await fs.writeFile(vlist, clips.map((c) => `file '${c}'`).join('\n'));

  // 2. Narration track (concat scene audio, normalised to one format)
  const alist = path.join(workDir, 'audio.txt');
  await fs.writeFile(alist, scenes.map((s) => `file '${s.audio}'`).join('\n'));
  const narration = path.join(workDir, 'narration.m4a');
  await run('ffmpeg', ['-y', '-f', 'concat', '-safe', '0', '-i', alist, '-ar', '44100', '-ac', '2', '-c:a', 'aac', narration]);

  // 3. Final mux: video + narration (+ music ducked under voice) + burned captions
  const assEsc = assPath.replace(/\\/g, '/').replace(/:/g, '\\:').replace(/'/g, "\\'");
  const args = ['-y', '-f', 'concat', '-safe', '0', '-i', vlist, '-i', narration];
  let filter = `[0:v]subtitles='${assEsc}'[v]`;
  let amap = '1:a';
  if (musicPath) {
    args.push('-stream_loop', '-1', '-i', musicPath);
    filter +=
      ';[2:a]volume=0.12[m];[1:a][m]amix=inputs=2:duration=first:dropout_transition=0[a]';
    amap = '[a]';
  }
  args.push(
    '-filter_complex', filter, '-map', '[v]', '-map', amap,
    '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '21', '-pix_fmt', 'yuv420p',
    '-c:a', 'aac', '-b:a', '160k', '-shortest', '-movflags', '+faststart', outPath,
  );
  await run('ffmpeg', args);

  // 4. Thumbnail from the first second
  const thumb = outPath.replace(/\.mp4$/, '.jpg');
  await run('ffmpeg', ['-y', '-ss', '0.5', '-i', outPath, '-frames:v', '1', '-q:v', '3', thumb]);
  return { video: outPath, thumbnail: thumb };
}
