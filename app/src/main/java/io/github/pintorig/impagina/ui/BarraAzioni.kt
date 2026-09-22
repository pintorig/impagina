// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * La barra fissa in fondo: peso del file a sinistra, azioni a destra.
 *
 * Salvare e condividere erano in fondo a uno scroll lungo quindici sezioni.
 * Sono le due cose per cui si apre l'app: ora non si scappano mai.
 */
@Composable
fun BarraAzioni(
    peso: String,
    formato: String,
    pronto: Boolean,
    occupato: Boolean,
    onSalva: () -> Unit,
    onCondividi: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.navigationBarsPadding()) {
            if (occupato) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spazi.bordo, vertical = Spazi.fra),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spazi.fra)
            ) {
                Text(
                    peso,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onCondividi, enabled = pronto) {
                        Icon(AppIcons.Condividi, contentDescription = null)
                    }
                }
                Button(onClick = onSalva, enabled = pronto) { Text("Salva $formato") }
            }
        }
    }
}
