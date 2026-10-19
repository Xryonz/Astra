package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.dto.ChannelVisibilityDto
import app.astra.mobile.core.network.dto.RoleDto
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

@Composable
internal fun VisibilidadeDaOrbitaDialog(
    nomeDaOrbita: String,
    carregar: ((ChannelVisibilityDto?, String?) -> Unit) -> Unit,
    carregarCargos: ((List<RoleDto>?, String?) -> Unit) -> Unit,
    salvar: (privada: Boolean, cargos: List<String>, aoTerminar: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var privada by remember { mutableStateOf(false) }
    var escolhidos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cargos by remember { mutableStateOf<List<RoleDto>?>(null) }
    var erroDeCargos by remember { mutableStateOf<String?>(null) }
    var lendo by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        carregar { visto, falha ->
            if (visto != null) {
                privada = visto.isPrivate
                escolhidos = visto.roleIds.toSet()
            }
            erro = falha
            lendo = false
        }
        carregarCargos { lista, falha ->
            cargos = lista
            erroDeCargos = falha
        }
    }

    Dialogo(titulo = "quem vê $nomeDaOrbita", aoFechar = onDismiss) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(14.dp))

        if (lendo) {
            Text("lendo…", style = Tipo.descricao)
        } else {
            ToggleRow(
                title = "órbita privada",
                sub = "quando ligada, só os cargos escolhidos entram aqui",
                on = privada,
                onChange = { privada = it },
            )

            if (privada) {
                FieldLabel("cargos que veem")
                val lista = cargos
                val falhaDosCargos = erroDeCargos
                when {
                    falhaDosCargos != null -> RecadoDoDialogo(falhaDosCargos)
                    lista == null -> Text("lendo os cargos…", style = Tipo.descricao)
                    lista.isEmpty() -> Text(
                        "esta constelação ainda não tem cargos. Crie um em Configurações › Cargos.",
                        style = Tipo.apoio,
                    )
                    else -> LazyColumn(
                        Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(lista, key = { it.id }) { cargo ->
                            LinhaDeCargo(cargo, cargo.id in escolhidos) {
                                escolhidos = if (cargo.id in escolhidos) escolhidos - cargo.id
                                else escolhidos + cargo.id
                            }
                        }
                    }
                }
                if (escolhidos.isEmpty() && erroDeCargos == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "sem nenhum cargo marcado, só quem administra a constelação vê esta órbita.",
                        style = Tipo.apoio,
                    )
                }
            }
        }

        erro?.let {
            Spacer(Modifier.height(10.dp))
            RecadoDoDialogo(it)
        }

        RodapeDoDialogo {
            BotaoDeDialogo("cancelar", TipoDeBotao.SECUNDARIO, onClick = fechar, ligado = !salvando)
            BotaoDeDialogo(
                "salvar",
                TipoDeBotao.PRINCIPAL,
                onClick = {
                    salvando = true
                    erro = null
                    salvar(privada, escolhidos.toList()) { falha ->
                        salvando = false
                        if (falha == null) onDismiss() else erro = falha
                    }
                },
                ligado = !lendo && !salvando,
                foco = focoInicial,
            )
        }
    }
}

@Composable
private fun LinhaDeCargo(cargo: RoleDto, marcado: Boolean, aoTocar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val hover by interacao.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clickScale(interacao, formaDoFoco = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (marcado) Obsidian.active else if (hover) Obsidian.hover else Color.Transparent)
            .hoverable(interacao)
            .clickable(interactionSource = interacao, indication = null, onClick = aoTocar)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(memberRoleColor(cargo.color) ?: Obsidian.text3),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            cargo.name,
            style = TextStyle(
                color = if (marcado) Obsidian.text1 else Obsidian.text2,
                fontSize = 13.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (marcado) LIcon(Lucide.Check, tint = Obsidian.accent, size = 14.dp)
    }
}
