package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.CrashLog
import app.astra.desktop.net.DesktopSocket
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.AudioDevices
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// "O que o app esta vendo AGORA".
//
// Existe por um motivo especifico: quase todo bug daqui chegou como "não
// funciona pra mim" — e sem nada pra olhar, a unica saida era adivinhar. O caso
// que doeu: o áudio escolhia o dispositivo errado e ninguem tinha como ver QUAL
// ele tinha escolhido. Esta aba responde as perguntas que separam "o aviso nunca
// chegou" de "chegou e o app ignorou", que são problemas em pontas opostas.
//
// So LE estado — não muda nada. O botao de copiar existe pra a resposta caber
// numa mensagem, quando quem esta com o problema e outra pessoa.

private val HORA = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
internal fun DiagnosticsSection() {
    val socket = remember { GlobalContext.get().get<DesktopSocket>() }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // Redesenha sozinho: diagnostico congelado engana mais do que ajuda.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }

    val connected = remember(tick) { socket.isConnected() }
    val (channels, dms, servers) = remember(tick) { socket.joinedRooms() }
    val events = remember(tick) { socket.recentEvents().asReversed() }
    // Dispositivos: enumerar abre um modulo de audio temporario, entao NAO entra
    // no tick de 1s — so na abertura da aba.
    val outputs = remember { runCatching { AudioDevices.outputs() }.getOrDefault(emptyList()) }
    val inputs = remember { runCatching { AudioDevices.inputs() }.getOrDefault(emptyList()) }

    val rt = Runtime.getRuntime()
    val heapMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
    val heapMax = rt.maxMemory() / 1024 / 1024
    val falhas = remember { File(CrashLog.dataDir(), "falhas.txt") }

    Column(Modifier.fillMaxWidth()) {
        DiagTitle("conexão")
        DiagRow("socket", if (connected) "conectado" else "DESCONECTADO", ok = connected)
        // O motivo importa mais que o estado: token vencido, servidor dormindo e
        // rede caida parecem iguais aqui e tem conserto totalmente diferente.
        if (!connected) {
            remember(tick) { socket.lastError() }?.let { motivo ->
                DiagRow("motivo", motivo.take(60), ok = false)
            }
        }
        DiagRow("constelações ouvindo", servers.size.toString(), ok = true)
        DiagRow("órbitas na sala", channels.size.toString(), ok = true)
        DiagRow("sussurros na sala", dms.size.toString(), ok = true)

        Spacer(Modifier.height(18.dp))
        DiagTitle("áudio")
        DiagRow("saídas encontradas", outputs.size.toString(), ok = outputs.isNotEmpty())
        DiagRow("entradas encontradas", inputs.size.toString(), ok = inputs.isNotEmpty())

        Spacer(Modifier.height(18.dp))
        DiagTitle("app")
        DiagRow("versão", System.getProperty("astra.version") ?: "dev", ok = true)
        DiagRow("desenho", org.jetbrains.skiko.SkikoProperties.renderApi.toString(), ok = true)
        DiagRow("memória (heap)", "$heapMb MB de $heapMax MB", ok = true)
        DiagRow(
            "registro de falhas",
            if (falhas.exists()) "TEM falhas registradas" else "nenhuma falha",
            ok = !falhas.exists(),
        )

        Spacer(Modifier.height(18.dp))
        DiagTitle("últimos avisos recebidos")
        Spacer(Modifier.height(6.dp))
        if (events.isEmpty()) {
            Text(
                "nada ainda — se algo devia ter chegado e não chegou, o problema está do lado do servidor.",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Obsidian.void.copy(alpha = 0.4f))
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                events.take(14).forEach { (at, name) ->
                    Row {
                        Text(
                            HORA.format(Instant.ofEpochMilli(at)),
                            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(name, style = TextStyle(color = Obsidian.text2, fontSize = 11.sp, fontFamily = DmMono))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            if (copied) "copiado" else "copiar tudo",
            style = TextStyle(color = Obsidian.accent, fontSize = 13.sp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Obsidian.accentDim, RoundedCornerShape(8.dp))
                .clickable {
                    clipboard.setText(
                        AnnotatedString(
                            buildReport(connected, servers, channels, dms, outputs, inputs, heapMb, heapMax, events),
                        ),
                    )
                    copied = true
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "cole isto quando pedir ajuda — não vai conteúdo de mensagem nenhuma, só nomes de aviso e horários.",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        )
    }
}

@Composable
private fun DiagTitle(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.5.sp),
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DiagRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(6.dp).height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (ok) Obsidian.success else Obsidian.danger),
        )
        Spacer(Modifier.width(9.dp))
        Text(label, style = TextStyle(color = Obsidian.text2, fontSize = 12.sp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.height(1.dp).weight(1f).background(Color.Transparent))
        Text(
            value,
            style = TextStyle(
                color = if (ok) Obsidian.text1 else Obsidian.danger,
                fontSize = 12.sp,
                fontFamily = DmMono,
            ),
        )
    }
}

private fun buildReport(
    connected: Boolean,
    servers: Set<String>, channels: Set<String>, dms: Set<String>,
    outputs: List<String>, inputs: List<String>,
    heapMb: Long, heapMax: Long,
    events: List<Pair<Long, String>>,
): String = buildString {
    appendLine("Astra — diagnostico")
    appendLine("versao : ${System.getProperty("astra.version") ?: "dev"}")
    appendLine("SO     : ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
    appendLine("desenho: ${org.jetbrains.skiko.SkikoProperties.renderApi}")
    appendLine("heap   : ${heapMb}MB de ${heapMax}MB")
    appendLine("socket : ${if (connected) "conectado" else "DESCONECTADO"}")
    appendLine("salas  : ${servers.size} constelacoes, ${channels.size} orbitas, ${dms.size} sussurros")
    appendLine("saidas : ${outputs.size} -> ${outputs.take(4).joinToString()}")
    appendLine("entradas: ${inputs.size} -> ${inputs.take(4).joinToString()}")
    appendLine()
    appendLine("ultimos avisos:")
    events.take(20).forEach { (at, name) -> appendLine("  ${HORA.format(Instant.ofEpochMilli(at))}  $name") }
}
