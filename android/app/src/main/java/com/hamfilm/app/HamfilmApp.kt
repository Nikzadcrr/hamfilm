package com.hamfilm.app

import android.app.Application
import com.hamfilm.app.data.ApiConfig
import com.hamfilm.app.data.TokenStore

class HamfilmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiConfig.init(this)
        TokenStore.init(this)
    }
}
