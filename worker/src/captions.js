// Builds an .ass subtitle file in the "big bold word chunks" style used by faceless channels.

const pad = (n, w = 2) => String(n).padStart(w, '0');
function assTime(sec) {
  const cs = Math.max(0, Math.round(sec * 100));
  const h = Math.floor(cs / 360000);
  const m = Math.floor((cs % 360000) / 6000);
  const s = Math.floor((cs % 6000) / 100);
  return `${h}:${pad(m)}:${pad(s)}.${pad(cs % 100)}`;
}
const esc = (t) => t.replace(/[{}\\]/g, '').replace(/\n/g, ' ');

/**
 * Fallback when no word timings: spread each scene's words evenly over that scene's audio duration.
 * scenes: [{narration, start, duration}]
 */
export function estimateWords(scenes) {
  const out = [];
  for (const sc of scenes) {
    const words = sc.narration.split(/\s+/).filter(Boolean);
    const weights = words.map((w) => Math.max(2, w.length));
    const total = weights.reduce((a, b) => a + b, 0);
    let t = sc.start;
    words.forEach((w, i) => {
      const d = (weights[i] / total) * sc.duration;
      out.push({ word: w, start: t, end: t + d });
      t += d;
    });
  }
  return out;
}

/** words: [{word,start,end}] -> ASS file content. Groups up to `maxWords` words per line, highlights the spoken word. */
export function buildAss(words, { maxWords = 3, font = 'DejaVu Sans' } = {}) {
  const header = `[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920
WrapStyle: 0

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Cap,${font},92,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,7,3,5,60,60,0,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
`;
  const lines = [];
  for (let i = 0; i < words.length; i += maxWords) {
    const chunk = words.slice(i, i + maxWords);
    const chunkEnd = (words[i + maxWords]?.start ?? chunk[chunk.length - 1].end);
    chunk.forEach((w, j) => {
      const start = w.start;
      const end = j < chunk.length - 1 ? chunk[j + 1].start : chunkEnd;
      if (end <= start) return;
      const text = chunk
        .map((c, k) => {
          const t = esc(c.word.trim()).toUpperCase();
          return k === j ? `{\\c&H00FFFF&}${t}{\\c&HFFFFFF&}` : t; // spoken word in yellow
        })
        .join(' ');
      lines.push(`Dialogue: 0,${assTime(start)},${assTime(end)},Cap,,0,0,0,,${text}`);
    });
  }
  return header + lines.join('\n') + '\n';
}
