package com.smartsense.app.util

import java.util.Locale

fun String.uppercaseFirst(): String {
    return this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}

