-- Core schema for the faceless video app (Supabase / Postgres)
-- Run in Supabase SQL editor or via `supabase db push`.

-- 1. Profiles (one row per auth user)
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  plan text not null default 'free',          -- free | pro (set by billing webhook later)
  credits int not null default 3,             -- free users get 3 videos
  created_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1)));
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- 2. Series = a niche + style the user wants recurring videos for
create table if not exists public.series (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  niche text not null,                         -- e.g. "scary stories", "history facts", or custom
  custom_prompt text,                          -- free text when niche = custom
  art_style text not null default 'cinematic', -- see worker/src/styles.js
  voice text not null default 'onyx',          -- TTS voice id
  language text not null default 'en',
  target_seconds int not null default 45 check (target_seconds between 15 and 90),
  music_url text,                              -- optional background track
  created_at timestamptz not null default now()
);

-- 3. Videos = one render job
create type public.video_status as enum ('queued','scripting','imaging','voicing','rendering','done','failed');

create table if not exists public.videos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  series_id uuid not null references public.series(id) on delete cascade,
  status public.video_status not null default 'queued',
  progress int not null default 0,
  title text,
  script jsonb,                                -- { title, caption, hashtags, scenes:[{narration, image_prompt}] }
  video_path text,                             -- path in storage bucket "videos"
  thumbnail_path text,
  error text,
  locked_at timestamptz,                       -- worker claim
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists videos_queue_idx on public.videos (status, created_at) where status = 'queued';

-- 4. Row level security: users only see their own data
alter table public.profiles enable row level security;
alter table public.series   enable row level security;
alter table public.videos   enable row level security;

create policy "own profile" on public.profiles for select using (auth.uid() = id);
create policy "own series"  on public.series for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "read own videos" on public.videos for select using (auth.uid() = user_id);
create policy "delete own videos" on public.videos for delete using (auth.uid() = user_id);
-- Inserts go through request_video() so credits are enforced server-side.

-- 5. Request a video: checks + spends a credit atomically
create or replace function public.request_video(p_series_id uuid)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
  if not exists (select 1 from series where id = p_series_id and user_id = auth.uid()) then
    raise exception 'series not found';
  end if;
  update profiles set credits = credits - 1 where id = auth.uid() and credits > 0;
  if not found then raise exception 'no credits left'; end if;
  insert into videos (user_id, series_id) values (auth.uid(), p_series_id) returning id into v_id;
  return v_id;
end $$;
grant execute on function public.request_video(uuid) to authenticated;

-- 6. Worker claim: atomically grab the oldest queued job (called with service role)
create or replace function public.claim_next_video()
returns setof public.videos language sql security definer set search_path = public as $$
  update videos set status = 'scripting', locked_at = now(), updated_at = now()
  where id = (
    select id from videos where status = 'queued'
    order by created_at limit 1 for update skip locked
  )
  returning *;
$$;
revoke execute on function public.claim_next_video() from public, anon, authenticated;

-- 7. Storage bucket for finished videos (private; app gets signed URLs)
insert into storage.buckets (id, name, public) values ('videos', 'videos', false)
  on conflict (id) do nothing;
create policy "read own video files" on storage.objects for select
  using (bucket_id = 'videos' and (storage.foldername(name))[1] = auth.uid()::text);

-- 8. Realtime so the app sees progress live
alter publication supabase_realtime add table public.videos;
