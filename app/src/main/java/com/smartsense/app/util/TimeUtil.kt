package com.smartsense.app.util

import android.content.Context
import android.text.format.DateUtils
import com.smartsense.app.R

object TimeUtils {
    /**
     * Localised "last updated" string. Pass a [Context] so the result follows the user's
     * chosen UI language. The legacy no-context overload below is kept for callers that
     * don't have one (and resolves to default English).
     */
    fun getLastUpdatedText(context: Context, timestamp: Long?): String {
        val safeTimestamp = timestamp ?: return context.getString(R.string.no_data)
        val now = System.currentTimeMillis()
        val diff = now - safeTimestamp
        val seconds = maxOf(0, diff / 1000L)

        return when {
            seconds < 1 -> context.getString(R.string.updated_just_now)
            seconds < 60 -> context.getString(R.string.updated_seconds_ago, seconds.toInt())
            else -> {
                val ago = DateUtils.getRelativeTimeSpanString(
                    safeTimestamp, now, DateUtils.SECOND_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
                )
                context.getString(R.string.updated_x, ago)
            }
        }
    }

    /** Legacy overload — used by ViewModel layers without a Context. Falls back to English. */
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