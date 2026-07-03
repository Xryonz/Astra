package app.astra.mobile.core.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.astra.mobile.BuildConfig
import app.astra.mobile.MainActivity
import java.net.URL

// Atalho dinamico por Sussurro: long-press no icone do app mostra as conversas
// recentes, e elas aparecem como alvos de Direct Share no compartilhar do
// Android (a categoria casa com res/xml/shortcuts.xml).
object DmShortcuts {
    const val CATEGORY = "app.astra.mobile.category.SHARE_TARGET"
    const val EXTRA_CONV_ID = "shareConversationId"
    const val EXTRA_CONV_NAME = "shareConversationName"

    fun idFor(conversationId: String) = "dm-$conversationId"

    fun conversationIdFrom(shortcutId: String?): String? =
        if (shortcutId != null && shortcutId.startsWith("dm-")) {
            shortcutId.substring(3).takeIf { it.isNotBlank() }
        } else {
            null
        }

    // Chamar em IO: baixa o avatar (best-effort; sem avatar vira inicial ambar).
    fun push(context: Context, conversationId: String, name: String, avatarUrl: String?) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_CONV_ID, conversationId)
                putExtra(EXTRA_CONV_NAME, name)
            }
            val icon = loadAvatarIcon(avatarUrl) ?: letterIcon(name)
            val shortcut = ShortcutInfoCompat.Builder(context, idFor(conversationId))
                .setShortLabel(name.ifBlank { "Conversa" })
                .setIcon(icon)
                .setIntent(intent)
                .setLongLived(true)
                .setPerson(Person.Builder().setName(name).setIcon(icon).build())
                .setCategories(setOf(CATEGORY))
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (_: Exception) {
            // Atalho e conveniencia — falhou, segue o jogo.
        }
    }

    private fun loadAvatarIcon(url: String?): IconCompat? {
        if (url.isNullOrBlank()) return null
        val full = if (url.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + url else url
        return try {
            val bytes = URL(full).readBytes()
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            IconCompat.createWithBitmap(circleCrop(Bitmap.createScaledBitmap(raw, 128, 128, true)))
        } catch (_: Exception) {
            null
        }
    }

    private fun letterIcon(name: String): IconCompat {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9A96E.toInt() }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF141210.toInt()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val y = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(name.trim().take(1).uppercase().ifBlank { "?" }, size / 2f, y, text)
        return IconCompat.createWithBitmap(bmp)
    }

    private fun circleCrop(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = src.width / 2f
        canvas.drawCircle(r, r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
