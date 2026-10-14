package com.example

import androidx.compose.ui.graphics.Color

data class OptionItem(val id: String, val label: String)

object Constants {
    val NICHES = listOf(
        OptionItem("scary_stories", "Scary stories"),
        OptionItem("history", "History facts"),
        OptionItem("motivation", "Motivation"),
        OptionItem("fun_facts", "Fun facts"),
        OptionItem("bible_stories", "Bible stories"),
        OptionItem("true_crime", "Unsolved mysteries"),
        OptionItem("philosophy", "Stoic philosophy"),
        OptionItem("custom", "Custom topic")
    )

    val ART_STYLES = listOf(
        OptionItem("cinematic", "Cinematic"),
        OptionItem("anime", "Anime"),
        OptionItem("comic", "Comic book"),
        OptionItem("dark_fantasy", "Dark fantasy"),
        OptionItem("watercolor", "Watercolor"),
        OptionItem("pixel", "Pixel art"),
        OptionItem("claymation", "Claymation"),
        OptionItem("sketch", "Pencil sketch")
    )

    val VOICES = listOf(
        OptionItem("onyx", "Deep male"),
        OptionItem("echo", "Calm male"),
        OptionItem("nova", "Bright female"),
        OptionItem("shimmer", "Soft female"),
        OptionItem("fable", "Storyteller"),
        OptionItem("alloy", "Neutral")
    )

    val DURATIONS = listOf(15, 30, 45, 60, 90)

    val STATUS_LABELS = mapOf(
        "queued" to "Waiting in line",
        "scripting" to "Writing script",
        "imaging" to "Finding video clips",
        "voicing" to "Recording voiceover",
        "rendering" to "Editing video",
        "done" to "Ready",
        "failed" to "Failed (not counted)"
    )
}

object AppColors {
    val Bg = Color(0xFF0B0B10)
    val Card = Color(0xFF17171F)
    val Border = Color(0xFF2A2A36)
    val Text = Color(0xFFF2F2F7)
    val Muted = Color(0xFF9A9AAB)
    val Accent = Color(0xFF7C5CFF)
    val Danger = Color(0xFFFF5C7A)
}
