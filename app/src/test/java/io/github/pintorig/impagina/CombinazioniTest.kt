// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I template che mettono più documenti sullo stesso foglio.
 *
 * Il primo test è il più importante: non verifica il codice ma la decisione
 * che lo rende possibile.
 */
class CombinazioniTest {

    @Test
    fun `ogni combinazione usa solo documenti dello stesso formato fisico`() {
        // Il motore calcola UNA dimensione di cella per l'intero piano. Mescolare
        // un ID-3 con degli ID-1 chiederebbe celle diverse sulla stessa pagina:
        // finché quell'invariante regge, le combinazioni restano omogenee.
        Combinazione.entries.forEach { combo ->
            val formati = combo.documenti.map { it.physicalSize }.distinct()
            assertEquals(
                "${combo.name} mescola formati diversi: $formati",
                1,
                formati.size
            )
            assertEquals("${combo.name} non è ID-1", Formats.ID1, formati.single())
        }
    }

    @Test
    fun `nessuna combinazione include il passaporto`() {
        Combinazione.entries.forEach { combo ->
            assertTrue(
                "${combo.name} include un ID-3",
                DocumentType.PASSAPORTO !in combo.documenti
            )
        }
    }

    @Test
    fun `ogni documento porta due facciate`() {
        Combinazione.entries.forEach { combo ->
            assertEquals(combo.documenti.size * 2, combo.slotCount)
            assertEquals(combo.slotCount, combo.labels.size)
        }
    }

    @Test
    fun `identita piu tessera sanitaria fa quattro facciate in ordine`() {
        val combo = Combinazione.IDENTITA_SANITARIA
        assertEquals(4, combo.slotCount)
        assertEquals(
            listOf(
                "Identità · fronte",
                "Identità · retro",
                "Sanitaria · fronte",
                "Sanitaria · retro"
            ),
            combo.labels
        )
    }

    @Test
    fun `tre documenti fanno sei facciate`() {
        assertEquals(6, Combinazione.IDENTITA_SANITARIA_PATENTE.slotCount)
    }

    @Test
    fun `le etichette dicono a quale documento appartiene la facciata`() {
        // Senza il nome davanti, sul foglio quattro «Fronte» identici non
        // direbbero quale documento va dove.
        Combinazione.entries.forEach { combo ->
            combo.documenti.forEach { doc ->
                assertTrue(
                    "${combo.name} non nomina ${doc.shortLabel}",
                    combo.labels.any { it.startsWith(doc.shortLabel) }
                )
            }
        }
    }

    @Test
    fun `il piano segue le etichette della combinazione`() {
        val combo = Combinazione.IDENTITA_PATENTE
        val plan = PageLayouts.computePlan(
            LayoutSpec(combinazione = combo, slotCount = combo.slotCount)
        )
        val etichette = plan.pages.flatMap { it.labels }
        assertEquals(combo.labels, etichette)
    }

    @Test
    fun `senza combinazione le etichette restano quelle del tipo singolo`() {
        val spec = LayoutSpec(documentType = DocumentType.TESSERA_SANITARIA, slotCount = 2)
        assertEquals(
            DocumentType.TESSERA_SANITARIA.slotLabels,
            PageLayouts.labelsFor(spec, 2)
        )
    }

    @Test
    fun `piu slot delle etichette previste non lascia buchi`() {
        val combo = Combinazione.IDENTITA_SANITARIA
        val etichette = PageLayouts.labelsFor(
            LayoutSpec(combinazione = combo),
            combo.slotCount + 2
        )
        assertEquals(combo.slotCount + 2, etichette.size)
        assertTrue(etichette.none { it.isBlank() })
    }

    @Test
    fun `quattro facciate ID-1 a dimensione reale stanno in un foglio solo`() {
        // È la ragione pratica per cui queste combinazioni hanno senso:
        // altrimenti servirebbero due fogli e tanto varrebbe fare due documenti.
        val plan = PageLayouts.computePlan(
            LayoutSpec(
                combinazione = Combinazione.IDENTITA_SANITARIA,
                slotCount = 4
            )
        )
        assertEquals(1, plan.pageCount)
        assertTrue("è stata ridotta: non entra a dimensione reale", !plan.isScaledDown)
    }
}

/**
 * La combinazione dentro i preset. Deve sopravvivere al giro, e la decodifica
 * deve restare tollerante come tutto il resto del codec: un preset salvato
 * prima che i template esistessero va letto lo stesso.
 */
class CombinazioniNeiPresetTest {

    private fun giro(preset: Preset): Preset =
        PresetCodec.decode(PresetCodec.encode(preset))!!

    @Test
    fun `la combinazione sopravvive a codifica e decodifica`() {
        val preset = Preset(
            name = "Iscrizione scolastica",
            layout = LayoutSpec(
                combinazione = Combinazione.IDENTITA_SANITARIA,
                slotCount = 4
            ),
            filter = ImageFilter.NONE,
            export = ExportSpec()
        )
        assertEquals(Combinazione.IDENTITA_SANITARIA, giro(preset).layout.combinazione)
    }

    @Test
    fun `senza combinazione il campo resta nullo`() {
        val preset = Preset("Semplice", LayoutSpec(), ImageFilter.NONE, ExportSpec())
        assertEquals(null, giro(preset).layout.combinazione)
    }

    @Test
    fun `un preset senza il campo si legge lo stesso`() {
        // Il caso dei preset gia' su disco: si simula togliendo il campo dalla
        // riga codificata, invece di scriverne una a mano con i separatori.
        val preset = Preset("Vecchio", LayoutSpec(), ImageFilter.NONE, ExportSpec())
        val senzaCampo = PresetCodec.encode(preset)
            .split(';')
            .filterNot { it.startsWith("tm=") }
            .joinToString(";")
        val letto = PresetCodec.decode(senzaCampo)
        assertEquals("Vecchio", letto?.name)
        assertEquals(null, letto?.layout?.combinazione)
    }

    @Test
    fun `una combinazione sconosciuta ricade sul nessun template`() {
        val preset = Preset(
            "Strano",
            LayoutSpec(combinazione = Combinazione.IDENTITA_PATENTE),
            ImageFilter.NONE,
            ExportSpec()
        )
        val dalFuturo = PresetCodec.encode(preset)
            .replace("tm=IDENTITA_PATENTE", "tm=COMBINAZIONE_DAL_FUTURO")
        assertEquals(null, PresetCodec.decode(dalFuturo)?.layout?.combinazione)
    }
}

/**
 * La disposizione dei template misti: fronte e retro dello stesso documento
 * devono finire sulla stessa riga, e il documento dopo sulla riga dopo.
 */
class DisposizioneTemplateTest {

    private fun piano(combo: Combinazione, arrangement: GridArrangement) =
        PageLayouts.computePlan(
            LayoutSpec(
                combinazione = combo,
                slotCount = combo.slotCount,
                arrangement = arrangement
            )
        )

    @Test
    fun `affiancate mette fronte e retro dello stesso documento sulla stessa riga`() {
        // Le etichette della combinazione sono ordinate fronte, retro, fronte,
        // retro: con due colonne il riempimento per righe le accoppia da solo.
        val combo = Combinazione.IDENTITA_SANITARIA_PATENTE
        val plan = piano(combo, GridArrangement.SIDE_BY_SIDE)
        assertEquals(2, plan.columns)

        val etichette = plan.pages.flatMap { it.labels }
        etichette.chunked(2).forEachIndexed { riga, coppia ->
            val documento = combo.documenti[riga].shortLabel
            assertTrue(
                "riga $riga: attese due facciate di $documento, trovate $coppia",
                coppia.all { it.startsWith(documento) }
            )
            assertTrue("riga $riga non ha fronte e retro", coppia.any { it.endsWith("fronte") })
            assertTrue("riga $riga non ha fronte e retro", coppia.any { it.endsWith("retro") })
        }
    }

    @Test
    fun `tre documenti affiancati stanno in un foglio solo`() {
        val plan = piano(Combinazione.IDENTITA_SANITARIA_PATENTE, GridArrangement.SIDE_BY_SIDE)
        assertEquals(1, plan.pageCount)
        assertTrue("sono stati rimpiccioliti", !plan.isScaledDown)
    }

    @Test
    fun `in colonna gli stessi tre documenti occupano due fogli`() {
        val plan = piano(Combinazione.IDENTITA_SANITARIA_PATENTE, GridArrangement.STACKED)
        assertEquals(2, plan.pageCount)
    }

    @Test
    fun `la disposizione che salva una pagina viene suggerita`() {
        val spec = LayoutSpec(
            combinazione = Combinazione.IDENTITA_SANITARIA_PATENTE,
            slotCount = 6,
            arrangement = GridArrangement.STACKED
        )
        assertEquals(GridArrangement.SIDE_BY_SIDE, PageLayouts.arrangementThatFitsOnePage(spec))
    }

    @Test
    fun `quando basta gia' una pagina non si suggerisce niente`() {
        val spec = LayoutSpec(
            combinazione = Combinazione.IDENTITA_SANITARIA,
            slotCount = 4,
            arrangement = GridArrangement.STACKED
        )
        assertEquals(null, PageLayouts.arrangementThatFitsOnePage(spec))
    }
}
