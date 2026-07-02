package app.astra.mobile.feature.onboarding.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.ConstellationGraphic
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.EaseSpring
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Orbit
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Star

private data class OnboardSlide(
    val icon: ImageVector,
    val title: String,
    val text: String,
)

private val SLIDES = listOf(
    OnboardSlide(Lucide.Sparkles, "Constelações", "Comunidades onde vocês orbitam juntos. Forje a sua ou entre por convite."),
    OnboardSlide(Lucide.Orbit, "Órbitas", "Cada constelação tem órbitas: canais de conversa e salas de voz."),
    OnboardSlide(Lucide.Star, "Estrelas", "Seus amigos. Adicione pelo @username e acompanhem o brilho um do outro."),
    OnboardSlide(Lucide.MessageCircle, "Sussurros", "Conversas diretas, só entre vocês. Texto, voz e chamada."),
    OnboardSlide(Lucide.Rocket, "Você é o cometa", "Pronto pra riscar o céu. Boa viagem."),
)

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var page by remember { mutableIntStateOf(0) }
    val reduceMotion = LocalAppPrefs.current.reduceMotion
    val isLast = page == SLIDES.lastIndex

    fun finish() {
        viewModel.markDone()
        onDone()
    }

    // Voltar navega entre slides; no primeiro, conclui (nao escapa sem marcar).
    BackHandler { if (page > 0) page-- else finish() }

    CosmicBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // Constelacao se re-desenha a cada slide (key reinicia o Animatable
            // interno); rotaciona por pagina pra parecer um ceu diferente.
            key(page) {
                Box(
                    Modifier.graphicsLayer {
                        rotationZ = page * 63f
                        alpha = 0.45f
                    },
                ) { ConstellationGraphic() }
            }
            Spacer(Modifier.height(26.dp))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (reduceMotion) fadeIn(tween(120)).togetherWith(fadeOut(tween(90)))
                    else (slideInHorizontally(tween(380, easing = EaseSpring)) { it / 3 } + fadeIn(tween(260)))
                        .togetherWith(fadeOut(tween(180)))
                },
                label = "onboarding-slide",
            ) { p ->
                val slide = SLIDES[p]
                val enter = remember { Animatable(if (reduceMotion) 1f else 0f) }
                LaunchedEffect(Unit) {
                    if (!reduceMotion) enter.animateTo(1f, tween(520, easing = EaseOutSoft))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        slide.icon,
                        contentDescription = null,
                        tint = astraColors.accent,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                val e = enter.value
                                scaleX = 0.8f + 0.2f * e
                                scaleY = 0.8f + 0.2f * e
                                rotationZ = -14f * (1f - e)
                                alpha = e
                            },
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = slide.title,
                        fontFamily = DmSerif,
                        fontSize = 30.sp,
                        color = astraColors.text1,
                        modifier = Modifier.graphicsLayer {
                            translationY = (1f - enter.value) * 24f
                            alpha = enter.value
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = slide.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = astraColors.text2,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .graphicsLayer {
                                translationY = (1f - enter.value) * 34f
                                alpha = enter.value
                            },
                    )
                }
            }

            Spacer(Modifier.weight(1.2f))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SLIDES.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == page) astraColors.accent else astraColors.borderMid),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarginaliaLabel(
                    "pular",
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { finish() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(astraColors.accent)
                        .clickable { if (isLast) finish() else page++ }
                        .padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isLast) "Começar" else "Próximo",
                        color = astraColors.textInv,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
