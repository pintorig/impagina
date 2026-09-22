// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.pintorig.impagina.ExportSpec
import io.github.pintorig.impagina.ImageFilter
import io.github.pintorig.impagina.LayoutSpec
import io.github.pintorig.impagina.PagePlan
import io.github.pintorig.impagina.Watermark
import io.github.pintorig.impagina.ui.sezioni.SezioneDocumento
import io.github.pintorig.impagina.ui.sezioni.SezioneFile
import io.github.pintorig.impagina.ui.sezioni.SezioneFiligrana
import io.github.pintorig.impagina.ui.sezioni.SezioneFoglio
import io.github.pintorig.impagina.ui.sezioni.SezioneResa

/** Quale sezione è aperta nel pannello: una per volta. */
enum class Pannello { DOCUMENTO, RESA, FOGLIO, FILIGRANA, FILE }

/**
 * Le impostazioni, che salgono dal basso solo quando servono.
 *
 * Stanno fuori dalla schermata principale di proposito: si toccano una volta
 * ogni tanto, mentre il foglio si guarda in continuazione. Lasciarle sempre a
 * vista rubava al foglio i due terzi dello schermo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PannelloOpzioni(
    spec: LayoutSpec,
    filtro: ImageFilter,
    export: ExportSpec,
    piano: PagePlan,
    dimensioneRealePossibile: Boolean,
    pesabile: Boolean,
    inPesatura: Boolean,
    inversionePossibile: Boolean,
    sezione: Pannello?,
    onSezione: (Pannello?) -> Unit,
    onSpec: (LayoutSpec) -> Unit,
    onFiltro: (ImageFilter) -> Unit,
    onExport: (ExportSpec) -> Unit,
    onFiligrana: (Watermark) -> Unit,
    onInverti: () -> Unit,
    onTetto: (Int) -> Unit,
    onSalvaPreset: () -> Unit,
    onChiudi: () -> Unit,
    /** I preset: li passa MainActivity, che e' l'unica a conoscerli. */
    intestazione: @Composable ColumnScope.() -> Unit = {}
) {
    val stato = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun apri(p: Pannello) = onSezione(if (sezione == p) null else p)

    ModalBottomSheet(onDismissRequest = onChiudi, sheetState = stato) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spazi.fra)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spazi.stretto)
        ) {
            intestazione()

            SezioneDocumento(
                tipo = spec.documentType,
                aperta = sezione == Pannello.DOCUMENTO,
                onToggle = { apri(Pannello.DOCUMENTO) },
                onTipo = { onSpec(spec.copy(documentType = it, slotCount = it.slotLabels.size)) }
            )
            SezioneResa(
                filtro = filtro,
                dueFacciate = spec.slotCount == 2,
                inversionePossibile = inversionePossibile,
                aperta = sezione == Pannello.RESA,
                onToggle = { apri(Pannello.RESA) },
                onFiltro = onFiltro,
                onInverti = onInverti
            )
            SezioneFoglio(
                spec = spec,
                dimensioneRealePossibile = dimensioneRealePossibile,
                aperta = sezione == Pannello.FOGLIO,
                onToggle = { apri(Pannello.FOGLIO) },
                onSpec = onSpec
            )
            SezioneFiligrana(
                filigrana = spec.watermark,
                aperta = sezione == Pannello.FILIGRANA,
                onToggle = { apri(Pannello.FILIGRANA) },
                onFiligrana = onFiligrana
            )
            SezioneFile(
                export = export,
                piano = piano,
                pesabile = pesabile,
                inPesatura = inPesatura,
                aperta = sezione == Pannello.FILE,
                onToggle = { apri(Pannello.FILE) },
                onExport = onExport,
                onTetto = onTetto
            )

            TextButton(onClick = onSalvaPreset, modifier = Modifier.fillMaxWidth()) {
                Text("Salva queste impostazioni come preset")
            }
            Nota(
                "Tutta l'elaborazione avviene sul dispositivo: nessuna immagine viene " +
                    "inviata in rete. I preset contengono solo impostazioni, mai le scansioni."
            )
        }
    }
}
