// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {

    @Test
    fun `solo il PDF non e un formato raster`() {
        assertFalse(OutputFormat.PDF.isRaster)
        OutputFormat.entries.filter { it != OutputFormat.PDF }.forEach {
            assertTrue("${it.label} dovrebbe essere raster", it.isRaster)
        }
    }

    @Test
    fun `ogni formato ha estensione e mime distinti`() {
        val extensions = OutputFormat.entries.map { it.extension }
        val mimes = OutputFormat.entries.map { it.mimeType }
        assertEquals(extensions.size, extensions.distinct().size)
        assertEquals(mimes.size, mimes.distinct().size)
        extensions.forEach { assertFalse("estensione col punto: $it", it.startsWith(".")) }
    }

    @Test
    fun `A4 a 300 dpi da le dimensioni attese`() {
        assertEquals(2479, Raster.pixels(PageLayouts.A4_SHORT_PT, 300))
        assertEquals(3508, Raster.pixels(PageLayouts.A4_LONG_PT, 300))
    }

    @Test
    fun `A4 a 150 dpi da le dimensioni attese`() {
        assertEquals(1240, Raster.pixels(PageLayouts.A4_SHORT_PT, 150))
        assertEquals(1754, Raster.pixels(PageLayouts.A4_LONG_PT, 150))
    }

    @Test
    fun `la densita raddoppiata raddoppia i pixel`() {
        val low = Raster.pixels(PageLayouts.A4_LONG_PT, 150)
        val high = Raster.pixels(PageLayouts.A4_LONG_PT, 300)
        assertEquals((2 * low).toDouble(), high.toDouble(), 1.0)
    }

    @Test
    fun `una misura minuscola non produce mai zero pixel`() {
        assertEquals(1, Raster.pixels(0.1f, 72))
        assertEquals(1, Raster.pixels(0f, 300))
    }

    @Test
    fun `la densita dell'anteprima porta il lato lungo alla misura voluta`() {
        val dpi = Raster.dpiForLongSide(PageLayouts.A4_LONG_PT, 1000)
        val actual = Raster.pixels(PageLayouts.A4_LONG_PT, dpi)
        assertTrue("lato lungo fuori tolleranza: $actual", kotlin.math.abs(actual - 1000) <= 6)
    }

    @Test
    fun `i pesi sono formattati in modo leggibile`() {
        assertEquals("512 B", Sizes.format(512))
        assertEquals("1 kB", Sizes.format(1024))
        assertEquals("2 kB", Sizes.format(1536))
        assertEquals("1,0 MB", Sizes.format(1024 * 1024))
        assertEquals("2,5 MB", Sizes.format((2.5 * 1024 * 1024).toInt()))
    }

    @Test
    fun `il riepilogo raster riporta le dimensioni in pixel`() {
        // 1653×2339 sono le misure reali di un A4 a 200 dpi
        val result = ExportResult(ByteArray(2048), OutputFormat.JPEG, 1653, 2339)
        assertEquals("JPEG 1653×2339, 2 kB", result.summary)
    }

    @Test
    fun `il riepilogo PDF non riporta pixel`() {
        val result = ExportResult(ByteArray(4096), OutputFormat.PDF, 595, 842)
        assertEquals("PDF, 4 kB", result.summary)
    }

    @Test
    fun `le risoluzioni offerte sono crescenti`() {
        val dpis = ExportResolution.entries.map { it.dpi }
        assertEquals(dpis.sorted(), dpis)
        assertTrue(dpis.all { it in 72..600 })
    }

    /* --- Qualità ------------------------------------------------------- */

    /** Curva peso/qualità plausibile: monotona e più ripida verso l'alto. */
    private fun jpegSize(q: Int): Int {
        val t = (q - QUALITY_MIN) / (QUALITY_MAX - QUALITY_MIN).toDouble()
        return (120_000 * (1 + t * t * 9)).toInt()
    }

    @Test
    fun `la qualita ha senso solo per i formati con perdita`() {
        assertTrue(OutputFormat.JPEG.isLossy)
        assertTrue(OutputFormat.WEBP.isLossy)
        assertFalse(OutputFormat.PNG.isLossy)
        assertFalse(OutputFormat.PDF.isLossy)
    }

    @Test
    fun `la griglia di qualita e regolare e crescente`() {
        assertEquals(QUALITY_MIN, QUALITY_STEPS.first())
        assertEquals(QUALITY_MAX, QUALITY_STEPS.last())
        assertEquals(QUALITY_STEPS.sorted(), QUALITY_STEPS)
        QUALITY_STEPS.zipWithNext { a, b -> assertEquals(QUALITY_STEP, b - a) }
        assertTrue(DEFAULT_QUALITY in QUALITY_STEPS)
    }

    @Test
    fun `i tetti di peso proposti sono crescenti`() {
        assertEquals(SIZE_TARGETS.sorted(), SIZE_TARGETS)
    }

    @Test
    fun `sceglie la qualita piu alta che rientra nel tetto`() {
        val target = 500 * 1024
        val fit = QualitySearch.highestUnder(target, sizeAt = ::jpegSize)
        val expected = QUALITY_STEPS.filter { jpegSize(it) <= target }.max()
        assertEquals(expected, fit.quality)
        assertTrue(fit.withinTarget)
        assertTrue(fit.sizeBytes <= target)
    }

    @Test
    fun `con un tetto generoso sceglie la qualita massima`() {
        val fit = QualitySearch.highestUnder(Int.MAX_VALUE, sizeAt = ::jpegSize)
        assertEquals(QUALITY_MAX, fit.quality)
        assertTrue(fit.withinTarget)
    }

    @Test
    fun `con un tetto irraggiungibile ripiega sul minimo senza fallire`() {
        val fit = QualitySearch.highestUnder(1, sizeAt = ::jpegSize)
        assertEquals(QUALITY_MIN, fit.quality)
        assertFalse(fit.withinTarget)
        assertEquals(jpegSize(QUALITY_MIN), fit.sizeBytes)
    }

    @Test
    fun `nessuna qualita viene compressa due volte`() {
        val seen = mutableListOf<Int>()
        QualitySearch.highestUnder(1) { q -> seen += q; jpegSize(q) }
        assertEquals(seen.distinct(), seen)
    }

    @Test
    fun `la ricerca binaria evita di provare tutti i valori`() {
        val seen = mutableListOf<Int>()
        QualitySearch.highestUnder(600 * 1024) { q -> seen += q; jpegSize(q) }
        assertTrue("compressioni: ${seen.size}", seen.size <= 5)
        assertTrue(QUALITY_STEPS.size > 10)
    }

    @Test
    fun `una griglia con un solo valore non manda in errore la ricerca`() {
        val fit = QualitySearch.highestUnder(1, steps = listOf(70)) { jpegSize(it) }
        assertEquals(70, fit.quality)
        assertFalse(fit.withinTarget)
    }

    @Test
    fun `il riepilogo riporta la qualita quando ha senso`() {
        val jpeg = ExportResult(ByteArray(2048), OutputFormat.JPEG, 1653, 2339, 75)
        assertEquals("JPEG 1653×2339 q75, 2 kB", jpeg.summary)

        val png = ExportResult(ByteArray(2048), OutputFormat.PNG, 1653, 2339, null)
        assertEquals("PNG 1653×2339, 2 kB", png.summary)
    }
}

