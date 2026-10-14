// FFmpeg rendering: one 1080x1920 scene per (clip + voiceover) with burned-in captions, then join + music.
import fs from 'node:fs/promises';
import path from 'node:path';
import { run, download } from './providers.js';

const W = 1080, H = 1920, PAD = 0.35;

export async function duration(file) {
  const out = await run('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]);
  return parseFloat(out.trim());
}

const ts = (s) => {
  const cs = Math.round(s * 100);
  const h = Math.floor(cs / 360000), m = Math.floor((cs % 360000) / 6000);
  const sec = Math.floor((cs % 6000) / 100), c = cs % 100;
  return `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}.${String(c).padStart(2, '0')}`;
};

// Split narration into short caption chunks, timed in proportion to their length.
export function buildAss(text, dur) {
  const words = text.replace(/\s+/g, ' ').trim().split(' ').filter(Boolean);
  const chunks = [];
  let cur = [];
  for (const w of words) {
    cur.push(w);
    if (cur.length >= 4 || /[.!?,;:]$/.test(w)) { chunks.push(cur.join(' ')); cur = []; }
  }
  if (cur.length) chunks.push(cur.join(' '));
  const total = chunks.reduce((a, c) => a + c.length, 0) || 1;
  const speech = Math.max(0.5, dur - PAD);
  let t = 0;
  const lines = chunks.map((c) => {
    const len = (c.length / total) * speech;
    const line = `Dialogue: 0,${ts(t)},${ts(t + len)},Default,,0,0,0,,${c.toUpperCase().replace(/[{}\\]/g, '')}`;
    t += len;
    return line;
  });
  return `[Script Info]
ScriptType: v4.00+
PlayResX: ${W}
PlayResY: ${H}
WrapStyle: 0

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,DejaVu Sans,78,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,7,2,2,70,70,420,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
${lines.join('\n')}
`;
}

async function renderScene({ dir, i, clip, audio, text }) {
  const dur = (await duration(audio)) + PAD;
  const ass = `scene_${i}.ass`;
  await fs.writeFile(path.join(dir, ass), buildAss(text, dur));
  const out = `scene_${i}.mp4`;
  await run('ffmpeg', [
    '-y', '-loglevel', 'error',
    '-stream_loop', '-1', '-i', path.basename(clip),
    '-i', path.basename(audio),
    '-vf', `scale=${W}:${H}:force_original_aspect_ratio=increase,crop=${W}:${H},setsar=1,fps=30,ass=${ass}`,
    '-af', 'apad,aresample=44100',
    '-map', '0:v:0', '-map', '1:a:0',
    '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '24', '-pix_fmt', 'yuv420p',
    '-c:a', 'aac', '-b:a', '128k', '-ar', '44100', '-ac', '2',
    '-t', dur.toFixed(2),
    out,
  ], { cwd: dir });
  return out;
}

export async function renderReel({ dir, scenes, musicUrl }) {
  const parts = [];
  for (let i = 0; i < scenes.length; i++) {
    parts.push(await renderScene({ dir, i, ...scenes[i] }));
  }
  await fs.writeFile(path.join(dir, 'list.txt'), parts.map((p) => `file '${p}'`).join('\n'));
  await run('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', 'list.txt',
    '-c', 'copy', '-movflags', '+faststart', 'joined.mp4'], { cwd: dir });

  let final = 'joined.mp4';
  if (musicUrl && /^https?:\/\//.test(musicUrl)) {
    try {
      await download(musicUrl, path.join(dir, 'music.bin'));
      await run('ffmpeg', ['-y', '-loglevel', 'error', '-i', 'joined.mp4', '-stream_loop', '-1', '-i', 'music.bin',
        '-filter_complex', '[1:a]volume=0.12[m];[0:a][m]amix=inputs=2:duration=first:dropout_transition=0[a]',
        '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '128k',
        '-movflags', '+faststart', 'final.mp4'], { cwd: dir });
      final = 'final.mp4';
    } catch (e) {
      console.warn('Music mix skipped:', e.message);
    }
  }
  await run('ffmpeg', ['-y', '-loglevel', 'error', '-ss', '0.8', '-i', final, '-frames:v', '1', '-q:v', '3', 'thumb.jpg'], { cwd: dir });
  return { video: path.join(dir, final), thumb: path.join(dir, 'thumb.jpg') };
}
