// All AI calls (one OpenAI account covers script, images, voice and captions).
import fs from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import OpenAI from 'openai';
import { nicheFor, styleFor } from './styles.js';

const openai = new OpenAI(); // reads OPENAI_API_KEY
const env = (k, d) => process.env[k] || d;

/** Write a short-form script split into scenes. Returns {title, caption, hashtags, scenes:[{narration,image_prompt}]} */
export async function writeScript(series, recentTitles = []) {
  const words = Math.round(series.target_seconds * 2.5); // ~150 wpm
  const scenes = Math.max(4, Math.round(series.target_seconds / 6));
  const res = await openai.chat.completions.create({
    model: env('SCRIPT_MODEL', 'gpt-4o-mini'),
    response_format: { type: 'json_object' },
    temperature: 0.9,
    messages: [
      {
        role: 'system',
        content:
          'You write scripts for viral faceless short videos (TikTok/Reels/Shorts). ' +
          'Open with a strong hook in the first sentence. No intros like "In this video". ' +
          'Narration is spoken by a voiceover; never reference visuals or the viewer\'s screen. ' +
          'Return strict JSON only.',
      },
      {
        role: 'user',
        content: JSON.stringify({
          topic: nicheFor(series),
          language: series.language,
          total_words: words,
          scene_count: scenes,
          avoid_repeating_titles: recentTitles,
          output_format: {
            title: 'short catchy title',
            caption: 'social caption, 1-2 sentences',
            hashtags: ['5 relevant hashtags without #'],
            scenes: [{ narration: 'what the voice says in this scene', image_prompt: 'concrete visual description of this scene, no text in image' }],
          },
        }),
      },
    ],
  });
  const data = JSON.parse(res.choices[0].message.content);
  if (!Array.isArray(data.scenes) || data.scenes.length === 0) throw new Error('Script has no scenes');
  for (const s of data.scenes) {
    if (typeof s.narration !== 'string' || typeof s.image_prompt !== 'string') throw new Error('Malformed scene in script');
  }
  return data;
}

/** Generate one portrait image per scene. */
export async function generateImage(prompt, artStyle, outPath) {
  const res = await openai.images.generate({
    model: env('IMAGE_MODEL', 'gpt-image-1-mini'),
    prompt: `${prompt}. Style: ${styleFor(artStyle)}. Vertical composition, no text, no watermark, no captions.`,
    size: '1024x1536',
    quality: env('IMAGE_QUALITY', 'low'),
    n: 1,
  });
  const b64 = res.data?.[0]?.b64_json;
  if (!b64) throw new Error('Image API returned no image');
  await fs.writeFile(outPath, Buffer.from(b64, 'base64'));
  return outPath;
}

/** Text-to-speech for one scene -> mp3 file. */
export async function speak(text, voice, outPath) {
  const res = await openai.audio.speech.create({
    model: env('TTS_MODEL', 'gpt-4o-mini-tts'),
    voice,
    input: text,
    instructions: 'Engaging storyteller voice for a short-form video. Natural pace, slight energy.',
    response_format: 'mp3',
  });
  await fs.writeFile(outPath, Buffer.from(await res.arrayBuffer()));
  return outPath;
}

/** Word-level timestamps for captions. Returns [{word,start,end}] */
export async function transcribeWords(audioPath, language) {
  const res = await openai.audio.transcriptions.create({
    model: 'whisper-1',
    file: createReadStream(audioPath),
    language,
    response_format: 'verbose_json',
    timestamp_granularities: ['word'],
  });
  if (!Array.isArray(res.words)) throw new Error('Transcription returned no word timings');
  return res.words.map((w) => ({ word: w.word, start: w.start, end: w.end }));
}
