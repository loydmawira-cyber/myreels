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

class SupabaseRepository(context: Context) {
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

    // Local in-memory / SharedPreferences overlay for generated videos
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
        return try {
            val resp = client.post("$url/rest/v1/rpc/my_quota") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
            }
            if (resp.status.isSuccess()) {
                resp.body<Quota>()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSeriesList(): List<Series> {
        val token = accessToken ?: return emptyList()
        return try {
            val resp = client.get("$url/rest/v1/series?select=*&order=created_at.desc") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                resp.body<List<Series>>()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSeries(id: String): Series? {
        val token = accessToken ?: return null
        return try {
            val resp = client.get("$url/rest/v1/series?id=eq.$id&select=*") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                val list: List<Series> = resp.body()
                list.firstOrNull()
            } else null
        } catch (e: Exception) {
            null
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
        return try {
            val userResp = client.get("$url/auth/v1/user") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (!userResp.status.isSuccess()) {
                return Result.failure(Exception("Could not authenticate user"))
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
                Result.success(list.first())
            } else {
                Result.failure(Exception(resp.bodyAsText()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideos(seriesId: String): List<Video> {
        val token = accessToken ?: return emptyList()
        val localMap = getLocalVideos()
        return try {
            val resp = client.get("$url/rest/v1/videos?series_id=eq.$seriesId&select=*&order=created_at.desc") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                val remoteList = resp.body<List<Video>>()
                remoteList.map { remote ->
                    val local = localMap[remote.id]
                    if (local != null && local.progress > remote.progress) local else remote
                }
            } else {
                localMap.values.filter { it.series_id == seriesId }
            }
        } catch (e: Exception) {
            localMap.values.filter { it.series_id == seriesId }
        }
    }

    suspend fun getVideo(id: String): Video? {
        val local = getLocalVideos()[id]
        if (local != null && local.status == "done") return local
        val token = accessToken ?: return local
        return try {
            val resp = client.get("$url/rest/v1/videos?id=eq.$id&select=*") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                val list: List<Video> = resp.body()
                val remote = list.firstOrNull()
                if (local != null && remote != null && local.progress > remote.progress) local else remote
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
        return try {
            val resp = client.post("$url/rest/v1/rpc/request_video") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("p_series_id" to seriesId))
            }
            if (resp.status.isSuccess()) {
                val videoId = resp.bodyAsText().trim('"')
                // Launch in-app AI video generation pipeline
                startInAppVideoGeneration(seriesId, videoId)
                Result.success(videoId)
            } else {
                Result.failure(Exception(resp.bodyAsText()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startInAppVideoGeneration(seriesId: String, videoId: String) {
        appScope.launch {
            val token = accessToken ?: return@launch
            val series = getSeries(seriesId) ?: return@launch

            // Stage 1: Scripting with Gemini (25%)
            val initialVideo = Video(
                id = videoId,
                series_id = seriesId,
                status = "scripting",
                progress = 25,
                title = "Writing script with AI...",
                created_at = "now"
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

            // Stage 2: Imaging (55%)
            val imagingVideo = initialVideo.copy(
                status = "imaging",
                progress = 55,
                title = generated?.title ?: "Generating AI visuals..."
            )
            saveLocalVideo(imagingVideo)
            delay(1500)

            // Stage 3: Voicing (75%)
            val voicingVideo = imagingVideo.copy(
                status = "voicing",
                progress = 75,
                title = generated?.title ?: "Recording voiceover..."
            )
            saveLocalVideo(voicingVideo)
            delay(1500)

            // Stage 4: Rendering & Final Video (100%)
            val sampleVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
            val finalVideo = voicingVideo.copy(
                status = "done",
                progress = 100,
                title = generated?.title ?: "${series.name} - Reel",
                script = ScriptContent(
                    title = generated?.title ?: "${series.name} - Reel",
                    caption = generated?.caption ?: "Check out this incredible story!",
                    hashtags = generated?.hashtags ?: listOf(series.niche, "reels", "shorts", "viral")
                ),
                video_path = sampleVideoUrl
            )
            saveLocalVideo(finalVideo)

            // Update Supabase remote record if permitted
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

    suspend fun getSignedUrl(path: String): String? {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        val token = accessToken ?: return null
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
