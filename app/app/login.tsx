import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, Text, View } from 'react-native';
import { supabase } from '../src/supabase';
import { Button, Field, s } from '../src/ui';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mode, setMode] = useState<'in' | 'up'>('in');
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    const { error } =
      mode === 'in'
        ? await supabase.auth.signInWithPassword({ email: email.trim(), password })
        : await supabase.auth.signUp({ email: email.trim(), password });
    setBusy(false);
    if (error) return Alert.alert('Error', error.message);
    if (mode === 'up') Alert.alert('Check your email', 'Confirm your address, then sign in.');
  }

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={[s.screen, { justifyContent: 'center' }]}>
      <Text style={s.h1}>Faceless videos on autopilot</Text>
      <Text style={[s.p, { marginBottom: 28 }]}>Pick a niche, pick a style, get a ready-to-post video in minutes.</Text>
      <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" placeholder="you@example.com" />
      <Field label="Password" value={password} onChangeText={setPassword} secureTextEntry placeholder="At least 6 characters" />
      <Button title={mode === 'in' ? 'Sign in' : 'Create account'} onPress={submit} loading={busy} />
      <View style={{ height: 8 }} />
      <Button variant="ghost" title={mode === 'in' ? 'New here? Create an account' : 'Have an account? Sign in'} onPress={() => setMode(mode === 'in' ? 'up' : 'in')} />
    </KeyboardAvoidingView>
  );
}
