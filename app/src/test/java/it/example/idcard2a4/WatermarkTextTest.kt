package it.example.idcard2a4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `WatermarkText` riceve la misurazione come funzione, quindi si verifica su JVM
 * con un misuratore finto: qui ogni carattere vale 5 punti a corpo 10.
 */
class WatermarkTextTest {

    private val maxWidth = 523f            // A4 verticale meno i margini
    private val measure: (String) -> Float = { it.length * 5f }

    @Test
    fun `il segnaposto data viene sostituito`() {
        assertEquals(
            "Ad uso 15/09/2026",
            WatermarkText.expand("Ad uso ${WatermarkText.DATE_TOKEN}", "15/09/2026")
        )
    }

    @Test
    fun `il segnaposto e riconosciuto anche maiuscolo`() {
        assertEquals("Ad uso 01/01/2027", WatermarkText.expand("Ad uso {DATA}", "01/01/2027"))
    }

    @Test
    fun `un testo senza segnaposto resta intatto`() {
        assertEquals("USO INTERNO", WatermarkText.expand("USO INTERNO", "15/09/2026"))
    }

    @Test
    fun `un testo vuoto non produce righe`() {
        assertTrue(WatermarkText.wrap("   ", maxWidth, measure).isEmpty())
    }

    @Test
    fun `un testo breve resta su una riga`() {
        val lines = WatermarkText.wrap("USO INTERNO", maxWidth, measure)
        assertEquals(listOf("USO INTERNO"), lines)
    }

    @Test
    fun `un testo lungo va a capo senza superare la larghezza`() {
        val text = "Copia rilasciata ad uso iscrizione scolastica per l anno 2026/2027 " +
            "presso il Comune di Milano - Municipio 4 - protocollo interno del 15/09/2026"
        val lines = WatermarkText.wrap(text, maxWidth, measure)
        assertEquals(2, lines.size)
        lines.forEach { assertTrue("riga troppo larga: $it", measure(it) <= maxWidth) }
    }

    @Test
    fun `nessuna parola viene persa andando a capo`() {
        val text = "Copia rilasciata ad uso iscrizione scolastica per l anno 2026/2027 " +
            "presso il Comune di Milano Municipio 4 protocollo interno numero 998877 " +
            "emesso in data 15/09/2026 dal responsabile del procedimento amministrativo"
        val lines = WatermarkText.wrap(text, maxWidth, measure)
        assertEquals(text.split(" "), lines.joinToString(" ").split(" "))
    }

    @Test
    fun `l eccedenza confluisce nell'ultima riga invece di essere troncata`() {
        val text = (1..40).joinToString(" ") { "parola$it" }
        val lines = WatermarkText.wrap(text, maxWidth, measure, maxLines = 2)
        assertEquals(2, lines.size)
        // l'ultima riga sfora: sarà il corpo a rimpicciolirsi, ma il testo c'è tutto
        assertTrue(measure(lines.last()) > maxWidth)
        assertEquals(text.split(" "), lines.joinToString(" ").split(" "))
    }

    @Test
    fun `una parola singola piu larga della riga non blocca il wrap`() {
        val word = "a".repeat(200)
        val lines = WatermarkText.wrap(word, maxWidth, measure)
        assertEquals(listOf(word), lines)
    }

    @Test
    fun `il corpo scala linearmente con la larghezza obiettivo`() {
        // larghezza 20 a corpo 1 -> per arrivare a 200 serve corpo 10
        assertEquals(10f, WatermarkText.fittingTextSize(200f, 20f, 1f, 100f), 0.001f)
    }

    @Test
    fun `il corpo resta dentro i limiti richiesti`() {
        assertEquals(6f, WatermarkText.fittingTextSize(10f, 20f, 6f, 10f), 0.001f)
        assertEquals(10f, WatermarkText.fittingTextSize(9999f, 20f, 6f, 10f), 0.001f)
    }

    @Test
    fun `una larghezza unitaria nulla non provoca divisione per zero`() {
        assertEquals(10f, WatermarkText.fittingTextSize(100f, 0f, 6f, 10f), 0.001f)
    }

    @Test
    fun `la filigrana e attiva solo con stile e testo`() {
        assertFalse(Watermark("USO INTERNO", WatermarkStyle.NONE).isActive)
        assertFalse(Watermark("   ", WatermarkStyle.BELOW).isActive)
        assertTrue(Watermark("USO INTERNO", WatermarkStyle.BELOW).isActive)
    }

    @Test
    fun `nessun preset dichiara una conformita all'originale`() {
        // "copia conforme all'originale" è un'autentica ex art. 18 DPR 445/2000:
        // proporla come formula pronta indurrebbe in errore
        Watermark.PRESETS.forEach {
            assertFalse(
                "preset fuorviante: $it",
                it.lowercase().contains("conforme")
            )
        }
    }

    @Test
    fun `solo la filigrana in fondo riserva spazio sul foglio`() {
        assertEquals(
            0f,
            PageLayouts.reservedBottomFor(Watermark("X", WatermarkStyle.DIAGONAL)),
            0.001f
        )
        assertEquals(
            0f,
            PageLayouts.reservedBottomFor(Watermark("", WatermarkStyle.BELOW)),
            0.001f
        )
        assertEquals(
            PageLayouts.WATERMARK_BAND_PT,
            PageLayouts.reservedBottomFor(Watermark("X", WatermarkStyle.BELOW)),
            0.001f
        )
    }
}
