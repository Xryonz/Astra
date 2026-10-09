package app.astra.mobile.core.upload

import coil3.map.Mapper
import coil3.request.Options

class RelativeUrlMapper(private val base: String) : Mapper<String, String> {
    override fun map(data: String, options: Options): String? =
        if (data.startsWith("/")) base.trimEnd('/') + data else null
}
