package app.astra.mobile

import android.app.Application
import android.os.Build
import app.astra.mobile.core.upload.DataUriMapper
import app.astra.mobile.core.upload.RelativeUrlMapper
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AstraApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {

            add(DataUriMapper())
            add(RelativeUrlMapper(BuildConfig.BASE_URL))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
