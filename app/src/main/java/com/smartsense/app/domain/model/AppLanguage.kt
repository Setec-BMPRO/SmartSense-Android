package com.smartsense.app.domain.model

import androidx.annotation.StringRes
import com.smartsense.app.R

/**
 * App-supported UI languages.
 *
 * [tag] is the BCP-47 language tag passed to `AppCompatDelegate.setApplicationLocales()`.
 * It must match exactly one of the locales declared in `res/xml/locales_config.xml`.
 *
 * [displayNameRes] points to a string defined in **every** locale's `strings.xml` whose
 * value is the language's own name (e.g. `lang_es` → "Español"), so the picker shows the
 * same label regardless of the app's current locale.
 *
 * [SYSTEM] means "follow the device language" — applied by passing an empty
 * `LocaleListCompat` to AppCompat. It's the on-disk default for fresh installs.
 */
enum class AppLanguage(val tag: String, @StringRes val displayNameRes: Int) {
    SYSTEM("", R.string.language_system_default),
    ENGLISH("en", R.string.lang_en),
    VIETNAMESE("vi", R.string.lang_vi),
    CHINESE_SIMPLIFIED("zh-CN", R.string.lang_zh_cn),
    SPANISH("es", R.string.lang_es),
    FRENCH("fr", R.string.lang_fr),
    GERMAN("de", R.string.lang_de),
    PORTUGUESE_BR("pt-BR", R.string.lang_pt_br),
    ITALIAN("it", R.string.lang_it),
    RUSSIAN("ru", R.string.lang_ru),
    JAPANESE("ja", R.string.lang_ja),
    KOREAN("ko", R.string.lang_ko),
    INDONESIAN("id", R.string.lang_id),
    THAI("th", R.string.lang_th),
    ARABIC("ar", R.string.lang_ar),
    HINDI("hi", R.string.lang_hi);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: SYSTEM

        /** The set of explicit (non-SYSTEM) language tags this app ships translations for. */
        fun supportedTags(): List<String> = entries.filter { it != SYSTEM }.map { it.tag }

        /**
         * Pick the closest [AppLanguage] for a device locale tag like "en-US" or "zh-Hans-CN".
         * - Exact match (case-insensitive) wins.
         * - Otherwise match by primary language subtag (the part before the first `-`),
         *   so a Spanish-speaking user gets [SPANISH] regardless of country.
         * Returns [ENGLISH] when nothing matches so we never silently fall through.
         */
        fun bestMatchForLocale(deviceTag: String): AppLanguage {
            if (deviceTag.isBlank()) return ENGLISH
            val direct = entries.firstOrNull { it.tag.equals(deviceTag, ignoreCase = true) }
            if (direct != null) return direct
            val deviceLang = deviceTag.substringBefore('-').lowercase()
            return entries.firstOrNull {
                it != SYSTEM && it.tag.substringBefore('-').lowercase() == deviceLang
            } ?: ENGLISH
        }
    }
}
