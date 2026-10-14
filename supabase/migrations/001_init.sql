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

-- 5. App settings: one row you edit in Supabase -> Table Editor -> app_settings
--    free_mode = true  -> no payments/credits; each user gets daily_limit_per_user videos per day
--    free_mode = false -> credit system (for when payments are added)
--    invite_only = true -> only emails listed in allowed_emails can generate videos
--    global_daily_limit -> hard cap on videos per day across ALL users (protects your OpenAI bill)
create table if not exists public.app_settings (
  id int primary key default 1 check (id = 1),
  free_mode boolean not null default true,
  daily_limit_per_user int not null default 2,
  global_daily_limit int not null default 50,
  invite_only boolean not null default false,
  updated_at timestamptz not null default now()
);
insert into public.app_settings (id) values (1) on conflict (id) do nothing;
alter table public.app_settings enable row level security;
create policy "anyone signed in can read settings" on public.app_settings for select to authenticated using (true);

create table if not exists public.allowed_emails (
  email text primary key,
  note text,
  added_at timestamptz not null default now()
);
alter table public.allowed_emails enable row level security; -- no policies: only you (dashboard) can see/edit

alter table public.videos add column if not exists used_credit boolean not null default false;

-- 6. Request a video: enforces invite list, daily limits and credits atomically
create or replace function public.request_video(p_series_id uuid)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
  cfg app_settings;
  v_used_credit boolean := false;
  v_today_user int;
  v_today_all int;
begin
  select * into cfg from app_settings where id = 1;

  if not exists (select 1 from series where id = p_series_id and user_id = auth.uid()) then
    raise exception 'series not found';
  end if;

  if cfg.invite_only and not exists (
    select 1 from allowed_emails where lower(email) = lower(auth.jwt()->>'email')
  ) then
    raise exception 'The app is invite-only right now. Ask for an invite to start creating videos.';
  end if;

  select count(*) into v_today_all from videos
    where created_at >= date_trunc('day', now()) and status <> 'failed';
  if v_today_all >= cfg.global_daily_limit then
    raise exception 'We''ve hit today''s video limit. Please try again tomorrow.';
  end if;

  if cfg.free_mode then
    select count(*) into v_today_user from videos
      where user_id = auth.uid() and created_at >= date_trunc('day', now()) and status <> 'failed';
    if v_today_user >= cfg.daily_limit_per_user then
      raise exception 'You''ve used your % free videos for today. Come back tomorrow!', cfg.daily_limit_per_user;
    end if;
  else
    update profiles set credits = credits - 1 where id = auth.uid() and credits > 0;
    if not found then raise exception 'no credits left'; end if;
    v_used_credit := true;
  end if;

  insert into videos (user_id, series_id, used_credit)
    values (auth.uid(), p_series_id, v_used_credit) returning id into v_id;
  return v_id;
end $$;
grant execute on function public.request_video(uuid) to authenticated;

-- What the app shows on the home screen
create or replace function public.my_quota()
returns json language sql security definer set search_path = public stable as $$
  select json_build_object(
    'free_mode', s.free_mode,
    'credits', (select credits from profiles where id = auth.uid()),
    'daily_limit', s.daily_limit_per_user,
    'used_today', (select count(*) from videos where user_id = auth.uid()
                     and created_at >= date_trunc('day', now()) and status <> 'failed')
  ) from app_settings s where s.id = 1;
$$;
grant execute on function public.my_quota() to authenticated;

-- 7. Worker claim: atomically grab the oldest queued job (called with service role)
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

-- 8. Storage bucket for finished videos (private; app gets signed URLs)
insert into storage.buckets (id, name, public) values ('videos', 'videos', false)
  on conflict (id) do nothing;
create policy "read own video files" on storage.objects for select
  using (bucket_id = 'videos' and (storage.foldername(name))[1] = auth.uid()::text);

-- 9. Realtime so the app sees progress live
alter publication supabase_realtime add table public.videos;
