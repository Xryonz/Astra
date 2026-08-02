package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary

// A IDENTIDADE DO ASTRA PRO WINDOWS.
//
// No Windows nao existe "pedir permissao de notificacao" pra app de area de
// trabalho — nao ha janelinha como a do navegador. O que existe e IDENTIDADE: o
// AppUserModelID. E por ele que o Windows sabe que aquele aviso e "do Astra",
// junta os avisos do mesmo app, mostra o nome e o icone certos e cria a entrada
// em Configuracoes > Sistema > Notificacoes.
//
// Sem AUMID o processo e anonimo. Foi o que confirmei no registro desta maquina:
// dos 36 apps que o Windows conhece em Notifications\Settings, NENHUM era o
// Astra. O aviso ate podia sair da bandeja, mas nao havia app nenhum pra ligar,
// desligar ou configurar — nem pro Windows nem pra quem usa.
//
// TEM QUE SER ANTES do AWT/bandeja: a chamada vale pro processo inteiro, e o
// Windows carimba a identidade quando o icone de bandeja nasce. Depois disso, e
// tarde.
//
// O valor segue a convencao Empresa.Produto e NAO pode mudar entre versoes: e a
// chave da entrada nas configuracoes do Windows. Mudar equivale a virar outro
// app, e as preferencias do usuario ficariam orfas.
object WindowsAppId {
    const val AUMID = "Xryonz.Astra"

    private interface Shell32 : StdCallLibrary {
        fun SetCurrentProcessExplicitAppUserModelID(appID: WString): Int
    }

    fun aplicar() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        // Falha aqui nao pode derrubar o app: sem AUMID o Astra so volta a ser
        // anonimo pro Windows, que e exatamente como ele era antes.
        runCatching {
            Native.load("shell32", Shell32::class.java)
                .SetCurrentProcessExplicitAppUserModelID(WString(AUMID))
        }
    }
}
