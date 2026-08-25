package app.astra.mobile

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import androidx.core.content.getSystemService
import app.astra.mobile.core.crash.CrashReporter
import app.astra.mobile.core.upload.DataUriMapper
import app.astra.mobile.core.upload.RelativeUrlMapper
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AstraApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val lowRam = getSystemService<ActivityManager>()?.isLowRamDevice == true
        return ImageLoader.Builder(context)
            .components {

                add(DataUriMapper())
                add(RelativeUrlMapper(BuildConfig.BASE_URL))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .allowRgb565(lowRam)
            .apply {
                if (lowRam) {
                    memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
                }
            }
            .build()
    }
}
