// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import android.graphics.Bitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.pintorig.impagina.DocumentType
import io.github.pintorig.impagina.PageLayouts
import io.github.pintorig.impagina.Reorder

/**
 * Le facciate come striscia orizzontale sotto il foglio.
 *
 * Restano sulla schermata principale mentre le impostazioni si ritirano nel
 * pannello: sono il contenuto, non un'opzione. Senza di loro non ci sarebbe
 * modo di acquisire niente.
 */
@Composable
fun StrisciaFacciate(
    tipo: DocumentType,
    quante: Int,
    scatti: List<Bitmap?>,
    resi: List<Bitmap?>,
    onQuante: (Int) -> Unit,
    onScatta: (Int) -> Unit,
    onScegli: (Int) -> Unit,
    onRuota: (Int) -> Unit,
    onTogli: (Int) -> Unit,
    onSposta: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = Spazi.bordo)) {
        // Con due facciate — il caso normale — devono entrarci entrambe senza
        // scorrere. La larghezza si ricava dallo spazio che resta, tolta la
        // colonna dei comandi: fissarla a occhio tagliava la seconda scheda.
        val comandi = 48.dp
        val larghezza = (maxWidth - comandi - Spazi.fra * 2) / 2

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spazi.fra),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spazi.fra),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PageLayouts.labelsFor(tipo, quante).forEachIndexed { i, etichetta ->
                    SchedaFacciata(
                        etichetta = etichetta,
                        // la miniatura mostra il filtro: l'effetto si vede senza
                        // aspettare che si rigeneri l'anteprima del foglio
                        bitmap = resi.getOrNull(i) ?: scatti.getOrNull(i),
                        proporzione = tipo.previewRatio,
                        modifier = Modifier.width(larghezza),
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
            }

            // I comandi del numero di facciate restano fermi: dentro lo
            // scorrimento finivano tagliati dal bordo e sembravano rotti.
            Column(verticalArrangement = Arrangement.spacedBy(Spazi.stretto)) {
                OutlinedIconButton(
                    onClick = { onQuante(quante + 1) },
                    enabled = quante < PageLayouts.MAX_SLOTS
                ) { Icon(AppIcons.Piu, contentDescription = "Aggiungi una facciata") }
                OutlinedIconButton(
                    onClick = { onQuante(quante - 1) },
                    enabled = quante > 1
                ) { Icon(AppIcons.Meno, contentDescription = "Togli l'ultima facciata") }
            }
        }
    }
}
