// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui.sezioni

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.pintorig.impagina.DocumentType
import io.github.pintorig.impagina.PageLayouts
import io.github.pintorig.impagina.ui.AppIcons
import io.github.pintorig.impagina.ui.Nota
import io.github.pintorig.impagina.ui.Sezione
import io.github.pintorig.impagina.ui.Spazi

@Composable
fun SezioneDocumento(
    tipo: DocumentType,
    aperta: Boolean,
    onToggle: () -> Unit,
    onTipo: (DocumentType) -> Unit,
    quante: Int,
    onQuante: (Int) -> Unit
) {
    Sezione("Documento", "${tipo.label}, $quante facciate", aperta, onToggle) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spazi.stretto)
        ) {
            DocumentType.entries.forEach { t ->
                FilterChip(
                    selected = tipo == t,
                    onClick = { onTipo(t) },
                    label = { Text(t.label) }
                )
            }
        }
        Nota(tipo.hint)

        // Il numero di facciate vive qui da quando i comandi stanno sul foglio:
        // aggiungerne una significa aggiungere uno slot al template.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Facciate: $quante",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedIconButton(onClick = { onQuante(quante - 1) }, enabled = quante > 1) {
                Icon(AppIcons.Meno, contentDescription = "Una facciata in meno")
            }
            Spacer(Modifier.width(Spazi.stretto))
            OutlinedIconButton(
                onClick = { onQuante(quante + 1) },
                enabled = quante < PageLayouts.MAX_SLOTS
            ) {
                Icon(AppIcons.Piu, contentDescription = "Una facciata in più")
            }
        }
    }
}
