# MyReels (Android)

MyReels is a native Android application built with Kotlin and Jetpack Compose that automates faceless video generation (Scary stories, History facts, Motivation, Fun facts, Bible stories, Unsolved mysteries, Stoic philosophy, or Custom topics).

## Features

- **Authentication**: Secure Supabase email/password sign-in and account creation.
- **Quota & Credits**: Track daily free video generation limit or pro credits.
- **Series Management**: Create recurring series with custom niches, art styles, AI voices, durations, and background music.
- **Video Generation**: Trigger video creation via Supabase backend RPC (`request_video`).
- **Live Progress**: Monitor video generation stages (scripting, imaging, voicing, rendering) with real-time progress bars.
- **Video Player & Sharing**: Built-in Media3 ExoPlayer video player, copyable suggested captions and hashtags, and sharing/saving capabilities.

## Tech Stack

- **UI**: Jetpack Compose, Material Design 3
- **Navigation**: Navigation Compose
- **Video Playback**: Media3 ExoPlayer
- **Image Loading**: Coil Compose
- **Networking**: Ktor Client & Kotlinx Serialization
- **Backend**: Supabase (Postgres, Auth, Storage, RPC)
