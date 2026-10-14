import 'react-native-url-polyfill/auto';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createClient } from '@supabase/supabase-js';

const url = process.env.EXPO_PUBLIC_SUPABASE_URL;
const anon = process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY;
if (!url || !anon) throw new Error('Set EXPO_PUBLIC_SUPABASE_URL and EXPO_PUBLIC_SUPABASE_ANON_KEY in app/.env');

export const supabase = createClient(url, anon, {
  auth: { storage: AsyncStorage, autoRefreshToken: true, persistSession: true, detectSessionInUrl: false },
});

export type Series = {
  id: string;
  name: string;
  niche: string;
  custom_prompt: string | null;
  art_style: string;
  voice: string;
  language: string;
  target_seconds: number;
  music_url: string | null;
  created_at: string;
};

export type Video = {
  id: string;
  series_id: string;
  status: 'queued' | 'scripting' | 'imaging' | 'voicing' | 'rendering' | 'done' | 'failed';
  progress: number;
  title: string | null;
  script: { title: string; caption: string; hashtags: string[] } | null;
  video_path: string | null;
  thumbnail_path: string | null;
  error: string | null;
  created_at: string;
};

export async function signedUrl(path: string) {
  const { data, error } = await supabase.storage.from('videos').createSignedUrl(path, 60 * 60);
  if (error) throw error;
  return data.signedUrl;
}
