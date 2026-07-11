package app.astra.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian

// Port do StatusDot do mobile (Canvas puro, mesmas formas do Discord:
// lua no idle, barra no dnd, anel no offline).

enum class UserStatus { ONLINE, IDLE, DND, INVISIBLE, OFFLINE }

fun userStatus(raw: String?): UserStatus = when (raw?.uppercase()) {
    "ONLINE" -> UserStatus.ONLINE
    "IDLE" -> UserStatus.IDLE
    "DND" -> UserStatus.DND
    "INVISIBLE" -> UserStatus.INVISIBLE
    else -> UserStatus.OFFLINE
}

private val StatusGreen = Color(0xFF22C55E)
private val StatusAmber = Color(0xFFF59E0B)
private val StatusRed = Color(0xFFEF4444)
private val StatusGray = Color(0xFF6B7280)

fun statusColor(status: UserStatus): Color = when (status) {
    UserStatus.ONLINE -> StatusGreen
    UserStatus.IDLE -> StatusAmber
    UserStatus.DND -> StatusRed
    UserStatus.INVISIBLE, UserStatus.OFFLINE -> StatusGray
}

@Composable
fun StatusDot(
    status: UserStatus,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    bordered: Boolean = false,
    borderColor: Color = Obsidian.base,
    cutoutColor: Color = Obsidian.base,
) {
    val color = statusColor(status)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2f, s / 2f)
        val ring = if (bordered) s * 0.16f else 0f
        if (bordered) drawCircle(borderColor, s / 2f, c)
        val r = s / 2f - ring
        when (status) {
            UserStatus.ONLINE -> drawCircle(color, r, c)
            UserStatus.IDLE -> {
                drawCircle(color, r, c)
                drawCircle(cutoutColor, r * 0.62f, Offset(c.x - r * 0.34f, c.y - r * 0.34f))
            }
            UserStatus.DND -> {
                drawCircle(color, r, c)
                drawRoundRect(
                    color = cutoutColor,
                    topLeft = Offset(c.x - r * 0.6f, c.y - r * 0.2f),
                    size = Size(r * 1.2f, r * 0.4f),
                    cornerRadius = CornerRadius(r * 0.1f),
                )
            }
            UserStatus.INVISIBLE, UserStatus.OFFLINE -> {
                drawCircle(color, r * 0.74f, c, style = Stroke(width = r * 0.52f))
            }
        }
    }
}
