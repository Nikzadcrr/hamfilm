package com.hamfilm.app.data.api

import com.hamfilm.app.BuildConfig
import com.hamfilm.app.data.ApiConfig
import com.hamfilm.app.data.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    private var retrofit: Retrofit? = null

    val service: ApiService
        get() {
            val current = retrofit
            if (current != null && current.baseUrl().toString() == ApiConfig.baseUrl) {
                return current.create(ApiService::class.java)
            }
            synchronized(this) {
                val r = Retrofit.Builder()
                    .baseUrl(ApiConfig.baseUrl)
                    .client(okHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                retrofit = r
                return r.create(ApiService::class.java)
            }
        }

    /** وقتی کاربر آدرس سرور را عوض می‌کند */
    fun reset() {
        retrofit = null
    }

    private fun okHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        builder.addInterceptor { chain ->
            val req = chain.request().newBuilder()
            val token = TokenStore.token
            if (token.isNotBlank()) req.header("Authorization", "Bearer $token")
            req.header("Accept", "application/json")
            chain.proceed(req.build())
        }

        if (BuildConfig.DEBUG_LOG) {
            val logging = HttpLoggingInterceptor().apply {
                // فقط در debug و بدون بدنه حساس — درس‌گرفته از اشتباه اپ ببینیم
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }
}
