package com.storetd.play

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.storetd.play.core.network.NetworkModule

class StoreTdPlayApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { NetworkModule.okHttpClient }
            .build()
    }
}
