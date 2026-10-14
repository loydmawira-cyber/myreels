import { useState } from 'react';
import { Alert, ScrollView, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { supabase } from '../src/supabase';
import { ART_STYLES, DURATIONS, NICHES, VOICES } from '../src/constants';
import { Button, Chips, Field, s } from '../src/ui';

export default function NewSeries() {
  const router = useRouter();
  const [name, setName] = useState('');
  const [niche, setNiche] = useState('scary_stories');
  const [custom, setCustom] = useState('');
  const [style, setStyle] = useState('cinematic');
  const [voice, setVoice] = useState('onyx');
  const [seconds, setSeconds] = useState(45);
  const [music, setMusic] = useState('');
  const [busy, setBusy] = useState(false);

  async function create() {
    if (niche === 'custom' && !custom.trim()) return Alert.alert('Describe your topic');
    setBusy(true);
    const { data: user } = await supabase.auth.getUser();
    const { data, error } = await supabase
      .from('series')
      .insert({
        user_id: user.user!.id,
        name: name.trim() || NICHES.find((n) => n.id === niche)!.label,
        niche,
        custom_prompt: niche === 'custom' ? custom.trim() : null,
        art_style: style,
        voice,
        target_seconds: seconds,
        music_url: music.trim() || null,
      })
      .select()
      .single();
    setBusy(false);
    if (error) return Alert.alert('Error', error.message);
    router.replace(`/series/${data.id}`);
  }

  return (
    <ScrollView style={s.screen} contentContainerStyle={{ paddingBottom: 60 }}>
      <Text style={s.h2}>1. Niche</Text>
      <Chips options={NICHES} value={niche} onChange={setNiche} />
      {niche === 'custom' && (
        <Field label="Your topic" value={custom} onChangeText={setCustom} placeholder="e.g. weird animal facts for kids" style={{ marginTop: 12 }} />
      )}
      <Text style={s.h2}>2. Art style</Text>
      <Chips options={ART_STYLES} value={style} onChange={setStyle} />
      <Text style={s.h2}>3. Voice</Text>
      <Chips options={VOICES} value={voice} onChange={setVoice} />
      <Text style={s.h2}>4. Length</Text>
      <Chips options={DURATIONS.map((d) => ({ id: d, label: `${d} sec` }))} value={seconds} onChange={setSeconds} />
      <Text style={s.h2}>5. Details</Text>
      <Field label="Series name (optional)" value={name} onChangeText={setName} placeholder="My scary channel" />
      <Field label="Background music link (optional, .mp3)" value={music} onChangeText={setMusic} autoCapitalize="none" placeholder="https://..." />
      <Button title="Create series" onPress={create} loading={busy} />
    </ScrollView>
  );
}
