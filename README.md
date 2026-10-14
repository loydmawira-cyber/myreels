# MyReels (working name) – faceless video app

Android app + backend that turns a niche + art style into a ready-to-post 9:16 video:
**script → AI images → AI voiceover → word-by-word captions → Ken Burns edit + music → MP4**.

```
app/        Android app (Expo / React Native). Login, series wizard, generate, live progress, play, save, share
worker/     Video engine (Node + ffmpeg). Polls the job queue, calls OpenAI, renders, uploads
supabase/   Database schema: users, credits, series, videos, storage, security rules
```

## How it fits together
1. User signs up in the app (Supabase Auth). In free mode they get a few free videos per day (see below).
2. User creates a *series* (niche, art style, voice, length, optional music).
3. "Generate a video" calls `request_video()` → checks limits (or spends a credit), queues a job.
4. The worker claims the job, produces the video, uploads it to Supabase Storage.
5. The app shows live progress and then plays the video, with Save and Share buttons
   (Share opens TikTok / Instagram / YouTube on the phone; no platform approvals needed).

## Launch mode: no payments (default)
The app starts in **free mode**: no credits, no billing. Limits live in one row you edit in
Supabase → Table Editor → `app_settings`:

| Setting | Default | What it does |
|---|---|---|
| `free_mode` | `true` | `true` = free daily videos; `false` = credit system (when payments are added) |
| `daily_limit_per_user` | `2` | Free videos each user can make per day |
| `global_daily_limit` | `50` | Max videos per day across all users (caps your OpenAI bill) |
| `invite_only` | `false` | `true` = only emails in `allowed_emails` can generate videos |

Failed videos don't count toward limits. Also set a monthly budget limit in OpenAI as a final safety net.

## Setup (in order)

### 1. Supabase (free)
- Create a project at supabase.com.
- SQL Editor → paste `supabase/migrations/001_init.sql` → Run.
- Project Settings → API: copy the **URL**, **anon key** and **service_role key**.

### 2. OpenAI API (pay as you go, $5 minimum top-up)
- platform.openai.com → Billing → add $5 → API keys → create key.
- Set a monthly spend limit while testing.

### 3. Worker (Railway, ~$5/month; any Docker host works)
- Push this repo to GitHub, then on railway.com: New Project → Deploy from repo → root directory `worker`.
- Add the variables from `worker/.env.example`.
- Local test without any keys: `cd worker && npm install && npm run test:render`.

### 4. App (Expo, free)
```
cd app
cp .env.example .env            # fill in Supabase URL + anon key
npm install && npx expo install --fix
npx expo start                  # scan the QR with Expo Go on your phone
npm i -g eas-cli && eas login
npm run build:apk               # cloud-built APK you can install on any Android phone
```
Before building, change `name`, `slug`, `scheme` and `android.package` in `app/app.json` to your brand.

### 5. Google Play (later, $25 one-time)
`npm run build:play` produces the .aab to upload in Play Console.

## Cost per video (rough, check current OpenAI pricing)
Script ~$0.001 · 7–10 images (low quality) ~$0.03–0.08 · voice ~$0.01 · captions ~$0.005
≈ **$0.05–0.15 per 45-second video**. Set `CAPTIONS_MODE=estimate` to skip the caption call.

## Roadmap
- [x] Phase 1 MVP: accounts, series wizard, full generation pipeline, progress, play/save/share
- [ ] Phase 2: payments (Google Play Billing via RevenueCat), credit packs / subscriptions
- [ ] Phase 3: scheduled daily generation per series; push notification when ready
- [ ] Phase 4: auto-posting (YouTube Data API first, then TikTok Content Posting API and Instagram Graph API; each requires app review)
- [ ] Phase 5: more voices (ElevenLabs), AI video clips, templates, custom music upload, web dashboard
