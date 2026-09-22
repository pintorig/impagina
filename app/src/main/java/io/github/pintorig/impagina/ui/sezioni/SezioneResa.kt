// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui.sezioni

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.pintorig.impagina.ImageFilter
import io.github.pintorig.impagina.ui.Nota
import io.github.pintorig.impagina.ui.RigaDiScelta
import io.github.pintorig.impagina.ui.Sezione

@Composable
fun SezioneResa(
    filtro: ImageFilter,
    dueFacciate: Boolean,
    inversionePossibile: Boolean,
    aperta: Boolean,
    onToggle: () -> Unit,
    onFiltro: (ImageFilter) -> Unit,
    onInverti: () -> Unit
) {
    Sezione("Resa", filtro.label, aperta, onToggle) {
        RigaDiScelta(
            voci = ImageFilter.entries.map { it.label },
            selezionata = ImageFilter.entries.indexOf(filtro),
            onSeleziona = { onFiltro(ImageFilter.entries[it]) }
        )
        Nota(filtro.hint)
        if (dueFacciate) {
            TextButton(onClick = onInverti, enabled = inversionePossibile) {
                Text("Inverti le due facciate")
            }
        }
    }
}
