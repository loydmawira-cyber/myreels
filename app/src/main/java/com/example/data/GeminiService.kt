package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
data class GeneratedReelScript(
    val title: String,
    val narration: String,
    val caption: String,
    val hashtags: List<String>,
    val scenes: List<String>
)

class GeminiService(context: Context, private val client: HttpClient) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    var customApiKey: String?
        get() = prefs.getString("gemini_api_key", null)
        set(value) = prefs.edit().putString("gemini_api_key", value).apply()

    private val effectiveApiKey: String
        get() = customApiKey?.trim()?.ifBlank { null }
            ?: BuildConfig.GEMINI_API_KEY.ifEmpty { "" }

    suspend fun generateReelScript(
        niche: String,
        artStyle: String,
        voice: String,
        durationSeconds: Int,
        customPrompt: String?
    ): Result<GeneratedReelScript> = withContext(Dispatchers.IO) {
        val apiKey = effectiveApiKey
        val promptText = buildString {
            append("You are an expert viral short-form video creator for TikTok, YouTube Shorts, and Instagram Reels.\n")
            append("Create an engaging, fast-paced faceless video script.\n")
            append("Niche: $niche\n")
            append("Art Style: $artStyle\n")
            append("Target Duration: $durationSeconds seconds\n")
            if (!customPrompt.isNullOrBlank()) {
                append("Topic Details: $customPrompt\n")
            }
            append("\nRespond strictly in valid JSON format with this exact structure:\n")
            append("{\n")
            append("  \"title\": \"Catchy title hook under 8 words\",\n")
            append("  \"narration\": \"Engaging narration text spoken clearly without stage directions\",\n")
            append("  \"caption\": \"High engagement social media caption with a question\",\n")
            append("  \"hashtags\": [\"tag1\", \"tag2\", \"tag3\", \"tag4\"],\n")
            append("  \"scenes\": [\"Visual description for scene 1\", \"Visual description for scene 2\", \"Visual description for scene 3\"]\n")
            append("}\n")
        }

        // If no API key is provided, return an intelligent scripted fallback for the niche
        if (apiKey.isBlank()) {
            return@withContext Result.success(getFallbackScript(niche, customPrompt, durationSeconds))
        }

        try {
            val requestJson = buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject {
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", promptText)
                            })
                        }
                    })
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.7)
                    put("responseMimeType", "application/json")
                }
            }

            val response = client.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(requestJson)
            }

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val rootJson = json.parseToJsonElement(responseText).jsonObject
                val candidate = rootJson["candidates"]?.jsonArray?.getOrNull(0)?.jsonObject
                val partText = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                if (partText != null) {
                    val parsed = json.decodeFromString<GeneratedReelScript>(partText)
                    Result.success(parsed)
                } else {
                    Result.success(getFallbackScript(niche, customPrompt, durationSeconds))
                }
            } else {
                // If API call fails (e.g. invalid key), use the fallback
                Result.success(getFallbackScript(niche, customPrompt, durationSeconds))
            }
        } catch (e: Exception) {
            Result.success(getFallbackScript(niche, customPrompt, durationSeconds))
        }
    }

    private fun getFallbackScript(niche: String, prompt: String?, duration: Int): GeneratedReelScript {
        return when (niche) {
            "scary_stories" -> GeneratedReelScript(
                title = "The Midnight Caller",
                narration = "Every night at exactly 3:17 AM, the vintage payphone outside my apartment rings. Last night, I finally picked it up... and heard my own voice whispering from the other end.",
                caption = "Would you answer the phone at 3 AM? Comment below! 😱",
                hashtags = listOf("scarystories", "horror", "creepypasta", "urbanlegend", "shorts"),
                scenes = listOf("Abandoned street with fog", "Vintage telephone booth illuminated in eerie blue", "Silhouette looking into the distance")
            )
            "history" -> GeneratedReelScript(
                title = "The Roman Secret Weapon",
                narration = "In 672 AD, the Byzantine Empire unleashed a weapon so terrifying it burned directly on water: Greek Fire. Its recipe was so fiercely guarded that to this day, nobody knows its exact chemical formula.",
                caption = "Ancient technology that vanished from history! Which ancient secret shocks you the most? 🏛️",
                hashtags = listOf("historyfacts", "romanempire", "ancientsecrets", "historical", "reels"),
                scenes = listOf("Ancient war fleet on stormy seas", "Liquid fire erupting across naval galleys", "Secret imperial scroll sealed in gold")
            )
            "motivation" -> GeneratedReelScript(
                title = "Don't Waste Today",
                narration = "You have two choices every single morning: sleep with your dreams, or wake up and chase them. Your future self is watching you right now through memories. Make them proud.",
                caption = "Stop waiting for the 'right time'. The time is now. Tag someone who needs to hear this! 🔥",
                hashtags = listOf("motivation", "discipline", "mindset", "success", "grind"),
                scenes = listOf("Sunrise over a misty mountain peak", "Athlete tying their shoes in the dark", "Looking out over a vibrant skyline")
            )
            "bible_stories" -> GeneratedReelScript(
                title = "David vs Goliath",
                narration = "A giant nine feet tall stood between an army and victory. While warriors trembled, a young shepherd boy stepped forward with just five smooth stones and unwavering faith.",
                caption = "No giant is too big when faith leads the way. Amen! 🙏",
                hashtags = listOf("biblestories", "faith", "inspiration", "davidandgoliath", "christian"),
                scenes = listOf("Valley of Elah beneath stormy clouds", "Massive armor and spear of the giant", "Shepherd boy holding a sling with quiet confidence")
            )
            else -> GeneratedReelScript(
                title = prompt?.take(30) ?: "Unbelievable Facts Revealed",
                narration = "Here is something you probably didn't know: deep beneath the Antarctic ice lies an ancient river network, preserved in pitch darkness for over thirty million years.",
                caption = "Did you know this? Share with a curious friend! 🌍✨",
                hashtags = listOf("funfacts", "didyouknow", "curiosity", "mindblown", "viral"),
                scenes = listOf("Glacier breaking into cold water", "Glowing subterranean river cavern", "Satellite view of the icy continent")
            )
        }
    }
}
