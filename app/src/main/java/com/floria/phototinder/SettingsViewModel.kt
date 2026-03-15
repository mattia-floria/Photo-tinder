package com.floria.phototinder

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel

private const val PREFS_NAME = "PhotoTinderPrefs"
private const val KEY_THEME = "theme_preference"
private const val KEY_PURE_BLACK = "pure_black_preference"
private const val KEY_LANGUAGE = "language_preference"
private const val KEY_PRIMARY_COLOR = "primary_color_preference"
private const val KEY_SECONDARY_COLOR = "secondary_color_preference"
private const val KEY_TERTIARY_COLOR = "tertiary_color_preference"

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _theme = mutableStateOf(Theme.valueOf(prefs.getString(KEY_THEME, Theme.SYSTEM.name) ?: Theme.SYSTEM.name))
    val theme: State<Theme> = _theme

    private val _pureBlack = mutableStateOf(prefs.getBoolean(KEY_PURE_BLACK, false))
    val pureBlack: State<Boolean> = _pureBlack

    private val _language = mutableStateOf(prefs.getString(KEY_LANGUAGE, "en") ?: "en")
    val language: State<String> = _language

    private val _primaryColor = mutableStateOf(if (prefs.contains(KEY_PRIMARY_COLOR)) Color(prefs.getInt(KEY_PRIMARY_COLOR, 0)) else Color.Transparent)
    val primaryColor: State<Color> = _primaryColor

    private val _secondaryColor = mutableStateOf(if (prefs.contains(KEY_SECONDARY_COLOR)) Color(prefs.getInt(KEY_SECONDARY_COLOR, 0)) else Color.Transparent)
    val secondaryColor: State<Color> = _secondaryColor

    private val _tertiaryColor = mutableStateOf(if (prefs.contains(KEY_TERTIARY_COLOR)) Color(prefs.getInt(KEY_TERTIARY_COLOR, 0)) else Color.Transparent)
    val tertiaryColor: State<Color> = _tertiaryColor

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

    fun setPalette(primary: Color, secondary: Color, tertiary: Color) {
        _primaryColor.value = primary
        _secondaryColor.value = secondary
        _tertiaryColor.value = tertiary
        prefs.edit()
            .putInt(KEY_PRIMARY_COLOR, primary.toArgb())
            .putInt(KEY_SECONDARY_COLOR, secondary.toArgb())
            .putInt(KEY_TERTIARY_COLOR, tertiary.toArgb())
            .apply()
    }
}

enum class Theme {
    SYSTEM,
    LIGHT,
    DARK
}
