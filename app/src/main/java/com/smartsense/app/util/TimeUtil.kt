package com.smartsense.app.util

import android.text.format.DateUtils

object TimeUtils {

    fun getLastUpdatedText(timestamp: Long?): String {
        val safeTimestamp = timestamp ?: 0L
        if (safeTimestamp <= 0L) return "No data"

        val now = System.currentTimeMillis()
        val diff = now - safeTimestamp

        return if (diff < 10_000L) {
            "Updated just now"
        } else {
            val ago = DateUtils.getRelativeTimeSpanString(
                safeTimestamp,
                now,
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()

            "Updated $ago"
        }
    }
}