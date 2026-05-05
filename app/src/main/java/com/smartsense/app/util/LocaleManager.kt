package com.smartsense.app.util

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.smartsense.app.domain.model.AppLanguage
import java.util.Locale

/**
 * Centralised wrapper around `AppCompatDelegate.setApplicationLocales(...)`.
 *
 * AppCompat persists the chosen locale (since 1.6) — we *also* mirror it into our
 * DataStore so the picker UI can render the current selection without depending on
 * AppCompat's storage layer.
 */
object LocaleManager {

    /** Apply the user's choice to the app. [AppLanguage.SYSTEM] hands control back to the OS. */
    fun apply(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Best-guess BCP-47 tag of the device's primary locale (e.g. "en-US", "vi-VN", "zh-Hans-CN").
     * Used at first-run to seed the user's language to a supported one when possible.
     */
    fun deviceLocaleTag(): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.content.res.Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            android.content.res.Resources.getSystem().configuration.locale
        }
        return locale?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
    }
}
