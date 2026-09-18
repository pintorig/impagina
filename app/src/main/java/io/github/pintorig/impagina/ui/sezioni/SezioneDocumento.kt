// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui.sezioni

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.pintorig.impagina.DocumentType
import io.github.pintorig.impagina.ui.Nota
import io.github.pintorig.impagina.ui.Sezione
import io.github.pintorig.impagina.ui.Spazi

@Composable
fun SezioneDocumento(
    tipo: DocumentType,
    aperta: Boolean,
    onToggle: () -> Unit,
    onTipo: (DocumentType) -> Unit
) {
    Sezione("Documento", tipo.label, aperta, onToggle) {
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
    }
}
