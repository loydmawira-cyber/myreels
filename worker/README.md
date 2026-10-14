# MyReels render worker (free stack)

Script: Gemini free tier · Clips: Pexels stock video · Voice: edge-tts · Edit: FFmpeg · Host: GitHub Actions

## One-time setup
1. Keys: Gemini (aistudio.google.com/apikey), Pexels (pexels.com/api).
2. GitHub repo -> Settings -> Secrets and variables -> Actions -> **Secrets**:
   `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` (Supabase -> Settings -> API; the secret/service key, NEVER in the app),
   `GEMINI_API_KEY`, `PEXELS_API_KEY`.
3. Optional **Variables**: `GEMINI_MODEL` (default `gemini-2.5-flash`), `RETENTION_DAYS` (delete videos older than N days; default off, useful for the 1 GB free storage).
4. Run `supabase/migrations/001_init.sql` (done) then `002_worker_trigger.sql` (edit OWNER/REPO + store token first).
5. Actions tab -> "Render worker" -> Run workflow to test.

## Swapping to paid later
All providers live in `src/providers.js`: `generateScript`, `fetchClip` (replace with Veo/Kling/Runway), `synthesize` (replace with OpenAI TTS / ElevenLabs).
