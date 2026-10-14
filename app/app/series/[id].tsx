import { useCallback, useEffect, useState } from 'react';
import { Alert, FlatList, Image, Pressable, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { supabase, signedUrl, Series, Video } from '../../src/supabase';
import { STATUS_LABEL, colors } from '../../src/constants';
import { Button, s } from '../../src/ui';

export default function SeriesScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const [series, setSeries] = useState<Series | null>(null);
  const [videos, setVideos] = useState<Video[]>([]);
  const [thumbs, setThumbs] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const [{ data: ser }, { data: vids }] = await Promise.all([
      supabase.from('series').select('*').eq('id', id).single(),
      supabase.from('videos').select('*').eq('series_id', id).order('created_at', { ascending: false }),
    ]);
    setSeries(ser);
    setVideos(vids ?? []);
    for (const v of vids ?? []) {
      if (v.thumbnail_path && !thumbs[v.id]) {
        signedUrl(v.thumbnail_path).then((u) => setThumbs((t) => ({ ...t, [v.id]: u }))).catch(() => {});
      }
    }
  }, [id]);

  useEffect(() => {
    load();
    // live progress updates
    const ch = supabase
      .channel(`videos-${id}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'videos', filter: `series_id=eq.${id}` }, load)
      .subscribe();
    return () => { supabase.removeChannel(ch); };
  }, [id, load]);

  async function generate() {
    setBusy(true);
    const { error } = await supabase.rpc('request_video', { p_series_id: id });
    setBusy(false);
    if (error) return Alert.alert('Could not start', error.message);
    load();
  }

  return (
    <View style={s.screen}>
      <Text style={s.h1}>{series?.name ?? ''}</Text>
      <Button title="Generate a video" onPress={generate} loading={busy} />
      <Text style={s.h2}>Videos</Text>
      <FlatList
        data={videos}
        keyExtractor={(v) => v.id}
        ListEmptyComponent={<Text style={s.p}>Nothing yet. Tap “Generate a video”.</Text>}
        renderItem={({ item }) => (
          <Pressable
            style={[s.card, { flexDirection: 'row', gap: 12, alignItems: 'center' }]}
            disabled={item.status !== 'done'}
            onPress={() => router.push(`/video/${item.id}`)}
          >
            {thumbs[item.id] ? (
              <Image source={{ uri: thumbs[item.id] }} style={{ width: 54, height: 96, borderRadius: 8 }} />
            ) : (
              <View style={{ width: 54, height: 96, borderRadius: 8, backgroundColor: colors.border }} />
            )}
            <View style={{ flex: 1 }}>
              <Text style={{ color: colors.text, fontWeight: '700', fontSize: 16 }}>{item.title ?? 'New video'}</Text>
              <Text style={[s.p, item.status === 'failed' && { color: colors.danger }]}>
                {STATUS_LABEL[item.status]}{item.status !== 'done' && item.status !== 'failed' ? ` · ${item.progress}%` : ''}
              </Text>
              {item.status !== 'done' && item.status !== 'failed' && (
                <View style={{ height: 6, backgroundColor: colors.border, borderRadius: 3, marginTop: 8 }}>
                  <View style={{ height: 6, width: `${item.progress}%`, backgroundColor: colors.accent, borderRadius: 3 }} />
                </View>
              )}
            </View>
          </Pressable>
        )}
      />
    </View>
  );
}
