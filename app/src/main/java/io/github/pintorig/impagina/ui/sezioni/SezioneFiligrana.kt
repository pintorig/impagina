// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui.sezioni

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.pintorig.impagina.Watermark
import io.github.pintorig.impagina.WatermarkStyle
import io.github.pintorig.impagina.WatermarkText
import io.github.pintorig.impagina.ui.Nota
import io.github.pintorig.impagina.ui.RigaDiScelta
import io.github.pintorig.impagina.ui.Sezione
import io.github.pintorig.impagina.ui.Spazi

@Composable
fun SezioneFiligrana(
    filigrana: Watermark,
    aperta: Boolean,
    onToggle: () -> Unit,
    onFiligrana: (Watermark) -> Unit
) {
    val riassunto = if (filigrana.style == WatermarkStyle.NONE) "nessuna" else filigrana.style.label
    Sezione("Filigrana", riassunto, aperta, onToggle) {
        RigaDiScelta(
            voci = WatermarkStyle.entries.map { it.label },
            selezionata = WatermarkStyle.entries.indexOf(filigrana.style),
            onSeleziona = { onFiligrana(filigrana.copy(style = WatermarkStyle.entries[it])) }
        )
        Nota(filigrana.style.hint)

        if (filigrana.style != WatermarkStyle.NONE) {
            OutlinedTextField(
                value = filigrana.text,
                onValueChange = { onFiligrana(filigrana.copy(text = it)) },
                label = { Text("Testo della filigrana") },
                supportingText = {
                    Text("${WatermarkText.DATE_TOKEN} diventa la data di oggi.")
                },
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spazi.stretto)
            ) {
                Watermark.PRESETS.forEach { proposta ->
                    AssistChip(
                        onClick = { onFiligrana(filigrana.copy(text = proposta)) },
                        label = { Text(proposta) }
                    )
                }
            }
            // Presidiata anche da un test: nessun preset propone una formula di
            // conformità all'originale, che solo un pubblico ufficiale rilascia.
            Nota(
                "«Copia conforme all'originale» è un'autentica che solo un pubblico " +
                    "ufficiale può rilasciare: una scritta apposta qui non la sostituisce."
            )
        }
    }
}
