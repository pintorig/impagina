package it.example.idcard2a4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ToneMapping` non tocca API Android, quindi la curva si verifica su JVM
 * costruendo istogrammi sintetici che imitano scansioni reali.
 */
class ToneMappingTest {

    /** Istogramma da bande uniformi: (centro, semiampiezza, pixel totali). */
    private fun histogram(vararg bands: Triple<Int, Int, Int>): IntArray {
        val h = IntArray(256)
        bands.forEach { (centre, spread, count) ->
            val bins = 2 * spread + 1
            for (d in -spread..spread) {
                h[(centre + d).coerceIn(0, 255)] += count / bins
            }
        }
        return h
    }

    /** Scansione tipica: carta chiara dominante, testo scuro, foto a toni medi. */
    private fun wellLitCard() = histogram(
        Triple(205, 18, 620_000),
        Triple(60, 25, 120_000),
        Triple(130, 40, 260_000)
    )

    @Test
    fun `lo sfondo carta diventa bianco pieno`() {
        val lut = ToneMapping.buildLut(
            ImageFilter.HIGH_CONTRAST,
            ToneMapping.statsFrom(wellLitCard())
        )
        assertEquals(255, lut[205])
    }

    @Test
    fun `il testo resta ben scuro`() {
        val lut = ToneMapping.buildLut(
            ImageFilter.HIGH_CONTRAST,
            ToneMapping.statsFrom(wellLitCard())
        )
        assertTrue("testo troppo chiaro: ${lut[60]}", lut[60] < 40)
    }

    @Test
    fun `i toni intermedi non collassano come in una binarizzazione`() {
        val lut = ToneMapping.buildLut(
            ImageFilter.HIGH_CONTRAST,
            ToneMapping.statsFrom(wellLitCard())
        )
        // fra testo e carta devono restare livelli distinti: è ciò che salva
        // la fotografia del volto e i microtesti di sicurezza
        val distinct = (60..205).map { lut[it] }.distinct().size
        assertTrue("solo $distinct livelli sopravvissuti", distinct > 60)
    }

    @Test
    fun `la curva e sempre monotona`() {
        val lut = ToneMapping.buildLut(
            ImageFilter.HIGH_CONTRAST,
            ToneMapping.statsFrom(wellLitCard())
        )
        for (i in 1..255) {
            assertTrue("inversione di tono a $i", lut[i] >= lut[i - 1])
        }
    }

    @Test
    fun `la curva resta nei limiti 0-255`() {
        val lut = ToneMapping.buildLut(
            ImageFilter.HIGH_CONTRAST,
            ToneMapping.statsFrom(wellLitCard())
        )
        assertTrue(lut.min() >= 0 && lut.max() <= 255)
    }

    @Test
    fun `uno scatto sottoesposto viene comunque riportato a bianco`() {
        val dim = histogram(
            Triple(140, 20, 620_000),
            Triple(35, 20, 120_000),
            Triple(85, 30, 260_000)
        )
        val lut = ToneMapping.buildLut(ImageFilter.HIGH_CONTRAST, ToneMapping.statsFrom(dim))
        assertEquals(255, lut[140])
    }

    @Test
    fun `un immagine piatta non viene toccata`() {
        val flat = histogram(Triple(250, 3, 1_000_000))
        assertEquals(LumaStats.IDENTITY, ToneMapping.statsFrom(flat))
    }

    @Test
    fun `l intervallo non scende mai sotto la soglia minima`() {
        val stats = ToneMapping.statsFrom(wellLitCard())
        assertTrue(stats.whitePoint - stats.blackPoint >= ToneMapping.MIN_SPAN)
    }

    @Test
    fun `istogramma vuoto restituisce la curva identita`() {
        assertEquals(LumaStats.IDENTITY, ToneMapping.statsFrom(IntArray(256)))
    }

    @Test
    fun `la scala di grigi non altera la luminanza`() {
        val lut = ToneMapping.buildLut(ImageFilter.GRAYSCALE, LumaStats.IDENTITY)
        for (i in 0..255) assertEquals(i, lut[i])
    }

    @Test
    fun `la luminanza segue i pesi BT 601`() {
        assertEquals(0, ToneMapping.luma(0, 0, 0))
        assertEquals(255, ToneMapping.luma(255, 255, 255))
        // il verde pesa piu del rosso, che pesa piu del blu
        assertTrue(
            ToneMapping.luma(0, 255, 0) > ToneMapping.luma(255, 0, 0)
        )
        assertTrue(
            ToneMapping.luma(255, 0, 0) > ToneMapping.luma(0, 0, 255)
        )
    }
}
