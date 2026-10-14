// Offline smoke test for the renderer: no API keys needed.
// Generates fake scene images + tones, estimates captions, renders a full video.
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { run, audioDuration, renderVideo } from '../src/render.js';
import { estimateWords, buildAss } from '../src/captions.js';

const out = process.argv[2] || path.join(os.tmpdir(), 'smoke.mp4');
const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'smoke-'));
const narr = [
  'Did you know octopuses have three hearts?',
  'Two pump blood to the gills, one to the body.',
  'And their blood is blue.',
];
const colors = ['0x8a2be2', '0x1e90ff', '0x228b22'];
const scenes = [];
let t = 0;
for (let i = 0; i < narr.length; i++) {
  const image = path.join(dir, `img_${i}.png`);
  await run('ffmpeg', ['-y', '-f', 'lavfi', '-i', `color=c=${colors[i]}:s=1024x1536,drawgrid=w=128:h=128:t=4:c=white@0.4`, '-frames:v', '1', image]);
  const audio = path.join(dir, `vo_${i}.mp3`);
  await run('ffmpeg', ['-y', '-f', 'lavfi', '-i', `sine=frequency=${300 + i * 100}:duration=${2 + i}`, '-c:a', 'libmp3lame', audio]);
  const duration = await audioDuration(audio);
  scenes.push({ image, audio, duration, start: t, narration: narr[i] });
  t += duration;
}
const music = path.join(dir, 'music.mp3');
await run('ffmpeg', ['-y', '-f', 'lavfi', '-i', 'sine=frequency=110:duration=2', '-c:a', 'libmp3lame', music]);
const assPath = path.join(dir, 'captions.ass');
await fs.writeFile(assPath, buildAss(estimateWords(scenes)));
const res = await renderVideo(scenes, { workDir: dir, assPath, musicPath: music, outPath: out });
console.log(JSON.stringify({ ...res, expectedSeconds: t }));
