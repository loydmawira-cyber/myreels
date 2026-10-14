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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SupabaseRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("supabase_auth", Context.MODE_PRIVATE)
    
    private val url = BuildConfig.SUPABASE_URL.ifEmpty { "https://xyzplaceholder.supabase.co" }
    val anonKey = BuildConfig.SUPABASE_ANON_KEY.ifEmpty { "placeholder" }

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
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

            val bodyMap = mutableMapOf<String, Any?>(
                "user_id" to userId,
                "name" to name,
                "niche" to niche,
                "art_style" to artStyle,
                "voice" to voice,
                "target_seconds" to targetSeconds
            )
            if (!customPrompt.isNullOrBlank()) bodyMap["custom_prompt"] = customPrompt
            if (!musicUrl.isNullOrBlank()) bodyMap["music_url"] = musicUrl

            val resp = client.post("$url/rest/v1/series") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                header("Prefer", "return=representation")
                contentType(ContentType.Application.Json)
                setBody(bodyMap)
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
        return try {
            val resp = client.get("$url/rest/v1/videos?series_id=eq.$seriesId&select=*&order=created_at.desc") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                resp.body<List<Video>>()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getVideo(id: String): Video? {
        val token = accessToken ?: return null
        return try {
            val resp = client.get("$url/rest/v1/videos?id=eq.$id&select=*") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            if (resp.status.isSuccess()) {
                val list: List<Video> = resp.body()
                list.firstOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteVideo(videoId: String): Boolean {
        val token = accessToken ?: return false
        return try {
            val resp = client.delete("$url/rest/v1/videos?id=eq.$videoId") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
            }
            resp.status.isSuccess()
        } catch (e: Exception) {
            false
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
                Result.success(videoId)
            } else {
                Result.failure(Exception(resp.bodyAsText()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSignedUrl(path: String): String? {
        val token = accessToken ?: return null
        return try {
            val resp = client.post("$url/storage/v1/object/sign/videos/$path") {
                header("apikey", anonKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("expiresIn" to 3600))
            }
            if (resp.status.isSuccess()) {
                val json = Json.parseToJsonElement(resp.bodyAsText()) as? JsonObject
                val signedUrl = (json?.get("signedURL") as? JsonPrimitive)?.content
                if (signedUrl != null) {
                    if (signedUrl.startsWith("http")) signedUrl else "$url/storage/v1$signedUrl"
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
