package it.example.idcard2a4

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
}
