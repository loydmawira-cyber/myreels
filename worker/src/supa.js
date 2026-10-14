// Minimal Supabase REST client (no dependencies). Uses the SERVICE ROLE / secret key.
const BASE = (process.env.SUPABASE_URL || '').replace(/\/$/, '');
const KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || '';

function headers(extra = {}) {
  const h = { apikey: KEY, ...extra };
  // Legacy service_role keys are JWTs and go in Authorization too; new sb_secret_ keys go in apikey only.
  if (KEY.startsWith('eyJ')) h.Authorization = `Bearer ${KEY}`;
  return h;
}

async function call(url, opts) {
  const res = await fetch(url, opts);
  const text = await res.text();
  if (!res.ok) throw new Error(`${opts.method || 'GET'} ${url.replace(BASE, '')} -> ${res.status} ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : null;
}

export const rpc = (name, args = {}) =>
  call(`${BASE}/rest/v1/rpc/${name}`, {
    method: 'POST',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(args),
  });

export const select = (table, query) =>
  call(`${BASE}/rest/v1/${table}?${query}`, { headers: headers() });

export const patch = (table, query, body) =>
  call(`${BASE}/rest/v1/${table}?${query}`, {
    method: 'PATCH',
    headers: headers({ 'Content-Type': 'application/json', Prefer: 'return=minimal' }),
    body: JSON.stringify(body),
  });

export const del = (table, query) =>
  call(`${BASE}/rest/v1/${table}?${query}`, { method: 'DELETE', headers: headers() });

export async function upload(bucket, path, buffer, contentType) {
  await call(`${BASE}/storage/v1/object/${bucket}/${path}`, {
    method: 'POST',
    headers: headers({ 'Content-Type': contentType, 'x-upsert': 'true' }),
    body: buffer,
  });
}

export const removeObjects = (bucket, paths) =>
  call(`${BASE}/storage/v1/object/${bucket}`, {
    method: 'DELETE',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ prefixes: paths }),
  });
