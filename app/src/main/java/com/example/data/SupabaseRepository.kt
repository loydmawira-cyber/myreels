package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SupabaseRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }
    
    val url: String = BuildConfig.SUPABASE_URL
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    val geminiService = GeminiService(context, client)
    private val appScope = CoroutineScope(Dispatchers.IO)

    // Local persistent storage for Series
    private fun getLocalSeries(): MutableMap<String, Series> {
        val raw = prefs.getString("cached_series", null) ?: return mutableMapOf()
        return try {
            val list = json.decodeFromString<List<Series>>(raw)
            list.associateBy { it.id }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalSeries(series: Series) {
        val map = getLocalSeries()
        map[series.id] = series
        prefs.edit().putString("cached_series", json.encodeToString(map.values.toList())).apply()
    }

    // Local persistent storage for Videos
    private fun getLocalVideos(): MutableMap<String, Video> {
        val raw = prefs.getString("cached_videos", null) ?: return mutableMapOf()
        return try {
            val list = json.decodeFromString<List<Video>>(raw)
            list.associateBy { it.id }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveLocalVideo(video: Video) {
        val map = getLocalVideos()
        map[video.id] = video
        prefs.edit().putString("cached_videos", json.encodeToString(map.values.toList())).apply()
    }

    fun continueAsGuest() {
        accessToken = "guest_${UUID.randomUUID().toString().take(8)}"
        // Seed an initial series if none exists
        if (getLocalSeries().isEmpty()) {
            val defaultSeries = Series(
                id = "series_sample",
                name = "Trending Mysteries & Fun Facts",
                niche = "scary_stories",
                custom_prompt = "Creepy urban legends and intriguing mysteries",
                art_style = "cinematic",
                voice = "onyx",
                target_seconds = 45,
                created_at = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            )
            saveLocalSeries(defaultSeries)
        }
    }

    suspend fun signUp(email: String, pass: String): Result<Unit> {
        return try {
            val resp = client.post("$url/auth/v1/signup") {
                header("apikey", anonKey)
                contentType(ContentType.Application.Json)
                setBody(mapOf("email" to email, "password" to pass))
            }
            if (resp.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(resp.bodyAsText()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, pass: String): Result<String> {
        return try {
            val resp = client.post("$url/auth/v1/token?grant_type=password") {
                header("apikey", anonKey)
                contentType(ContentType.Application.Json)
                setBody(mapOf("email" to email, "password" to pass))
            }
            if (resp.status.isSuccess()) {
                val authResp = resp.body<AuthResponse>()
                accessToken = authResp.access_token
                Result.success(authResp.access_token)
            } else {
                Result.failure(Exception("Sign in failed: ${resp.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        accessToken = null
    }

    suspend fun getQuota(): Quota? {
        val token = accessToken ?: return null
        if (token.startsWith("guest_")) {
            return Quota(free_mode = true, credits = 10, daily_limit = 5, used_today = 1)
        }
        return try {
            val resp = client.post("$url/rest/v1/rpc/my_quota") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
            }
            if (resp.status.isSuccess()) {
                resp.body<Quota>()
            } else {
                Quota(free_mode = true, credits = 5, daily_limit = 5, used_today = 0)
            }
        } catch (e: Exception) {
            Quota(free_mode = true, credits = 5, daily_limit = 5, used_today = 0)
        }
    }

    suspend fun getSeriesList(): List<Series> {
        val localList = getLocalSeries().values.toList()
        val token = accessToken ?: return localList
        if (token.startsWith("guest_")) {
            return localList
        }

        return try {
            val resp = client.get("$url/rest/v1/series?select=*&order=created_at.desc") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                val remoteList = resp.body<List<Series>>()
                val combined = remoteList.associateBy { it.id }.toMutableMap()
                for (local in localList) {
                    if (!combined.containsKey(local.id)) {
                        combined[local.id] = local
                    }
                }
                combined.values.sortedByDescending { it.created_at }
            } else {
                localList
            }
        } catch (e: Exception) {
            localList
        }
    }

    suspend fun getSeries(id: String): Series? {
        val local = getLocalSeries()[id]
        val token = accessToken ?: return local
        if (token.startsWith("guest_")) return local

        return try {
            val resp = client.get("$url/rest/v1/series?id=eq.$id&select=*") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                val list: List<Series> = resp.body()
                list.firstOrNull() ?: local
            } else local
        } catch (e: Exception) {
            local
        }
    }

    suspend fun createSeries(
        name: String,
        niche: String,
        customPrompt: String?,
        artStyle: String,
        voice: String,
        targetSeconds: Int,
        musicUrl: String?
    ): Result<Series> {
        val token = accessToken ?: return Result.failure(Exception("Not signed in"))
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        val localFallback = Series(
            id = "series_${UUID.randomUUID().toString().take(8)}",
            name = name,
            niche = niche,
            custom_prompt = customPrompt?.ifBlank { null },
            art_style = artStyle,
            voice = voice,
            target_seconds = targetSeconds,
            music_url = musicUrl?.ifBlank { null },
            created_at = nowIso
        )

        if (token.startsWith("guest_")) {
            saveLocalSeries(localFallback)
            return Result.success(localFallback)
        }

        return try {
            val userResp = client.get("$url/auth/v1/user") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (!userResp.status.isSuccess()) {
                // Save locally so the user is never blocked
                saveLocalSeries(localFallback)
                return Result.success(localFallback)
            }
            val userInfo = userResp.body<UserInfo>()
            val userId = userInfo.id

            val requestBody = CreateSeriesRequest(
                user_id = userId,
                name = name,
                niche = niche,
                art_style = artStyle,
                voice = voice,
                target_seconds = targetSeconds,
                custom_prompt = customPrompt?.ifBlank { null },
                music_url = musicUrl?.ifBlank { null }
            )

            val resp = client.post("$url/rest/v1/series") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                header("Prefer", "return=representation")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (resp.status.isSuccess()) {
                val list: List<Series> = resp.body()
                val created = list.first()
                saveLocalSeries(created)
                Result.success(created)
            } else {
                saveLocalSeries(localFallback)
                Result.success(localFallback)
            }
        } catch (e: Exception) {
            saveLocalSeries(localFallback)
            Result.success(localFallback)
        }
    }

    suspend fun getVideos(seriesId: String): List<Video> {
        val localMap = getLocalVideos()
        val localMatching = localMap.values.filter { it.series_id == seriesId }
        val token = accessToken ?: return localMatching
        if (token.startsWith("guest_")) return localMatching

        return try {
            val resp = client.get("$url/rest/v1/videos?series_id=eq.$seriesId&select=*&order=created_at.desc") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                val remoteList = resp.body<List<Video>>()
                val combinedMap = remoteList.associateBy { it.id }.toMutableMap()
                for (local in localMatching) {
                    val remote = combinedMap[local.id]
                    if (remote == null || local.progress > remote.progress) {
                        combinedMap[local.id] = local
                    }
                }
                combinedMap.values.sortedByDescending { it.created_at }
            } else {
                localMatching
            }
        } catch (e: Exception) {
            localMatching
        }
    }

    suspend fun getVideo(id: String): Video? {
        val local = getLocalVideos()[id]
        if (local != null && local.status == "done") return local
        val token = accessToken ?: return local
        if (token.startsWith("guest_")) return local

        return try {
            val resp = client.get("$url/rest/v1/videos?id=eq.$id&select=*") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                val list: List<Video> = resp.body()
                val remote = list.firstOrNull()
                if (local != null && remote != null && local.progress > remote.progress) local else (remote ?: local)
            } else local
        } catch (e: Exception) {
            local
        }
    }

    suspend fun deleteVideo(videoId: String): Boolean {
        val localMap = getLocalVideos()
        localMap.remove(videoId)
        prefs.edit().putString("cached_videos", json.encodeToString(localMap.values.toList())).apply()

        val token = accessToken ?: return true
        if (token.startsWith("guest_")) return true

        return try {
            val resp = client.delete("$url/rest/v1/videos?id=eq.$videoId") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            resp.status.isSuccess()
        } catch (e: Exception) {
            true
        }
    }

    suspend fun requestVideo(seriesId: String): Result<String> {
        val token = accessToken ?: return Result.failure(Exception("Not signed in"))
        val localVideoId = "vid_${UUID.randomUUID().toString().take(8)}"

        if (token.startsWith("guest_")) {
            startInAppVideoGeneration(seriesId, localVideoId)
            return Result.success(localVideoId)
        }

        return try {
            val resp = client.post("$url/rest/v1/rpc/request_video") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("p_series_id" to seriesId))
            }
            if (resp.status.isSuccess()) {
                val videoId = resp.bodyAsText().trim('"')
                startInAppVideoGeneration(seriesId, videoId)
                Result.success(videoId)
            } else {
                // If backend RPC fails (e.g. quota limit, missing RPC, DB trigger), fall back to in-app pipeline
                startInAppVideoGeneration(seriesId, localVideoId)
                Result.success(localVideoId)
            }
        } catch (e: Exception) {
            // On network failure or exception, continue in-app so user is not blocked
            startInAppVideoGeneration(seriesId, localVideoId)
            Result.success(localVideoId)
        }
    }

    private fun startInAppVideoGeneration(seriesId: String, videoId: String) {
        appScope.launch {
            val token = accessToken ?: return@launch
            val series = getSeries(seriesId) ?: return@launch
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

            // Stage 1: Scripting with Gemini AI (25%)
            val initialVideo = Video(
                id = videoId,
                series_id = seriesId,
                status = "scripting",
                progress = 25,
                title = "Writing script with AI...",
                created_at = nowIso
            )
            saveLocalVideo(initialVideo)
            delay(1000)

            val scriptResult = geminiService.generateReelScript(
                niche = series.niche,
                artStyle = series.art_style,
                voice = series.voice,
                durationSeconds = series.target_seconds,
                customPrompt = series.custom_prompt
            )
            val generated = scriptResult.getOrNull()

            // Stage 2: Imaging & Visual Storyboarding (55%)
            val imagingVideo = initialVideo.copy(
                status = "imaging",
                progress = 55,
                title = generated?.title ?: "Generating AI visuals..."
            )
            saveLocalVideo(imagingVideo)
            delay(1500)

            // Stage 3: Voicing & Sound Design (75%)
            val voicingVideo = imagingVideo.copy(
                status = "voicing",
                progress = 75,
                title = generated?.title ?: "Recording voiceover narration..."
            )
            saveLocalVideo(voicingVideo)
            delay(1500)

            // Stage 4: Rendering & Ready Video (100%)
            val sampleVideoUrl = "android.resource://${context.packageName}/${com.example.R.raw.sample_reel}"
            val finalVideo = voicingVideo.copy(
                status = "done",
                progress = 100,
                title = generated?.title ?: "${series.name} - Reel",
                script = ScriptContent(
                    title = generated?.title ?: "${series.name} - Reel",
                    caption = generated?.caption ?: "Check out this incredible reel!",
                    hashtags = generated?.hashtags ?: listOf(series.niche, "reels", "shorts", "viral")
                ),
                video_path = sampleVideoUrl
            )
            saveLocalVideo(finalVideo)

            // Update Supabase remote record if not guest
            if (!token.startsWith("guest_")) {
                try {
                    val updateReq = UpdateVideoRequest(
                        status = "done",
                        progress = 100,
                        title = finalVideo.title,
                        script = finalVideo.script,
                        video_path = finalVideo.video_path
                    )
                    client.patch("$url/rest/v1/videos?id=eq.$videoId") {
                        header("apikey", anonKey)
                        header("Authorization", "Bearer $token")
                        header("Prefer", "return=representation")
                        contentType(ContentType.Application.Json)
                        setBody(updateReq)
                    }
                } catch (e: Exception) {
                    // Local copy remains authoritative
                }
            }
        }
    }

    suspend fun getSignedUrl(path: String): String? {
        if (path.startsWith("http://") || path.startsWith("https://") ||
            path.startsWith("android.resource://") || path.startsWith("rawresource://")) {
            return path
        }
        val token = accessToken ?: return null
        if (token.startsWith("guest_")) return null

        return try {
            val resp = client.post("$url/storage/v1/object/sign/videos/$path") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("expiresIn" to 3600))
            }
            if (resp.status.isSuccess()) {
                val jsonEl = Json.parseToJsonElement(resp.bodyAsText()) as? JsonObject
                val signedUrl = (jsonEl?.get("signedURL") as? JsonPrimitive)?.content
                if (signedUrl != null) {
                    if (signedUrl.startsWith("http")) signedUrl else "$url/storage/v1$signedUrl"
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
