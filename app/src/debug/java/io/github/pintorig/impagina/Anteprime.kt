// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.pintorig.impagina.ui.AnteprimaFoglio
import io.github.pintorig.impagina.ui.BarraAzioni
import io.github.pintorig.impagina.ui.StrisciaFacciate
import io.github.pintorig.impagina.ui.ImpaginaTheme
import io.github.pintorig.impagina.ui.Spazi
import io.github.pintorig.impagina.ui.sezioni.SezioneDocumento
import io.github.pintorig.impagina.ui.sezioni.SezioneFile
import io.github.pintorig.impagina.ui.sezioni.SezioneFoglio
import io.github.pintorig.impagina.ui.sezioni.SezioneFiligrana
import io.github.pintorig.impagina.ui.sezioni.SezioneResa

/**
 * Anteprime della schermata, da guardare nel pannello Preview di Android
 * Studio senza installare niente sul telefono.
 *
 * Stanno nel source set `debug` perché `ui-tooling`, che le renderizza, è una
 * `debugImplementation`: così non entrano nel build di release. Non sono test,
 * non verificano niente da sole — servono a vedere.
 *
 * Funzionano perché i composable delle sezioni sono puri: ricevono valori e
 * callback, non un `Context` né un'`Activity`. Se un giorno una sezione
 * smette di comparire qui, è il segno che si è portata dentro una dipendenza
 * da Android che poteva restare fuori.
 */
private val spec = LayoutSpec()
private val piano = PageLayouts.computePlan(spec)

/**
 * La schermata come si presenta all'avvio: il foglio si prende tutto, le
 * facciate stanno in una striscia sotto, le impostazioni non ci sono — vivono
 * nel pannello che sale dal basso.
 */
@Composable
private fun SchermataIniziale() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        AnteprimaFoglio(
            anteprima = null,
            piano = piano,
            pagina = 0,
            onPagina = {},
            modifier = Modifier.weight(1f).fillMaxWidth()
                .padding(horizontal = Spazi.bordo, vertical = Spazi.fra)
        )
        StrisciaFacciate(
            tipo = DocumentType.CARTA_IDENTITA,
            quante = 2,
            scatti = listOf(null, null),
            resi = emptyList(),
            onQuante = {}, onScatta = {}, onScegli = {},
            onRuota = {}, onTogli = {}, onSposta = { _, _ -> },
            modifier = Modifier.padding(bottom = Spazi.fra)
        )
        BarraAzioni("—", "PDF", false, false, {}, {})
    }
}

/** Le sezioni tutte chiuse: si legge il valore a destra di ognuna? */
@Composable
private fun SoloSezioni() {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(Spazi.bordo),
        verticalArrangement = Arrangement.spacedBy(Spazi.stretto)
    ) {
        SezioneDocumento(DocumentType.PASSAPORTO, false, {}, {})
        SezioneResa(ImageFilter.GRAYSCALE, false, false, false, {}, {}, {})
        SezioneFoglio(spec, true, false, {}, {})
        SezioneFile(ExportSpec(), piano, false, false, false, {}, {}, {})
    }
}

@Preview(name = "Pannello opzioni", widthDp = 411, showBackground = true)
@Composable
fun AnteprimaSezioni() = ImpaginaTheme(scuro = false) { SoloSezioni() }

/**
 * La filigrana aperta: è la sezione con più contenuto — campo di testo,
 * proposte e la nota sull'autentica — quindi è quella che per prima rivela
 * se lo spazio non basta.
 */
@Preview(name = "Filigrana aperta", widthDp = 411, showBackground = true)
@Composable
fun AnteprimaFiligrana() = ImpaginaTheme(scuro = false) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(Spazi.bordo)
    ) {
        SezioneFiligrana(
            filigrana = Watermark("Ad uso iscrizione scolastica {data}", WatermarkStyle.BELOW),
            aperta = true,
            onToggle = {},
            onFiligrana = {}
        )
    }
}
