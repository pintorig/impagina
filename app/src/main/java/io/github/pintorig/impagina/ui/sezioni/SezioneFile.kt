// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui.sezioni

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.pintorig.impagina.ExportResolution
import io.github.pintorig.impagina.ExportSpec
import io.github.pintorig.impagina.OutputFormat
import io.github.pintorig.impagina.PagePlan
import io.github.pintorig.impagina.QUALITY_MAX
import io.github.pintorig.impagina.QUALITY_MIN
import io.github.pintorig.impagina.QUALITY_STEPS
import io.github.pintorig.impagina.SIZE_TARGETS
import io.github.pintorig.impagina.Sizes
import io.github.pintorig.impagina.ui.Nota
import io.github.pintorig.impagina.ui.RigaDiScelta
import io.github.pintorig.impagina.ui.Sezione
import io.github.pintorig.impagina.ui.Spazi

@Composable
fun SezioneFile(
    export: ExportSpec,
    piano: PagePlan,
    pesabile: Boolean,
    inPesatura: Boolean,
    aperta: Boolean,
    onToggle: () -> Unit,
    onExport: (ExportSpec) -> Unit,
    onTetto: (Int) -> Unit
) {
    Sezione("File", export.format.label, aperta, onToggle) {
        RigaDiScelta(
            voci = OutputFormat.entries.map { it.label },
            selezionata = OutputFormat.entries.indexOf(export.format),
            // un JPEG non ha pagine: oltre la prima resta solo il PDF
            attiva = !piano.isMultiPage,
            onSeleziona = { onExport(export.copy(format = OutputFormat.entries[it])) }
        )
        Nota(
            if (piano.isMultiPage) {
                "Il piano occupa ${piano.pageCount} pagine: solo il PDF può contenerle tutte."
            } else {
                export.format.hint
            }
        )

        // La risoluzione riguarda solo i formati immagine: il PDF incorpora i
        // pixel originali e non ha una densità propria.
        if (export.format.isRaster) {
            RigaDiScelta(
                voci = ExportResolution.entries.map { it.label },
                selezionata = ExportResolution.entries.indexOf(export.resolution),
                onSeleziona = { onExport(export.copy(resolution = ExportResolution.entries[it])) }
            )
            Nota(export.resolution.hint)
        }

        if (export.format.isLossy) {
            Text("Qualità ${export.quality}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = export.quality.toFloat(),
                onValueChange = { onExport(export.copy(quality = it.toInt())) },
                valueRange = QUALITY_MIN.toFloat()..QUALITY_MAX.toFloat(),
                steps = QUALITY_STEPS.size - 2,
                modifier = Modifier.fillMaxWidth()
            )
            Nota("Sotto 60 gli artefatti iniziano a intaccare i caratteri piccoli.")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spazi.stretto)
            ) {
                SIZE_TARGETS.forEach { tetto ->
                    AssistChip(
                        enabled = pesabile && !inPesatura,
                        onClick = { onTetto(tetto) },
                        label = { Text("≤ ${Sizes.format(tetto)}") }
                    )
                }
            }
        }
    }
}
