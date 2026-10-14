package com.example.util

import android.content.Context
import java.io.InputStream

object MediaUtils {
    // High-speed CDN sample reel URL as a reliable fallback if binary media is omitted from git checkouts
    const val FALLBACK_REEL_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"

    /**
     * Dynamically resolves the sample reel URI without requiring a compile-time R.raw reference.
     * This guarantees the codebase compiles cleanly in GitHub Actions and any fresh environment,
     * even if binary media files in res/raw/ were not checked into git.
     */
    fun getSampleReelUri(context: Context): String {
        val resId = context.resources.getIdentifier("sample_reel", "raw", context.packageName)
        return if (resId != 0) {
            "android.resource://${context.packageName}/$resId"
        } else {
            FALLBACK_REEL_URL
        }
    }

    /**
     * Safely opens an InputStream to the bundled raw resource if present on the device.
     */
    fun openSampleReelStream(context: Context): InputStream? {
        val resId = context.resources.getIdentifier("sample_reel", "raw", context.packageName)
        return if (resId != 0) {
            try {
                context.resources.openRawResource(resId)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}
