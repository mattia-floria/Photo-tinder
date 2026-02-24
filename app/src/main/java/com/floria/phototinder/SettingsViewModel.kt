package com.floria.phototinder

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel

private const val PREFS_NAME = "PhotoTinderPrefs"
private const val KEY_THEME = "theme_preference"
private const val KEY_PURE_BLACK = "pure_black_preference"
private const val KEY_LANGUAGE = "language_preference"

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _theme = mutableStateOf(Theme.valueOf(prefs.getString(KEY_THEME, Theme.SYSTEM.name) ?: Theme.SYSTEM.name))
    val theme: State<Theme> = _theme

    private val _pureBlack = mutableStateOf(prefs.getBoolean(KEY_PURE_BLACK, false))
    val pureBlack: State<Boolean> = _pureBlack

    private val _language = mutableStateOf(prefs.getString(KEY_LANGUAGE, "en") ?: "en")
    val language: State<String> = _language

    fun setTheme(theme: Theme) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun setPureBlack(enabled: Boolean) {
        _pureBlack.value = enabled
        prefs.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
    }

    fun setLanguage(language: String) {
        _language.value = language
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }
}

enum class Theme {
    SYSTEM,
    LIGHT,
    DARK
}