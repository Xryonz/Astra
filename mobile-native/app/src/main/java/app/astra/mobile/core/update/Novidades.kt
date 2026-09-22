package app.astra.mobile.core.update

import android.app.NotificationManager
import android.content.Context
import app.astra.mobile.BuildConfig
import app.astra.mobile.R

object Novidades {
    const val AVISO_ID = 4_209
    private const val MEMORIA = "novidades"
    private const val VISTAS = "vistas"

    fun ler(contexto: Context): List<String>? {
        val texto = runCatching {
            contexto.resources.openRawResource(R.raw.novidades).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return null
        val linhas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (linhas.firstOrNull() != "versao ${BuildConfig.VERSION_NAME}") return null
        return linhas.drop(1).takeIf { it.isNotEmpty() }
    }

    fun paraMostrar(contexto: Context): List<String>? {
        val memoria = contexto.getSharedPreferences(MEMORIA, Context.MODE_PRIVATE)
        val vista = memoria.getString(VISTAS, null)
        if (vista == BuildConfig.VERSION_NAME) return null
        val pacote = runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0) }.getOrNull()
        val veioDeAtualizacao = pacote != null && pacote.lastUpdateTime > pacote.firstInstallTime
        if (vista == null && !veioDeAtualizacao) {
            marcarVistas(contexto)
            return null
        }
        return ler(contexto) ?: run {
            marcarVistas(contexto)
            null
        }
    }

    fun marcarVistas(contexto: Context) {
        contexto.getSharedPreferences(MEMORIA, Context.MODE_PRIVATE).edit()
            .putString(VISTAS, BuildConfig.VERSION_NAME)
            .apply()
        contexto.getSystemService(NotificationManager::class.java)?.cancel(AVISO_ID)
    }
}
