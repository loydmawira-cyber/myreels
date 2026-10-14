import { useEffect, useState } from 'react';
import { Alert, ScrollView, Text, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useVideoPlayer, VideoView } from 'expo-video';
import { File, Paths } from 'expo-file-system';
import * as MediaLibrary from 'expo-media-library';
import * as Sharing from 'expo-sharing';
import { supabase, signedUrl, Video } from '../../src/supabase';
import { colors } from '../../src/constants';
import { Button, s } from '../../src/ui';

export default function VideoScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [video, setVideo] = useState<Video | null>(null);
  const [url, setUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const player = useVideoPlayer(url, (p) => { p.loop = true; p.play(); });

  useEffect(() => {
    supabase.from('videos').select('*').eq('id', id).single().then(async ({ data }) => {
      setVideo(data);
      if (data?.video_path) setUrl(await signedUrl(data.video_path));
    });
  }, [id]);

  async function download(): Promise<string | null> {
    if (!url) return null;
    const dest = new File(Paths.cache, `${id}.mp4`);
    if (dest.exists) return dest.uri;
    const file = await File.downloadFileAsync(url, dest);
    return file.uri;
  }

  async function save() {
    setBusy(true);
    try {
      const perm = await MediaLibrary.requestPermissionsAsync();
      if (!perm.granted) throw new Error('Gallery permission denied');
      const uri = await download();
      if (uri) await MediaLibrary.saveToLibraryAsync(uri);
      Alert.alert('Saved', 'Video saved to your gallery.');
    } catch (e: any) {
      Alert.alert('Error', e.message);
    } finally {
      setBusy(false);
    }
  }

  async function share() {
    setBusy(true);
    try {
      const uri = await download();
      if (uri) await Sharing.shareAsync(uri, { mimeType: 'video/mp4' }); // opens TikTok / Instagram / YouTube share sheet
    } finally {
      setBusy(false);
    }
  }

  const caption = video?.script ? `${video.script.caption}\n\n${video.script.hashtags.map((h) => `#${h}`).join(' ')}` : '';

  return (
    <ScrollView style={s.screen} contentContainerStyle={{ paddingBottom: 60 }}>
      <View style={{ aspectRatio: 9 / 16, borderRadius: 16, overflow: 'hidden', backgroundColor: '#000' }}>
        {url && <VideoView player={player} style={{ flex: 1 }} nativeControls contentFit="contain" />}
      </View>
      <Text style={[s.h2, { marginTop: 16 }]}>{video?.title}</Text>
      {!!caption && (
        <View style={s.card}>
          <Text style={s.label}>Suggested caption</Text>
          <Text selectable style={{ color: colors.text }}>{caption}</Text>
        </View>
      )}
      <Button title="Share to TikTok / Instagram / YouTube" onPress={share} loading={busy} />
      <Button variant="ghost" title="Save to gallery" onPress={save} disabled={busy} />
    </ScrollView>
  );
}
