package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.BanDto

// Aba BANIMENTOS: listar e revogar. Curta de proposito — banir acontece no painel
// de membros (botao direito), aqui e so a lista do que ja foi feito.
@Composable
internal fun BansSection(
    bans: List<BanDto>?,
    error: String?,
    onUnban: (userId: String, (String?) -> Unit) -> Unit,
) {
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    if (bans == null) {
        Text(
            error ?: "carregando banimentos…",
            style = TextStyle(color = if (error != null) Obsidian.danger else Obsidian.text3, fontSize = 12.sp),
        )
        return
    }

    Text(
        "quem esta banido nao consegue voltar, nem com convite.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    if (bans.isEmpty()) {
        Text("ninguem banido.", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        return
    }

    msg?.let { (text, ok) ->
        Text(
            text,
            style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(10.dp))
    }

    bans.forEach { ban ->
        BanRow(ban, busy = busy == ban.userId) {
            busy = ban.userId
            msg = null
            onUnban(ban.userId) { err ->
                busy = null
                // Sucesso nao precisa de texto: a linha some da lista quando o pai
                // recarrega. So o erro merece explicacao.
                if (err != null) msg = err to false
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun BanRow(ban: BanDto, busy: Boolean, onUnban: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Obsidian.raised.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopAvatar(ban.user.avatarUrl, ban.user.displayName ?: ban.user.username, 28)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ban.user.displayName ?: ban.user.username,
                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@${ban.user.username}",
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            ban.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                Spacer(Modifier.height(3.dp))
                Text(
                    reason,
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        val src = remember { MutableInteractionSource() }
        val hov by src.collectIsHoveredAsState()
        Box(
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(if (hov && !busy) Obsidian.hover else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.dp, Obsidian.accentDim, RoundedCornerShape(7.dp))
                .hoverable(src)
                .clickable(enabled = !busy, interactionSource = src, indication = null, onClick = onUnban)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                if (busy) "…" else "revogar",
                style = TextStyle(color = Obsidian.accent, fontSize = 12.sp),
            )
        }
    }
}
