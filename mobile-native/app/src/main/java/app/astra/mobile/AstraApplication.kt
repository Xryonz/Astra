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

    // coil3: o fetcher de rede (OkHttp) e auto-registrado pelo artifact coil-network-okhttp.
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Aparelho fraco (Android Go / pouca RAM): RGB_565 corta pela metade a RAM
        // de cada imagem opaca (Coil ignora quando tem canal alpha) e o cache de
        // memoria encolhe de ~25% pra 15% do heap. Aparelho normal fica intocado.
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
