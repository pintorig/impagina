// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui.sezioni

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.pintorig.impagina.DocumentType
import io.github.pintorig.impagina.PageLayouts
import io.github.pintorig.impagina.Reorder
import io.github.pintorig.impagina.ui.AppIcons
import io.github.pintorig.impagina.ui.SchedaFacciata
import io.github.pintorig.impagina.ui.Sezione
import io.github.pintorig.impagina.ui.Spazi

/**
 * Le facciate acquisite. È la sezione aperta all'avvio: da qui si scatta, ed è
 * la prima cosa che serve fare.
 */
@Composable
fun SezioneFacciate(
    tipo: DocumentType,
    quante: Int,
    scatti: List<Bitmap?>,
    resi: List<Bitmap?>,
    aperta: Boolean,
    onToggle: () -> Unit,
    onQuante: (Int) -> Unit,
    onScatta: (Int) -> Unit,
    onScegli: (Int) -> Unit,
    onRuota: (Int) -> Unit,
    onTogli: (Int) -> Unit,
    onSposta: (Int, Int) -> Unit
) {
    val fatte = scatti.count { it != null }
    Sezione("Facciate", "$fatte di $quante", aperta, onToggle) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$quante facciate",
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

        PageLayouts.labelsFor(tipo, quante).chunked(2).forEachIndexed { riga, etichette ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spazi.fra)) {
                etichette.forEachIndexed { colonna, etichetta ->
                    val i = riga * 2 + colonna
                    SchedaFacciata(
                        etichetta = etichetta,
                        // la miniatura mostra il filtro, così l'effetto è
                        // visibile senza scorrere fino all'anteprima
                        bitmap = resi.getOrNull(i) ?: scatti.getOrNull(i),
                        proporzione = tipo.previewRatio,
                        modifier = Modifier.weight(1f),
                        onScatta = { onScatta(i) },
                        onScegli = { onScegli(i) },
                        onRuota = { onRuota(i) },
                        onTogli = { onTogli(i) },
                        indietroPossibile = Reorder.canMoveBack(i),
                        avantiPossibile = Reorder.canMoveForward(scatti, i),
                        onIndietro = { onSposta(i, i - 1) },
                        onAvanti = { onSposta(i, i + 1) }
                    )
                }
                if (etichette.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
