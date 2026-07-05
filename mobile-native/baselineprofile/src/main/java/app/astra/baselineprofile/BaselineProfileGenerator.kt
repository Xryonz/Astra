package app.astra.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Journey de startup: abrir o app frio e deixar a primeira tela assentar
// (splash -> auth ou home). Tudo que executar aqui vira AOT no APK final.
// Nao loga em conta nenhuma: o ganho esta na inicializacao do Compose, Hilt,
// rede e navegacao, que rodam antes de qualquer sessao.
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(packageName = "app.astra.mobile") {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            // Da tempo da composicao inicial + primeiras chamadas de rede.
            Thread.sleep(4_000)
        }
    }
}
