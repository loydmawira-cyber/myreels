import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, TextInputProps, View } from 'react-native';
import { colors } from './constants';

export function Button({ title, onPress, loading, variant = 'primary', disabled }: {
  title: string; onPress: () => void; loading?: boolean; variant?: 'primary' | 'ghost'; disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={loading || disabled}
      style={[s.btn, variant === 'ghost' && s.ghost, (loading || disabled) && { opacity: 0.5 }]}
    >
      {loading ? <ActivityIndicator color="#fff" /> : <Text style={s.btnText}>{title}</Text>}
    </Pressable>
  );
}

export function Field(props: TextInputProps & { label: string }) {
  return (
    <View style={{ marginBottom: 14 }}>
      <Text style={s.label}>{props.label}</Text>
      <TextInput placeholderTextColor={colors.muted} {...props} style={[s.input, props.style]} />
    </View>
  );
}

export function Chips<T extends string | number>({ options, value, onChange }: {
  options: { id: T; label: string }[]; value: T; onChange: (v: T) => void;
}) {
  return (
    <View style={s.chips}>
      {options.map((o) => (
        <Pressable key={String(o.id)} onPress={() => onChange(o.id)} style={[s.chip, value === o.id && s.chipOn]}>
          <Text style={[s.chipText, value === o.id && { color: '#fff' }]}>{o.label}</Text>
        </Pressable>
      ))}
    </View>
  );
}

export const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, padding: 20 },
  h1: { color: colors.text, fontSize: 28, fontWeight: '800', marginBottom: 6 },
  h2: { color: colors.text, fontSize: 18, fontWeight: '700', marginTop: 18, marginBottom: 10 },
  p: { color: colors.muted, fontSize: 15, lineHeight: 21 },
  label: { color: colors.muted, fontSize: 13, marginBottom: 6, fontWeight: '600' },
  input: { backgroundColor: colors.card, borderColor: colors.border, borderWidth: 1, borderRadius: 12, padding: 14, color: colors.text, fontSize: 16 },
  btn: { backgroundColor: colors.accent, borderRadius: 14, paddingVertical: 15, alignItems: 'center', marginTop: 8 },
  ghost: { backgroundColor: 'transparent', borderWidth: 1, borderColor: colors.border },
  btnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  card: { backgroundColor: colors.card, borderRadius: 16, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: colors.border },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: 20, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border },
  chipOn: { backgroundColor: colors.accent, borderColor: colors.accent },
  chipText: { color: colors.muted, fontWeight: '600' },
});
