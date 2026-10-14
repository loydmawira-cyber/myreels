package com.example.data

import kotlinx.serialization.Serializable

@Serializable
data class Series(
    val id: String,
    val name: String,
    val niche: String,
    val custom_prompt: String? = null,
    val art_style: String,
    val voice: String,
    val language: String = "en",
    val target_seconds: Int,
    val music_url: String? = null,
    val created_at: String
)

@Serializable
data class ScriptContent(
    val title: String,
    val caption: String,
    val hashtags: List<String>
)

@Serializable
data class Video(
    val id: String,
    val series_id: String,
    val status: String,
    val progress: Int,
    val title: String? = null,
    val script: ScriptContent? = null,
    val video_path: String? = null,
    val thumbnail_path: String? = null,
    val error: String? = null,
    val created_at: String
)

@Serializable
data class Quota(
    val free_mode: Boolean,
    val credits: Int,
    val daily_limit: Int,
    val used_today: Int
)

@Serializable
data class AuthResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val user: UserInfo? = null
)

@Serializable
data class UserInfo(
    val id: String,
    val email: String? = null
)
