package com.example.zhukang

import android.content.Context

object SessionPrefs {
    private const val PREF_NAME = "zhukang_session"
    private const val KEY_LAST_MEAL_TYPE = "last_meal_type"
    private const val KEY_ENGLISH_ENABLED = "english_enabled"
    private const val DEFAULT_MEAL_TYPE = "LUNCH"

    fun saveLastMealType(context: Context, mealType: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_MEAL_TYPE, mealType)
            .apply()
    }

    fun getLastMealType(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_MEAL_TYPE, DEFAULT_MEAL_TYPE)
            ?: DEFAULT_MEAL_TYPE
    }

    fun saveEnglishEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENGLISH_ENABLED, enabled)
            .apply()
    }

    fun isEnglishEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENGLISH_ENABLED, false)
    }
}
