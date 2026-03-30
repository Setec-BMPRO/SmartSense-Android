package com.smartsense.app.util

import java.util.Locale

fun String.uppercaseFirst(): String {
    return this.lowercase().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}

