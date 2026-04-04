package com.smartsense.app.util

import android.text.format.DateUtils

object TimeUtils {
    // In TimeUtils.kt
    fun getLastUpdatedText(timestamp: Long?): String {
        val safeTimestamp = timestamp ?: return "No data"
        val now = System.currentTimeMillis()
        val diff = now - safeTimestamp
        val seconds = maxOf(0, diff / 1000L)

        return when {
            seconds < 1 -> "Updated just now"
            seconds < 60 -> "Updated $seconds ${if (seconds == 1L) "sec" else "secs"} ago"
            else -> {
                val ago = DateUtils.getRelativeTimeSpanString(
                    safeTimestamp, now, DateUtils.SECOND_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
                )
                "Updated $ago"
            }
        }
    }
}