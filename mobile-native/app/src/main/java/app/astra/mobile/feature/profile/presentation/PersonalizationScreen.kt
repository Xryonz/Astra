package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.astra.mobile.feature.namecolors.presentation.NameColorsSection
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.HairlineRule
import app.astra.mobile.ui.components.MarginaliaLabel

/** Aba unica que junta edicao de perfil (visual + identidade) e aparencia do app. */
@Composable
fun PersonalizationScreen(onBack: () -> Unit) {
    CosmicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            EditorialTopBar(title = "Personalização", marginalia = "seu perfil e o app", onBack = onBack)

            MarginaliaLabel("perfil", Modifier.padding(start = 22.dp, top = 8.dp))
            ProfileEditSection()

            Spacer(Modifier.height(12.dp))
            HairlineRule(Modifier.padding(horizontal = 22.dp))
            Spacer(Modifier.height(20.dp))

            MarginaliaLabel("cor do nome", Modifier.padding(start = 22.dp, bottom = 4.dp))
            NameColorsSection()

            Spacer(Modifier.height(12.dp))
            HairlineRule(Modifier.padding(horizontal = 22.dp))
            Spacer(Modifier.height(20.dp))

            MarginaliaLabel("aparência", Modifier.padding(start = 22.dp, bottom = 4.dp))
            AppearanceSection()

            Spacer(Modifier.height(28.dp))
        }
    }
}
