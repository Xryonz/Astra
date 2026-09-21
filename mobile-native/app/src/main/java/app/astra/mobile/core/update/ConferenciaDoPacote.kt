package app.astra.mobile.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

object ConferenciaDoPacote {

    fun motivoParaRecusar(contexto: Context, apk: File): String? {
        val gerente = contexto.packageManager
        val novo = runCatching { doArquivo(gerente, apk) }.getOrNull()
            ?: return "o pacote baixado não abre"
        if (novo.packageName != contexto.packageName) return "o pacote baixado é de outro aplicativo"
        val instalado = runCatching { doInstalado(gerente, contexto.packageName) }.getOrNull() ?: return null
        if (PackageInfoCompat.getLongVersionCode(novo) <= PackageInfoCompat.getLongVersionCode(instalado)) {
            return "o pacote baixado não é mais novo que o instalado"
        }
        val chavesNovas = certificados(novo)
        val chavesInstaladas = certificados(instalado)
        if (chavesNovas.isNotEmpty() && chavesInstaladas.isNotEmpty() && chavesNovas.intersect(chavesInstaladas).isEmpty()) {
            return "o pacote baixado foi assinado com outra chave"
        }
        return null
    }

    private fun bandeiras(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    private fun doArquivo(gerente: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gerente.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(bandeiras().toLong()))
        } else {
            @Suppress("DEPRECATION")
            gerente.getPackageArchiveInfo(apk.path, bandeiras())
        }

    private fun doInstalado(gerente: PackageManager, pacote: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gerente.getPackageInfo(pacote, PackageManager.PackageInfoFlags.of(bandeiras().toLong()))
        } else {
            @Suppress("DEPRECATION")
            gerente.getPackageInfo(pacote, bandeiras())
        }

    private fun certificados(info: PackageInfo): Set<String> {
        val assinaturas: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return assinaturas.orEmpty().map { impressao(it.toByteArray()) }.toSet()
    }

    private fun impressao(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
