package com.hamfilm.app.data

import android.content.Context
import android.content.SharedPreferences
import com.hamfilm.app.BuildConfig

/**
 * پیکربندی سرور:
 * کاربر می‌تواند بین بک‌اند کلادفلر و VPS از صفحه تنظیمات جابه‌جا شود.
 */
object ApiConfig {
    const val PREFS = "hamfilm_config"
    private const val KEY_BASE = "api_base"

    @Volatile
    var baseUrl: String = BuildConfig.DEFAULT_API_BASE
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        baseUrl = prefs.getString(KEY_BASE, BuildConfig.DEFAULT_API_BASE) ?: BuildConfig.DEFAULT_API_BASE
    }

    fun setBaseUrl(context: Context, url: String) {
        var clean = url.trim().trimEnd('/')
        if (!clean.endsWith("/")) clean += "/"
        if (!clean.startsWith("http")) clean = "https://$clean"
        baseUrl = clean
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE, clean).apply()
    }

    /** آدرس WebSocket اتاق‌ها — از روی همان هاست ساخته می‌شود */
    val wsBase: String
        get() = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/') + "/ws/"
}

/** ذخیره‌سازی امن نشست کاربر */
object TokenStore {
    private const val PREFS = "hamfilm_session"
    private const val KEY_TOKEN = "jwt"
    private const val KEY_NAME = "name"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_EMAIL = "email"
    private const val KEY_ONBOARDED = "onboarded"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOKEN, v).apply()

    var name: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(v) = prefs.edit().putString(KEY_NAME, v).apply()

    var avatar: String
        get() = prefs.getString(KEY_AVATAR, "🎬") ?: "🎬"
        set(v) = prefs.edit().putString(KEY_AVATAR, v).apply()

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_EMAIL, v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
