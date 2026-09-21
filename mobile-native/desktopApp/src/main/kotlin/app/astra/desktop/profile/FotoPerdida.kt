package app.astra.desktop.profile

import app.astra.desktop.CrashLog
import app.astra.mobile.core.network.BotPersonaApi
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.BotPersonaPatch
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import app.astra.shared.AstraShared
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Base64

object FotoPerdida {

    private const val DISCO_APAGADO = "/uploads/"
    private const val PASTA_DA_MEMORIA = "image-cache"
    private const val CORPO_GUARDADO = ".1"
    private const val TETO = 8L * 1024 * 1024

    suspend fun recuperar(userApi: UserApi): ProfileUserDto? = withContext(Dispatchers.IO) {
        val endereco = runCatching { userApi.fotoPerdida().data?.endereco }.getOrNull() ?: return@withContext null
        val foto = daMemoria(endereco) ?: return@withContext null
        runCatching { userApi.updateProfile(UpdateProfileRequest(avatarUrl = foto)).data?.user }.getOrNull()
    }

    suspend fun recuperarDasBots(api: BotPersonaApi) = withContext(Dispatchers.IO) {
        val personas = runCatching { api.personas().data?.personas }.getOrNull() ?: return@withContext
        for (persona in personas) {
            val foto = persona.avatarUrl?.takeIf { it.startsWith(DISCO_APAGADO) }?.let(::daMemoria)
            val capa = persona.bannerUrl?.takeIf { it.startsWith(DISCO_APAGADO) }?.let(::daMemoria)
            if (foto == null && capa == null) continue
            runCatching { api.ajustar(persona.chave, BotPersonaPatch(avatarUrl = foto, bannerUrl = capa)) }
        }
    }

    private fun daMemoria(endereco: String): String? {
        val bytes = daMemoriaDeImagens(AstraShared.BASE_URL.trimEnd('/') + endereco) ?: return null
        val tipo = tipoDe(bytes) ?: return null
        return "data:$tipo;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    private fun daMemoriaDeImagens(endereco: String): ByteArray? {
        val chave = MessageDigest.getInstance("SHA-256")
            .digest(endereco.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val guardado = File(File(CrashLog.dataDir(), PASTA_DA_MEMORIA), chave + CORPO_GUARDADO)
        if (!guardado.isFile || guardado.length() == 0L || guardado.length() > TETO) return null
        return runCatching { guardado.readBytes() }.getOrNull()
    }

    private fun tipoDe(b: ByteArray): String? = when {
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() -> "image/png"
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "image/jpeg"
        b.size > 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        b.size > 6 && String(b, 0, 3, Charsets.US_ASCII) == "GIF" -> "image/gif"
        else -> null
    }
}
