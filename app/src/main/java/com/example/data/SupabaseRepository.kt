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

    private fun errorMessage(body: String, fallback: String): String = try {
        val o = Json.parseToJsonElement(body) as? JsonObject
        ((o?.get("message") ?: o?.get("msg") ?: o?.get("error_description")) as? JsonPrimitive)?.content ?: fallback
    } catch (e: Exception) { fallback }

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
        if (token.startsWith("guest_")) return null
        return try {
            val resp = client.post("$url/rest/v1/rpc/my_quota") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
            }
            if (resp.status.isSuccess()) resp.body<Quota>() else null
        } catch (e: Exception) {
            null
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
        if (token.startsWith("guest_")) {
            return Result.failure(Exception("Sign in with an account to create series and generate videos"))
        }
        return try {
            val userResp = client.get("$url/auth/v1/user") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (!userResp.status.isSuccess()) {
                return Result.failure(Exception("Session expired. Please sign in again."))
            }
            val userId = userResp.body<UserInfo>().id

            val resp = client.post("$url/rest/v1/series") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                header("Prefer", "return=representation")
                contentType(ContentType.Application.Json)
                setBody(
                    CreateSeriesRequest(
                        user_id = userId,
                        name = name,
                        niche = niche,
                        art_style = artStyle,
                        voice = voice,
                        target_seconds = targetSeconds,
                        custom_prompt = customPrompt?.ifBlank { null },
                        music_url = musicUrl?.ifBlank { null }
                    )
                )
            }
            if (resp.status.isSuccess()) {
                val created = resp.body<List<Series>>().first()
                saveLocalSeries(created)
                Result.success(created)
            } else {
                Result.failure(Exception(errorMessage(resp.bodyAsText(), "Could not create series")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage ?: "could not reach server"}"))
        }
    }

    suspend fun getVideos(seriesId: String): List<Video> {
        val token = accessToken ?: return emptyList()
        if (token.startsWith("guest_")) return emptyList()
        return try {
            val resp = client.get("$url/rest/v1/videos?series_id=eq.$seriesId&select=*&order=created_at.desc") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) resp.body<List<Video>>() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getVideo(id: String): Video? {
        val token = accessToken ?: return null
        if (token.startsWith("guest_")) return null
        return try {
            val resp = client.get("$url/rest/v1/videos?id=eq.$id&select=*") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) resp.body<List<Video>>().firstOrNull() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteVideo(videoId: String): Boolean {
        val token = accessToken ?: return false
        if (token.startsWith("guest_")) return false
        return try {
            client.delete("$url/rest/v1/videos?id=eq.$videoId") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    /** Queues a video. The render worker (worker/) picks it up; the UI polls for progress. */
    suspend fun requestVideo(seriesId: String): Result<String> {
        val token = accessToken ?: return Result.failure(Exception("Not signed in"))
        if (token.startsWith("guest_")) {
            return Result.failure(Exception("Sign in with an account to generate videos"))
        }
        return try {
            val resp = client.post("$url/rest/v1/rpc/request_video") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("p_series_id" to seriesId))
            }
            if (resp.status.isSuccess()) {
                Result.success(resp.bodyAsText().trim('"'))
            } else {
                Result.failure(Exception(errorMessage(resp.bodyAsText(), "Could not start video generation")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage ?: "could not reach server"}"))
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
