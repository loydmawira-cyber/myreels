-- OPTIONAL but recommended: start the GitHub Actions render worker the moment a video is requested.
-- Without this the worker only runs hourly (cron) or when you press "Run workflow".
--
-- 1. Replace OWNER/REPO below with your GitHub repo.
-- 2. Create a fine-grained GitHub token for that repo with "Contents: Read and write"
--    (needed for repository_dispatch), then store it ONCE in the Supabase SQL editor:
--       select vault.create_secret('github_pat_xxx', 'github_dispatch_token');
--    Never put the token in the app or in git.

create extension if not exists pg_net with schema extensions;

create or replace function public.notify_render_worker()
returns trigger language plpgsql security definer
set search_path = public, extensions, vault as $$
declare tok text;
begin
  select decrypted_secret into tok from vault.decrypted_secrets
    where name = 'github_dispatch_token' limit 1;
  if tok is null then return new; end if;   -- not configured: hourly cron still picks the job up
  perform net.http_post(
    url := 'https://api.github.com/repos/OWNER/REPO/dispatches',
    headers := jsonb_build_object(
      'Authorization', 'Bearer ' || tok,
      'Accept', 'application/vnd.github+json',
      'User-Agent', 'myreels-supabase'),
    body := jsonb_build_object('event_type', 'render-video'));
  return new;
end $$;

drop trigger if exists videos_notify_worker on public.videos;
create trigger videos_notify_worker after insert on public.videos
  for each row execute function public.notify_render_worker();
