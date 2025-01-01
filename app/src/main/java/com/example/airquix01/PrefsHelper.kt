package com.example.airquix01

import android.content.Context
import android.content.SharedPreferences

object PrefsHelper {
    private const val PREF_NAME = "airquix_prefs"
    private const val KEY_LOGGING_AI = "logging_ai"
    private const val KEY_LOGGING_MANUAL = "logging_manual"
    private const val KEY_MANUAL_ENV = "manual_env"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun setLoggingAi(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOGGING_AI, enabled).apply()
    }

    fun isLoggingAi(context: Context) = getPrefs(context).getBoolean(KEY_LOGGING_AI, false)

    fun setLoggingManual(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOGGING_MANUAL, enabled).apply()
    }

    fun isLoggingManual(context: Context) = getPrefs(context).getBoolean(KEY_LOGGING_MANUAL, false)

    fun setManualEnv(context: Context, env: String?) {
        getPrefs(context).edit().putString(KEY_MANUAL_ENV, env ?: "").apply()
    }

    fun getManualEnv(context: Context) = getPrefs(context).getString(KEY_MANUAL_ENV, "")?.takeIf { it.isNotEmpty() }
}
