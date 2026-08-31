package com.example.visionbridge.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    fun wrapContext(context: Context, languageCode: String): Context {
        val locale = getLocale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }

    fun getLocale(languageCode: String): Locale {
        return when (languageCode.lowercase()) {
            "hi" -> Locale.forLanguageTag("hi-IN")
            "mr" -> Locale.forLanguageTag("mr-IN")
            else -> Locale.ENGLISH
        }
    }

    fun getLanguageTag(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "hi" -> "hi-IN"
            "mr" -> "mr-IN"
            else -> "en-US"
        }
    }
}
