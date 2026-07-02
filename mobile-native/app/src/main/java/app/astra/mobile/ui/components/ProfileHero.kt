package app.astra.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage

/**
 * Header de perfil estilo Discord, compartilhado entre o proprio perfil (ProfileSheet)
 * e o de outra pessoa (UserProfileScreen): banner (imagem c/ zoom/posicao ou cor solida)
 * com scrim, avatar sobreposto na borda inferior c/ anel void e ponto de status, e
 * nome + subtitulo abaixo. Coloque o conteudo (bio, etc.) depois deste bloco.
 */
@Composable
fun ProfileHero(
    bannerUrl: String?,
    bannerColor: Color,
    bannerPositionY: Int,
    bannerScale: Int,
    avatarUrl: String?,
    displayName: String,
    displayFont: FontFamily,
    subtitle: String,
    statusColor: Color?,
    modifier: Modifier = Modifier,
    bannerHeight: Int = 132,
    avatarSize: Int = 88,
    contentPadding: Int = 22,
) {
    val ring = 6
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(bannerHeight.dp).background(bannerColor)) {
            if (!bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = bannerScale / 100f
                            scaleY = bannerScale / 100f
                        },
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, (bannerPositionY / 50f - 1f).coerceIn(-1f, 1f)),
                )
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f)),
                ),
            )
            // Avatar pendurado na borda inferior do banner (metade pra fora).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = contentPadding.dp)
                    .offset(y = (avatarSize / 2).dp),
            ) {
                Box(
                    modifier = Modifier
                        .size((avatarSize + ring * 2).dp)
                        .clip(CircleShape)
                        .background(astraColors.void),
                    contentAlignment = Alignment.Center,
                ) {
                    AstraAvatar(avatarUrl, displayName, size = avatarSize)
                }
                if (statusColor != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size((avatarSize / 3.2f).dp)
                            .clip(CircleShape)
                            .background(astraColors.void)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                }
            }
        }

        Spacer(Modifier.height((avatarSize / 2 + 10).dp))

        Column(Modifier.padding(horizontal = contentPadding.dp)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = displayFont,
                color = astraColors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
