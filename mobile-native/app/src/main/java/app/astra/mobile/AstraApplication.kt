package app.astra.mobile

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dagger.hilt.android.HiltAndroidApp

/**
 * Entry point do Hilt — gera o container de DI da aplicacao inteira.
 *
 * Tambem e o ImageLoaderFactory do Coil: registra o decoder animado pra
 * avatares/banners em GIF (e WebP/HEIF animado no API 28+) animarem igual
 * no app web/Capacitor. Sem isso o Coil so mostra o 1o frame.
 */
@HiltAndroidApp
class AstraApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
