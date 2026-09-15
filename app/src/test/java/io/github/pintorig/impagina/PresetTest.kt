// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTest {

    /** Testi che mettono alla prova l'escaping: contengono i separatori. */
    private val nastyTexts = listOf(
        "USO INTERNO",
        "Ad uso pratica; nota=importante",
        "backslash \\ e doppio \\\\ insieme",
        "riga uno\nriga due\r\nriga tre",
        "=;=;=;\\\\;",
        "",
        "   spazi   ",
        "accenti àèìòù e simboli «» — €",
        "emoji 🇮🇹 e cinese 文書",
        "\\n non è un a capo vero"
    )

    private fun fullPreset(watermarkText: String = "USO INTERNO") = Preset(
        name = "Pratica scolastica",
        layout = LayoutSpec(
            documentType = DocumentType.PASSAPORTO,
            sizing = Sizing.FIT,
            arrangement = Arrangement.SIDE_BY_SIDE,
            orientation = PageOrientation.LANDSCAPE,
            slotCount = 4,
            showLabels = true,
            watermark = Watermark(watermarkText, WatermarkStyle.DIAGONAL)
        ),
        filter = ImageFilter.HIGH_CONTRAST,
        export = ExportSpec(OutputFormat.JPEG, ExportResolution.PRINT, 70)
    )

    /* --- escaping ------------------------------------------------------ */

    @Test
    fun `l escaping sopravvive a qualsiasi testo`() {
        nastyTexts.forEach {
            assertEquals(it, PresetCodec.unescape(PresetCodec.escape(it)))
        }
    }

    @Test
    fun `i separatori non compaiono mai nel testo protetto`() {
        val escaped = PresetCodec.escape("a;b=c\nd")
        assertTrue(!escaped.contains(';'))
        assertTrue(!escaped.contains('='))
        assertTrue(!escaped.contains('\n'))
    }

    @Test
    fun `una sequenza di escape sconosciuta non perde caratteri`() {
        assertEquals("\\q", PresetCodec.unescape("\\q"))
    }

    /* --- round-trip ---------------------------------------------------- */

    @Test
    fun `un preset completo sopravvive al round-trip`() {
        val original = fullPreset()
        assertEquals(original, PresetCodec.decode(PresetCodec.encode(original)))
    }

    @Test
    fun `il testo della filigrana sopravvive anche con i separatori dentro`() {
        nastyTexts.forEach { text ->
            val original = fullPreset(text)
            val decoded = PresetCodec.decode(PresetCodec.encode(original))
            assertEquals("fallito su: $text", original, decoded)
        }
    }

    @Test
    fun `un nome con separatori dentro resta intatto`() {
        val original = fullPreset().copy(name = "Pratica; comune=Milano")
        assertEquals(original, PresetCodec.decode(PresetCodec.encode(original)))
    }

    @Test
    fun `piu preset stanno su righe distinte`() {
        val list = listOf(
            fullPreset("riga uno\nriga due").copy(name = "A"),
            fullPreset("punto e virgola ; dentro").copy(name = "B"),
            fullPreset().copy(name = "C")
        )
        val blob = PresetCodec.encodeAll(list)
        assertEquals(3, blob.split("\n").size)
        assertEquals(list, PresetCodec.decodeAll(blob))
    }

    /* --- tolleranza ---------------------------------------------------- */

    @Test
    fun `una riga vuota non produce un preset`() {
        assertNull(PresetCodec.decode(""))
        assertNull(PresetCodec.decode("   "))
    }

    @Test
    fun `senza nome non c'e preset`() {
        assertNull(PresetCodec.decode("t=PATENTE;c=2"))
        assertNull(PresetCodec.decode("n=;t=PATENTE"))
    }

    @Test
    fun `valori di enum non riconosciuti ricadono sul default`() {
        // è lo scenario di un preset salvato da una versione futura
        val decoded = PresetCodec.decode("n=X;t=DOCUMENTO_INVENTATO;z=BOH;xf=TIFF")
        assertNotNull(decoded)
        assertEquals(DocumentType.CARTA_IDENTITA, decoded!!.layout.documentType)
        assertEquals(Sizing.ACTUAL, decoded.layout.sizing)
        assertEquals(OutputFormat.PDF, decoded.export.format)
    }

    @Test
    fun `campi mancanti ricadono sul default`() {
        val decoded = PresetCodec.decode("n=Minimo")
        assertEquals(Preset("Minimo"), decoded)
    }

    @Test
    fun `campi sconosciuti non impediscono la decodifica`() {
        val decoded = PresetCodec.decode("n=X;campoFuturo=42;t=PATENTE")
        assertEquals(DocumentType.PATENTE, decoded!!.layout.documentType)
    }

    @Test
    fun `i valori numerici fuori scala vengono riportati nei limiti`() {
        val tooMany = PresetCodec.decode("n=X;c=999;xq=999")!!
        assertEquals(PageLayouts.MAX_SLOTS, tooMany.layout.slotCount)
        assertEquals(QUALITY_MAX, tooMany.export.quality)

        val tooFew = PresetCodec.decode("n=X;c=0;xq=-5")!!
        assertEquals(1, tooFew.layout.slotCount)
        assertEquals(QUALITY_MIN, tooFew.export.quality)
    }

    @Test
    fun `un numero illeggibile ricade sul default`() {
        val decoded = PresetCodec.decode("n=X;c=due;xq=alta")!!
        assertEquals(2, decoded.layout.slotCount)
        assertEquals(DEFAULT_QUALITY, decoded.export.quality)
    }

    /* --- gestione della lista ------------------------------------------ */

    @Test
    fun `il preset appena salvato finisce in testa`() {
        val list = PresetCodec.upsert(listOf(Preset("A"), Preset("B")), Preset("C"))
        assertEquals(listOf("C", "A", "B"), list.map { it.name })
    }

    @Test
    fun `un nome gia usato sostituisce il preset esistente`() {
        val start = listOf(Preset("Scuola", filter = ImageFilter.NONE), Preset("Banca"))
        val list = PresetCodec.upsert(start, Preset("scuola", filter = ImageFilter.GRAYSCALE))
        assertEquals(2, list.size)
        assertEquals(ImageFilter.GRAYSCALE, list.first().filter)
    }

    @Test
    fun `oltre il massimo si scarta il piu vecchio`() {
        var list = emptyList<Preset>()
        repeat(PresetCodec.MAX_PRESETS + 3) { i ->
            list = PresetCodec.upsert(list, Preset("P$i"))
        }
        assertEquals(PresetCodec.MAX_PRESETS, list.size)
        assertEquals("P${PresetCodec.MAX_PRESETS + 2}", list.first().name)
    }

    @Test
    fun `un nome vuoto non crea un preset`() {
        val start = listOf(Preset("A"))
        assertEquals(start, PresetCodec.upsert(start, Preset("   ")))
    }

    @Test
    fun `il nome viene ripulito dagli spazi ai bordi`() {
        val list = PresetCodec.upsert(emptyList(), Preset("  Scuola  "))
        assertEquals("Scuola", list.single().name)
    }

    @Test
    fun `la rimozione ignora maiuscole e spazi`() {
        val start = listOf(Preset("Scuola"), Preset("Banca"))
        assertEquals(listOf("Banca"), PresetCodec.remove(start, " SCUOLA ").map { it.name })
    }

    @Test
    fun `rimuovere un nome inesistente non cambia nulla`() {
        val start = listOf(Preset("A"))
        assertEquals(start, PresetCodec.remove(start, "Z"))
    }
}
