package app.astra.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

// ABRIR JUNTO COM O WINDOWS.
//
// Faz sentido porque o Astra **já vive na bandeja**: quem fecha no X continua
// recebendo aviso de mensagem. Sem isto, esse segundo plano só começa quando a
// pessoa lembra de abrir o app — e um mensageiro que só recebe depois de você
// lembrar dele é um mensageiro que não recebe.
//
// O REGISTRO É A VERDADE, e não uma preferência nossa. É o mesmo lugar que o
// Gerenciador de Tarefas (aba Inicializar) mostra e deixa desligar — se
// guardássemos a resposta por fora, o app diria "ligado" para uma coisa que o
// Windows já tinha desligado, e a pessoa não teria como saber quem está mentindo.
// Ler o registro custa microssegundos e nunca discorda de ninguém.
//
// Chave: HKEY_CURRENT_USER, ou seja, só esta conta do Windows. A de máquina
// inteira (HKLM) exigiria elevação e mexeria no login dos outros usuários do PC —
// nenhum app de mensagem tem esse direito.
object InicioComWindows {

    private const val CHAVE = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NOME = "Astra"

    // Sem app empacotado não há o que registrar: num run de desenvolvimento o
    // caminho do processo é o java.exe do JDK, e gravar ISSO no arranque do
    // Windows deixaria pra trás uma entrada quebrada que sobrevive ao próximo
    // pacote. Mesma lição do DesktopShortcut.
    fun disponivel(): Boolean = caminhoDoExe() != null

    fun ligado(): Boolean = runCatching {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, CHAVE, NOME)
    }.getOrDefault(false)

    // A ordem "abrir escondido" mora no PRÓPRIO comando registrado, e não numa
    // preferência à parte. Assim só existe um lugar onde a resposta pode estar, e
    // ela é a mesma que o Windows vai executar.
    fun escondido(): Boolean = runCatching {
        Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, CHAVE, NOME)
            ?.contains(ARG_MINIMIZADO) == true
    }.getOrDefault(false)

    fun aplicar(ligar: Boolean, escondido: Boolean): Boolean {
        val exe = caminhoDoExe() ?: return false
        return runCatching {
            if (ligar) {
                // Aspas no caminho: "Program Files" tem espaço, e sem elas o Windows
                // tenta abrir "C:\Program" e desiste calado no logon seguinte.
                val comando = buildString {
                    append('"').append(exe).append('"')
                    if (escondido) append(' ').append(ARG_MINIMIZADO)
                }
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, CHAVE, NOME, comando)
            } else {
                if (ligado()) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, CHAVE, NOME)
            }
            true
        }.getOrDefault(false)
    }

    private fun caminhoDoExe(): String? =
        System.getProperty("jpackage.app-path")?.takeIf { it.endsWith(".exe", true) }
}
