package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.BotApi
import app.astra.mobile.core.network.dto.BotCommandDto
import org.koin.core.context.GlobalContext

private var cache: List<BotCommandDto>? = null

@Composable
fun rememberBotCommands(): List<BotCommandDto> {
    var cmds by remember { mutableStateOf(cache.orEmpty()) }
    LaunchedEffect(Unit) {
        if (cache == null) {
            cache = runCatching { GlobalContext.get().get<BotApi>().commands().data.orEmpty() }
                .getOrDefault(emptyList())
            cmds = cache.orEmpty()
        }
    }
    return cmds
}

fun matchCommands(draft: String, all: List<BotCommandDto>): List<BotCommandDto> {
    if (!draft.startsWith("/") || all.isEmpty()) return emptyList()
    val typed = draft.lowercase()
    val hits = all.filter { it.name.lowercase().startsWith(typed) }
    if (hits.isEmpty() && all.any { typed.startsWith(it.name.lowercase() + " ") }) return emptyList()
    return hits
}

@Composable
fun CommandPalette(commands: List<BotCommandDto>, onPick: (BotCommandDto) -> Unit) {
    if (commands.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(12.dp)),
    ) {
        Text(
            "COMANDOS",
            style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.5.sp),
            modifier = Modifier.padding(start = 14.dp, top = 11.dp, bottom = 6.dp),
        )
        val abertura = remember { Any() }
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            itemsIndexed(commands, key = { _, c -> c.name }) { indice, cmd ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                CascadeIn(index = indice, listKey = abertura) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (hovered) Obsidian.hover else androidx.compose.ui.graphics.Color.Transparent)
                        .hoverable(interaction)
                        .clickable { onPick(cmd) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        cmd.name,
                        style = TextStyle(
                            color = if (hovered) Obsidian.accent else Obsidian.text1,
                            fontSize = 13.sp,
                            fontFamily = DmMono,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        cmd.description,
                        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (cmd.category.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            cmd.category,
                            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
