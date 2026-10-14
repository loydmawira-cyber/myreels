import { useCallback, useState } from 'react';
import { FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { useFocusEffect, useRouter } from 'expo-router';
import { supabase, Series } from '../src/supabase';
import { ART_STYLES, NICHES, colors } from '../src/constants';
import { Button, s } from '../src/ui';

export default function Home() {
  const router = useRouter();
  const [series, setSeries] = useState<Series[]>([]);
  const [quota, setQuota] = useState<{ free_mode: boolean; credits: number; daily_limit: number; used_today: number } | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    const [{ data: ser }, { data: q }] = await Promise.all([
      supabase.from('series').select('*').order('created_at', { ascending: false }),
      supabase.rpc('my_quota'),
    ]);
    setSeries(ser ?? []);
    setQuota(q ?? null);
    setLoading(false);
  }, []);
  useFocusEffect(useCallback(() => { load(); }, [load]));

  const label = (list: { id: string; label: string }[], id: string) => list.find((x) => x.id === id)?.label ?? id;

  return (
    <View style={s.screen}>
      <View style={[s.card, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}>
        <Text style={s.p}>{quota?.free_mode ? 'Free videos left today' : 'Credits left'}</Text>
        <Text style={{ color: colors.text, fontSize: 22, fontWeight: '800' }}>
          {!quota ? '–' : quota.free_mode ? Math.max(0, quota.daily_limit - quota.used_today) : quota.credits}
        </Text>
      </View>
      <FlatList
        data={series}
        keyExtractor={(x) => x.id}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={load} tintColor={colors.text} />}
        ListEmptyComponent={!loading ? <Text style={[s.p, { marginTop: 30, textAlign: 'center' }]}>No series yet. Create one to make your first video.</Text> : null}
        renderItem={({ item }) => (
          <Pressable style={s.card} onPress={() => router.push(`/series/${item.id}`)}>
            <Text style={{ color: colors.text, fontSize: 17, fontWeight: '700' }}>{item.name}</Text>
            <Text style={s.p}>
              {label(NICHES, item.niche)} · {label(ART_STYLES, item.art_style)} · {item.target_seconds}s
            </Text>
          </Pressable>
        )}
      />
      <Button title="+ New series" onPress={() => router.push('/new-series')} />
      <Button variant="ghost" title="Sign out" onPress={() => supabase.auth.signOut()} />
    </View>
  );
}
