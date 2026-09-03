package com.example.visionbridge.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import java.util.Locale

object LocaleHelper {

    fun wrapContext(context: Context, languageCode: String?): Context {
        val locale = getLocale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        applyLocaleToConfiguration(config, locale)

        val configContext = context.createConfigurationContext(config)
        return LocalizedContextWrapper(context, configContext)
    }

    class LocalizedContextWrapper(
        base: Context,
        private val localizedContext: Context
    ) : ContextWrapper(base),
        ActivityResultRegistryOwner,
        OnBackPressedDispatcherOwner,
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner {

        override fun getResources(): Resources {
            return localizedContext.resources
        }

        override fun getAssets(): AssetManager {
            return localizedContext.assets
        }

        override val activityResultRegistry: ActivityResultRegistry
            get() = (baseContext as? ActivityResultRegistryOwner)?.activityResultRegistry
                ?: error("Base context ($baseContext) does not implement ActivityResultRegistryOwner")

        override val onBackPressedDispatcher: OnBackPressedDispatcher
            get() = (baseContext as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher
                ?: error("Base context ($baseContext) does not implement OnBackPressedDispatcherOwner")

        override val lifecycle: Lifecycle
            get() = (baseContext as? LifecycleOwner)?.lifecycle
                ?: error("Base context ($baseContext) does not implement LifecycleOwner")

        override val viewModelStore: ViewModelStore
            get() = (baseContext as? ViewModelStoreOwner)?.viewModelStore
                ?: error("Base context ($baseContext) does not implement ViewModelStoreOwner")

        override val savedStateRegistry: SavedStateRegistry
            get() = (baseContext as? SavedStateRegistryOwner)?.savedStateRegistry
                ?: error("Base context ($baseContext) does not implement SavedStateRegistryOwner")
    }

    fun applyLocaleToConfiguration(config: Configuration, locale: Locale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }
    }

    fun getLocale(languageCode: String?): Locale {
        return when (languageCode?.lowercase()?.trim()) {
            "hi", "hindi" -> Locale("hi", "IN")
            "mr", "marathi" -> Locale("mr", "IN")
            else -> Locale("en", "US")
        }
    }

    fun getLanguageTag(languageCode: String?): String {
        return when (languageCode?.lowercase()?.trim()) {
            "hi", "hindi" -> "hi-IN"
            "mr", "marathi" -> "mr-IN"
            else -> "en-US"
        }
    }
}
