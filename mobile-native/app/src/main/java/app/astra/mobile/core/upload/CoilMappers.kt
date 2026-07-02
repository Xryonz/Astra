package app.astra.mobile.core.upload

import android.util.Base64
import coil3.map.Mapper
import coil3.request.Options
import java.nio.ByteBuffer

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

class RelativeUrlMapper(private val base: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? =
        if (data.startsWith("/")) base.trimEnd('/') + data else null
}
