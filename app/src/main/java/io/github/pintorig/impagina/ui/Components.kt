// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Una sezione richiudibile: titolo a sinistra, valore attuale a destra.
 *
 * Il valore a destra è il punto: da chiusa, la sezione dice già che cosa
 * contiene, così la schermata si legge tutta in un colpo d'occhio invece di
 * costringere ad aprirle una per una.
 */
@Composable
fun Sezione(
    titolo: String,
    valore: String,
    aperta: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contenuto: @Composable ColumnScope.() -> Unit
) {
    val rotazione by animateFloatAsState(if (aperta) 180f else 0f, label = "freccia")
    Surface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(horizontal = Spazi.bordo, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spazi.fra),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(titolo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!aperta && valore.isNotBlank()) {
                    Text(
                        valore,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    AppIcons.FrecciaGiu,
                    contentDescription = null,
                    modifier = Modifier.padding(start = Spazi.stretto).rotate(rotazione),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(aperta) {
                Column(
                    Modifier.padding(bottom = Spazi.bordo),
                    verticalArrangement = Arrangement.spacedBy(Spazi.fra)
                ) { contenuto() }
            }
        }
    }
}

/** Gruppo di scelte mutuamente esclusive, da due a quattro voci. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RigaDiScelta(
    voci: List<String>,
    selezionata: Int,
    onSeleziona: (Int) -> Unit,
    attiva: Boolean = true
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        voci.forEachIndexed { i, voce ->
            SegmentedButton(
                selected = i == selezionata,
                onClick = { onSeleziona(i) },
                enabled = attiva,
                shape = SegmentedButtonDefaults.itemShape(i, voci.size)
            ) { Text(voce) }
        }
    }
}

/** Nota esplicativa sotto un controllo: presente ovunque, quindi in un posto solo. */
@Composable
fun Nota(testo: String, colore: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(testo, style = MaterialTheme.typography.bodySmall, color = colore)
}
