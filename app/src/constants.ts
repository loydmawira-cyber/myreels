// Keep ids in sync with worker/src/styles.js
export const NICHES = [
  { id: 'scary_stories', label: 'Scary stories' },
  { id: 'history', label: 'History facts' },
  { id: 'motivation', label: 'Motivation' },
  { id: 'fun_facts', label: 'Fun facts' },
  { id: 'bible_stories', label: 'Bible stories' },
  { id: 'true_crime', label: 'Unsolved mysteries' },
  { id: 'philosophy', label: 'Stoic philosophy' },
  { id: 'custom', label: 'Custom topic' },
];

export const ART_STYLES = [
  { id: 'cinematic', label: 'Cinematic' },
  { id: 'anime', label: 'Anime' },
  { id: 'comic', label: 'Comic book' },
  { id: 'dark_fantasy', label: 'Dark fantasy' },
  { id: 'watercolor', label: 'Watercolor' },
  { id: 'pixel', label: 'Pixel art' },
  { id: 'claymation', label: 'Claymation' },
  { id: 'sketch', label: 'Pencil sketch' },
];

// OpenAI TTS voices
export const VOICES = [
  { id: 'onyx', label: 'Deep male' },
  { id: 'echo', label: 'Calm male' },
  { id: 'nova', label: 'Bright female' },
  { id: 'shimmer', label: 'Soft female' },
  { id: 'fable', label: 'Storyteller' },
  { id: 'alloy', label: 'Neutral' },
];

export const DURATIONS = [30, 45, 60];

export const STATUS_LABEL: Record<string, string> = {
  queued: 'Waiting in line',
  scripting: 'Writing script',
  imaging: 'Creating images',
  voicing: 'Recording voiceover',
  rendering: 'Editing video',
  done: 'Ready',
  failed: 'Failed (not counted)',
};

export const colors = {
  bg: '#0b0b10',
  card: '#17171f',
  border: '#2a2a36',
  text: '#f2f2f7',
  muted: '#9a9aab',
  accent: '#7c5cff',
  danger: '#ff5c7a',
};
