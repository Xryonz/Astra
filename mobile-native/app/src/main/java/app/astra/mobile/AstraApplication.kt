package app.astra.mobile

import android.app.Application
import android.os.Build
import app.astra.mobile.core.crash.CrashReporter
import app.astra.mobile.core.upload.DataUriMapper
import app.astra.mobile.core.upload.RelativeUrlMapper
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AstraApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }

    // coil3: o fetcher de rede (OkHttp) e auto-registrado pelo artifact coil-network-okhttp.
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {

            add(DataUriMapper())
            add(RelativeUrlMapper(BuildConfig.BASE_URL))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
