package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun DataScreen(onBack: () -> Unit) {
    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Dados e privacidade", marginalia = "seus dados, suas regras", onBack = onBack)

            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("seus dados", Modifier.padding(start = 22.dp, bottom = 10.dp))

            Column(
                Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DataRow("Exportar meus dados", "baixe tudo que o Astra guarda sobre voce", danger = false)
                DataRow("Apagar minha conta", "remove permanentemente sua conta e dados", danger = true)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Em breve. Essas acoes ainda estao sendo construidas com cuidado.",
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DataRow(title: String, sub: String, danger: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, if (danger) astraColors.danger.copy(alpha = 0.25f) else astraColors.border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) astraColors.danger.copy(alpha = 0.7f) else astraColors.text3,
            )
            MarginaliaLabel(sub)
        }
        Text(
            text = "em breve",
            style = MaterialTheme.typography.labelSmall,
            color = astraColors.text3,
        )
    }
}
