package com.storetd.play.core.network

import com.storetd.play.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {
    
    val userAgentInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 15; moto g84 5G Build/V1TCS35H.88-20-1-6-1)")
            .build()
        chain.proceed(request)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    // You can access Xtream config dynamically here from BuildConfig which is generated from .env
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
    val xtreamUser: String = BuildConfig.XTREAM_USER
    val xtreamPassword: String = BuildConfig.XTREAM_PASSWORD
}
