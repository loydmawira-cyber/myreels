// Art styles and niches offered in the app. Keep ids in sync with app/src/constants.ts
export const ART_STYLES = {
  cinematic: 'cinematic photorealistic still, dramatic lighting, shallow depth of field, film grain',
  anime: 'high quality anime illustration, vibrant colors, detailed background, studio quality',
  comic: 'bold comic book art, thick ink lines, halftone shading, dynamic composition',
  dark_fantasy: 'dark fantasy painting, moody atmosphere, muted palette, volumetric fog',
  watercolor: 'soft watercolor painting, paper texture, gentle pastel tones',
  pixel: 'detailed 16-bit pixel art, retro game aesthetic',
  claymation: '3D claymation style, handmade plasticine look, soft studio lighting',
  sketch: 'pencil sketch, cross-hatching, monochrome graphite on paper',
};

export const NICHES = {
  scary_stories: 'short original scary stories with a twist ending',
  history: 'surprising, little-known true facts from history',
  motivation: 'motivational stories and lessons from famous people',
  fun_facts: 'mind-blowing fun facts about science and the world',
  bible_stories: 'retellings of well-known Bible stories',
  true_crime: 'concise summaries of famous unsolved mysteries (no graphic detail)',
  philosophy: 'stoic and philosophical lessons applied to modern life',
  custom: null, // uses series.custom_prompt
};

export function styleFor(id) {
  return ART_STYLES[id] ?? ART_STYLES.cinematic;
}

export function nicheFor(series) {
  if (series.niche === 'custom' || !NICHES[series.niche]) return series.custom_prompt || series.niche;
  return NICHES[series.niche];
}
