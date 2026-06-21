package app.astra.mobile.core.upload

import android.util.Base64
import coil.map.Mapper
import coil.request.Options
import java.nio.ByteBuffer

/**
 * Ensina o Coil a exibir data URIs base64 (avatar/banner/icone, igual web):
 * data:image/...;base64,XXXX -> ByteBuffer (o ByteBufferFetcher embutido pega).
 * Animado (GIF) anima via decoder ja registrado.
 */
class DataUriMapper : Mapper<String, ByteBuffer> {
    override fun map(data: String, options: Options): ByteBuffer? {
        if (!data.startsWith("data:")) return null
        val idx = data.indexOf("base64,")
        if (idx < 0) return null
        val b64 = data.substring(idx + 7)
        val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
        return ByteBuffer.wrap(bytes)
    }
}

/**
 * URL relativa do backend (/uploads/x.webp — anexos de chat) -> URL absoluta.
 * Sem isso o Coil nao acha o host.
 */
class RelativeUrlMapper(private val base: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? =
        if (data.startsWith("/")) base.trimEnd('/') + data else null
}
