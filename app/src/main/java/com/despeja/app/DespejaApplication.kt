package com.despeja.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class DespejaApplication : Application(), ImageLoaderFactory {
    /** Coil con soporte para sacar miniaturas de vídeos. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
