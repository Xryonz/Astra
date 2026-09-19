package app.astra.mobile.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import java.io.File

object Instalador {

    const val EXTRA_VERSAO = "app.astra.mobile.update.VERSAO"

    fun instalar(contexto: Context, apk: File, versao: String) {
        val instalador = contexto.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(contexto.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessaoId = instalador.createSession(parametros)
        try {
            instalador.openSession(sessaoId).use { sessao ->
                sessao.openWrite("astra.apk", 0, apk.length()).use { saida ->
                    apk.inputStream().use { it.copyTo(saida) }
                    sessao.fsync(saida)
                }
                val resposta = Intent(contexto, ResultadoDaInstalacao::class.java).putExtra(EXTRA_VERSAO, versao)
                val mutavel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val aviso = PendingIntent.getBroadcast(
                    contexto,
                    sessaoId,
                    resposta,
                    PendingIntent.FLAG_UPDATE_CURRENT or mutavel,
                )
                sessao.commit(aviso.intentSender)
            }
        } catch (e: Exception) {
            runCatching { instalador.abandonSession(sessaoId) }
            throw e
        }
    }
}

class ResultadoDaInstalacao : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        val versao = intent.getStringExtra(Instalador.EXTRA_VERSAO) ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmar = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (CuidadorDeAtualizacao.visivel && confirmar != null) {
                    contexto.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    val sessaoId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                    if (sessaoId >= 0) {
                        runCatching { contexto.packageManager.packageInstaller.abandonSession(sessaoId) }
                    }
                    CuidadorDeAtualizacao.pedirToque(contexto)
                }
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> CuidadorDeAtualizacao.falhou(contexto, versao)
        }
    }
}
