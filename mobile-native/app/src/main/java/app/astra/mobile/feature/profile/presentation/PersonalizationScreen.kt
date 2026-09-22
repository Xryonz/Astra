package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.astra.mobile.feature.namecolors.presentation.NameColorsSection
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar

@Composable
fun AparenciaScreen(onBack: () -> Unit) {
    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Aparência", onBack = onBack)
            AppearanceSection()
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun CoresDoNomeScreen(onBack: () -> Unit) {
    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Cores do nome", onBack = onBack)
            NameColorsSection()
            Spacer(Modifier.height(28.dp))
        }
    }
}
