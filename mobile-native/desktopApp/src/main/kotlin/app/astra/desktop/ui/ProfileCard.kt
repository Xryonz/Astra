package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import app.astra.mobile.core.network.dto.ProfileUserDto

// O CARTAO DE PERFIL — um so, duas variantes.
//
// POR QUE ISTO EXISTE: o cartao era desenhado DUAS vezes, em arquivos diferentes
// (o de verdade no ProfilePopup, a previa nas Configuracoes). Duas copias da
// mesma tela sempre divergem — e tinham divergido: avatar de 72 num, de 48 no
// outro, deslocamentos diferentes, seccoes fora de ordem. Ou seja, a previa
// prometia uma coisa e os outros viam outra, que e o pior defeito possivel numa
// previa.
//
// Agora ha UMA implementacao. A previa nao pode mais mentir porque e literalmente
// o mesmo desenho — se um dia sair torto, sai torto nos dois, e se conserta uma
// vez.

enum class CardVariante {
    /** O que abre ao clicar num avatar: compacto, 320 de largura. */
    NORMAL,

    /** Versao grande, com mais respiro e a bio inteira. */
    COMPLETO,
}

// Os dados que o cartao desenha. Existe pra a previa poder montar um cartao a
// partir do RASCUNHO (campo a campo, ao vivo, antes de salvar) usando exatamente
// o mesmo caminho do cartao real.
data class DadosDoCartao(
    val nome: String,
    val username: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    val pronomes: String? = null,
    val bio: String? = null,
    val statusEmoji: String? = null,
    val recado: String? = null,
    val fonte: String? = null,
    val status: String? = null,
)

fun ProfileUserDto.paraCartao() = DadosDoCartao(
    nome = displayName ?: username,
    username = username,
    avatarUrl = avatarUrl,
    bannerUrl = bannerUrl,
    bannerColor = bannerColor,
    bannerPositionY = bannerPositionY ?: 50,
    bannerScale = bannerScale ?: 100,
    pronomes = pronouns,
    bio = bio,
    statusEmoji = statusEmoji,
    recado = customStatus,
    fonte = displayFont,
    status = effectiveStatus,
)

@Composable
fun ProfileCard(
    dados: DadosDoCartao,
    variante: CardVariante = CardVariante.NORMAL,
    modifier: Modifier = Modifier,
    rodape: @Composable (() -> Unit)? = null,
) {
    val completo = variante == CardVariante.COMPLETO
    val avatarPx = if (completo) 88 else 72
    val recuoH = if (completo) 20.dp else 16.dp
    // O avatar sobe pra pisar no banner. Metade dele pra fora e o que da a
    // sensacao de "colado" sem cobrir a faixa inteira.
    val subida = (-avatarPx / 2 - 4).dp

    Column(
        modifier
            .clip(RoundedCornerShape(if (completo) 16.dp else 12.dp))
            // Gradiente CONTINUO: uma peca so, do topo do banner ao pe do cartao.
            .profileCardBackdrop(dados.bannerColor)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(if (completo) 16.dp else 12.dp)),
    ) {
        // css = null de proposito: quem pinta o gradiente e o cartao inteiro. Se a
        // faixa repintasse por conta, o gradiente "recomecaria" e apareceria o corte.
        ProfileBanner(
            css = null,
            imageUrl = dados.bannerUrl,
            positionY = dados.bannerPositionY,
            scale = dados.bannerScale,
            fallback = bannerBackdrop(dados.bannerUrl),
            modifier = Modifier.fillMaxWidth().aspectRatio(ProfileBannerAspect),
        )
        Column(Modifier.padding(horizontal = recuoH)) {
            Box(
                Modifier
                    .offset(y = subida)
                    // Anel do fundo do cartao ao redor do avatar: e o que separa a
                    // foto do banner quando as duas sao claras.
                    .clip(CircleShape)
                    .background(Obsidian.raised)
                    .padding(3.dp),
            ) {
                DesktopAvatar(dados.avatarUrl, dados.nome, avatarPx)
            }
            Column(Modifier.offset(y = subida / 2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dados.nome,
                        style = TextStyle(
                            color = Obsidian.text1,
                            fontSize = if (completo) 23.sp else 19.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = profileFontFamily(dados.fonte),
                        ),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusDot(
                        status = userStatus(dados.status),
                        size = if (completo) 12.dp else 10.dp,
                        cutoutColor = Obsidian.raised,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "@${dados.username}",
                        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                    )
                    if (!dados.pronomes.isNullOrBlank()) {
                        Text("  ·  ${dados.pronomes}", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
                    }
                }
                if (!dados.recado.isNullOrBlank() || !dados.statusEmoji.isNullOrBlank()) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        listOfNotNull(
                            dados.statusEmoji?.ifBlank { null },
                            dados.recado?.ifBlank { null },
                        ).joinToString(" "),
                        style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                    )
                }
                if (!dados.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    // Faixa propria pra bio (padrao do cartao do Discord): o bloco
                    // levemente destacado separa "quem e" de "o que escreveu" sem
                    // precisar de titulo.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Obsidian.void.copy(alpha = 0.38f))
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                    ) {
                        Text(
                            dados.bio.orEmpty(),
                            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                            // No completo a bio aparece inteira; no compacto corta.
                            maxLines = if (completo) 12 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (rodape != null) {
                    Spacer(Modifier.height(12.dp))
                    rodape()
                }
                Spacer(Modifier.height(if (completo) 16.dp else 12.dp))
            }
        }
    }
}
