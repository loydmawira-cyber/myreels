import { useEffect, useState } from 'react';
import { Stack, useRouter, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import type { Session } from '@supabase/supabase-js';
import { supabase } from '../src/supabase';
import { colors } from '../src/constants';

export default function RootLayout() {
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  const router = useRouter();
  const segments = useSegments();

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session));
    const { data: sub } = supabase.auth.onAuthStateChange((_e, s) => setSession(s));
    return () => sub.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (session === undefined) return;
    const onLogin = segments[0] === 'login';
    if (!session && !onLogin) router.replace('/login');
    if (session && onLogin) router.replace('/');
  }, [session, segments]);

  return (
    <>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.bg },
          headerTintColor: colors.text,
          contentStyle: { backgroundColor: colors.bg },
        }}
      >
        <Stack.Screen name="index" options={{ title: 'My videos' }} />
        <Stack.Screen name="login" options={{ headerShown: false }} />
        <Stack.Screen name="new-series" options={{ title: 'New series' }} />
        <Stack.Screen name="series/[id]" options={{ title: 'Series' }} />
        <Stack.Screen name="video/[id]" options={{ title: 'Video' }} />
      </Stack>
    </>
  );
}
