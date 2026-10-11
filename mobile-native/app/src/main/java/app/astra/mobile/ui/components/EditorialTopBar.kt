package app.astra.mobile.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors

@Composable
fun EditorialTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    modificadorDoTitulo: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 10.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Text(
                text = "‹",
                fontFamily = DmSerif,
                fontSize = 40.sp,
                color = astraColors.text1,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .clearAndSetSemantics {
                        contentDescription = "Voltar"
                        role = Role.Button
                    }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modificadorDoTitulo,
            )
            if (subtitulo != null) {
                Spacer(Modifier.height(2.dp))
                TextoDeApoio(subtitulo, maxLines = 1)
            }
        }
        trailing()
    }
}

@Composable
fun TopBarAction(
    glyph: String,
    rotulo: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = astraColors.accent,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, astraColors.borderMid, CircleShape)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = rotulo
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontFamily = DmSerif,
            fontSize = 26.sp,
            color = color,
        )
    }
}

@Composable
fun TopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = astraColors.accent,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, astraColors.borderMid, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(18.dp))
    }
}
