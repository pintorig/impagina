// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui.sezioni

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.pintorig.impagina.GridArrangement
import io.github.pintorig.impagina.LayoutSpec
import io.github.pintorig.impagina.PageOrientation
import io.github.pintorig.impagina.Sizing
import io.github.pintorig.impagina.ui.RigaDiScelta
import io.github.pintorig.impagina.ui.Sezione
import io.github.pintorig.impagina.ui.Spazi

@Composable
fun SezioneFoglio(
    spec: LayoutSpec,
    dimensioneRealePossibile: Boolean,
    aperta: Boolean,
    onToggle: () -> Unit,
    onSpec: (LayoutSpec) -> Unit
) {
    val riassunto = "${spec.orientation.label}, ${spec.arrangement.label.lowercase()}"
    Sezione("Foglio", riassunto, aperta, onToggle) {
        RigaDiScelta(
            voci = Sizing.entries.map { it.label },
            selezionata = Sizing.entries.indexOf(spec.sizing),
            attiva = dimensioneRealePossibile,
            onSeleziona = { onSpec(spec.copy(sizing = Sizing.entries[it])) }
        )
        RigaDiScelta(
            voci = GridArrangement.entries.map { it.label },
            selezionata = GridArrangement.entries.indexOf(spec.arrangement),
            onSeleziona = { onSpec(spec.copy(arrangement = GridArrangement.entries[it])) }
        )
        RigaDiScelta(
            voci = PageOrientation.entries.map { it.label },
            selezionata = PageOrientation.entries.indexOf(spec.orientation),
            onSeleziona = { onSpec(spec.copy(orientation = PageOrientation.entries[it])) }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = spec.showLabels,
                onCheckedChange = { onSpec(spec.copy(showLabels = it)) }
            )
            Spacer(Modifier.width(Spazi.fra))
            Text("Didascalie sotto ogni facciata")
        }
    }
}
