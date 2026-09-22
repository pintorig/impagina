// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui.sezioni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.pintorig.impagina.Combinazione
import io.github.pintorig.impagina.ui.Nota
import io.github.pintorig.impagina.ui.Sezione
import io.github.pintorig.impagina.ui.Spazi

/**
 * I template che mettono più documenti sullo stesso foglio.
 *
 * Servono al caso concreto per cui la gente fotocopia i documenti: lo
 * sportello che chiede identità e tessera sanitaria insieme, o identità e
 * patente. Quattro facciate a dimensione reale entrano in un A4, quindi il
 * risultato resta un foglio solo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SezioneTemplate(
    combinazione: Combinazione?,
    aperta: Boolean,
    onToggle: () -> Unit,
    onCombinazione: (Combinazione?) -> Unit
) {
    Sezione(
        titolo = "Template avanzati",
        valore = combinazione?.label ?: "nessuno",
        aperta = aperta,
        onToggle = onToggle
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spazi.stretto),
            verticalArrangement = Arrangement.spacedBy(Spazi.stretto)
        ) {
            FilterChip(
                selected = combinazione == null,
                onClick = { onCombinazione(null) },
                label = { Text("Documento singolo") }
            )
            Combinazione.entries.forEach { c ->
                FilterChip(
                    selected = combinazione == c,
                    onClick = { onCombinazione(c) },
                    label = { Text(c.label) }
                )
            }
        }
        Nota(
            if (combinazione == null) {
                "Un solo documento per foglio, con le facciate che scegli."
            } else {
                "${combinazione.documenti.size} documenti, ${combinazione.slotCount} facciate: " +
                    "fronte e retro dello stesso documento finiscono sulla stessa riga, " +
                    "il documento dopo sulla riga sotto. Le foto già acquisite restano " +
                    "al loro posto: se l'ordine non torna, spostale dal foglio."
            }
        )
        // Solo formati tessera: il motore calcola una sola dimensione di cella
        // per tutto il piano, e un passaporto ID-3 richiederebbe celle diverse.
        Nota("I template combinano solo documenti in formato tessera, non il passaporto.")
    }
}
