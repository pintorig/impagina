// Copyright 2026 pintorig
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import android.graphics.Bitmap
import kotlin.math.min
import kotlin.math.roundToInt

/* =========================================================================
 *  Filtri disponibili
 * ========================================================================= */

enum class ImageFilter(val label: String, val hint: String) {
    NONE(
        "Colore",
        "Nessuna alterazione. Da preferire quando l'ente chiede la copia a colori."
    ),
    GRAYSCALE(
        "Grigi",
        "Conversione in scala di grigi, senza toccare luminosità e contrasto."
    ),
    HIGH_CONTRAST(
        "Contrasto",
        "Sfondo appiattito a bianco e testo scurito. Resa da fotocopia, file più leggero."
    )
}

/* =========================================================================
 *  Parte pura: analisi dei toni e tabella di conversione
 *  Nessuna chiamata ad API Android, quindi testabile su JVM.
 * ========================================================================= */

/** Punti di nero e di bianco individuati nell'immagine, in scala 0..255. */
data class LumaStats(val blackPoint: Int, val whitePoint: Int) {
    companion object {
        /** Nessuna correzione: l'intervallo è già tutto. */
        val IDENTITY = LumaStats(0, 255)
    }
}

object ToneMapping {

    /** Ampiezza minima dell'intervallo, per non amplificare il rumore. */
    const val MIN_SPAN = 48

    /** Sotto questa dinamica l'immagine è considerata piatta e si lascia stare. */
    const val FLAT_IMAGE_SPAN = 16

    /** Guadagno di contrasto applicato dopo l'allineamento dei livelli. */
    const val CONTRAST_GAIN = 1.15f

    /** Frazione del picco sotto cui si considera finito il modo "carta". */
    const val PAPER_SHOULDER = 0.25f

    /** Massa minima del modo "carta" perché sia credibile come sfondo. */
    const val MIN_PAPER_SHARE = 0.15f

    /** Valore di luminanza sotto cui cade la frazione `p` dei pixel. */
    fun percentile(histogram: IntArray, p: Float): Int {
        val total = histogram.sum().toLong()
        if (total == 0L) return 0
        val target = (total * p).toLong().coerceAtLeast(1L)
        var acc = 0L
        for (i in histogram.indices) {
            acc += histogram[i]
            if (acc >= target) return i
        }
        return histogram.lastIndex
    }

    /**
     * Punto di bianco agganciato al picco della carta.
     *
     * Un percentile fisso non basta: su una scansione lo sfondo è un modo largo
     * e basso, e prendere il 90° percentile cade *dentro* quel modo, lasciando
     * la carta a un grigio chiaro invece che a bianco pieno. Qui si individua il
     * picco nella metà alta dell'istogramma e si scende fino alla sua spalla,
     * così l'intero sfondo viene portato a 255.
     *
     * Il modo va pesato per massa e non per altezza del singolo bin, altrimenti
     * una distribuzione larga viene scartata proprio quando è più chiaramente
     * la carta.
     */
    fun paperWhitePoint(histogram: IntArray, fallback: Int): Int {
        val total = histogram.sum()
        if (total == 0) return fallback

        var peak = 128
        for (i in 128..255) if (histogram[i] > histogram[peak]) peak = i

        val threshold = histogram[peak] * PAPER_SHOULDER
        var shoulder = peak
        while (shoulder > 0 && histogram[shoulder] >= threshold) shoulder--

        val mass = (shoulder..255).sumOf { histogram[it].toLong() }
        if (mass < total * MIN_PAPER_SHARE) return fallback
        return shoulder
    }

    /**
     * Auto-livelli tarati sui documenti, non sulle fotografie.
     *
     * Il punto di nero sta al 2° percentile: abbastanza in basso da non ingoiare
     * i grigi scuri della foto del volto, che è esattamente ciò che rovina una
     * binarizzazione a soglia.
     */
    fun statsFrom(histogram: IntArray): LumaStats {
        if (histogram.sum() == 0) return LumaStats.IDENTITY

        // Immagine quasi uniforme (foglio vuoto, inquadratura sbagliata):
        // qualunque correzione amplificherebbe solo il rumore del sensore.
        val dynamic = percentile(histogram, 0.99f) - percentile(histogram, 0.01f)
        if (dynamic < FLAT_IMAGE_SPAN) return LumaStats.IDENTITY

        var black = percentile(histogram, 0.02f)
        var white = paperWhitePoint(histogram, percentile(histogram, 0.90f))

        if (white - black < MIN_SPAN) {
            white = (black + MIN_SPAN).coerceAtMost(255)
            black = (white - MIN_SPAN).coerceAtLeast(0)
        }
        return LumaStats(black, white)
    }

    /**
     * Tabella di 256 valori luminanza → grigio finale.
     * Una LUT evita di rifare il conto per ognuno dei milioni di pixel.
     */
    fun buildLut(filter: ImageFilter, stats: LumaStats): IntArray {
        val lut = IntArray(256)
        when (filter) {
            ImageFilter.NONE, ImageFilter.GRAYSCALE ->
                for (i in 0..255) lut[i] = i

            ImageFilter.HIGH_CONTRAST -> {
                val span = (stats.whitePoint - stats.blackPoint).coerceAtLeast(1)
                for (i in 0..255) {
                    val levelled = (i - stats.blackPoint) * 255f / span
                    val boosted = 127.5f + (levelled - 127.5f) * CONTRAST_GAIN
                    lut[i] = boosted.roundToInt().coerceIn(0, 255)
                }
            }
        }
        return lut
    }

    /** Luminanza BT.601, in interi per evitare il floating point per pixel. */
    fun luma(r: Int, g: Int, b: Int): Int = (r * 299 + g * 587 + b * 114) / 1000
}

/* =========================================================================
 *  Parte Android: applicazione del filtro al bitmap
 * ========================================================================= */

object ImageFilters {

    /** Pixel campionati per costruire l'istogramma: più che sufficienti. */
    private const val HISTOGRAM_SAMPLES = 200_000

    /** Pixel per banda di elaborazione, per non allocare l'intera immagine. */
    private const val BAND_PIXELS = 1_000_000

    /**
     * Restituisce una nuova immagine filtrata, o l'originale se il filtro è
     * `NONE`. Operazione pesante: va chiamata fuori dal main thread.
     *
     * Il filtro viene applicato al bitmap e non al `Paint` in fase di disegno,
     * per due motivi: l'anteprima mostra esattamente ciò che verrà salvato, e
     * un'immagine con lo sfondo appiattito si comprime molto meglio dentro il
     * PDF di una con lo sfondo screziato.
     */
    fun apply(src: Bitmap, filter: ImageFilter): Bitmap {
        if (filter == ImageFilter.NONE) return src
        val stats = if (filter == ImageFilter.HIGH_CONTRAST) {
            ToneMapping.statsFrom(histogramOf(src))
        } else {
            LumaStats.IDENTITY
        }
        return mapThroughLut(src, ToneMapping.buildLut(filter, stats))
    }

    /** Istogramma della luminanza, su un sottoinsieme regolare dei pixel. */
    private fun histogramOf(src: Bitmap): IntArray {
        val histogram = IntArray(256)
        val w = src.width
        val h = src.height
        val step = maxOf(1, Math.round(Math.sqrt((w.toDouble() * h) / HISTOGRAM_SAMPLES)).toInt())

        val row = IntArray(w)
        var y = 0
        while (y < h) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val p = row[x]
                histogram[ToneMapping.luma((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)]++
                x += step
            }
            y += step
        }
        return histogram
    }

    /** Converte in grigio passando ogni pixel per la LUT, una banda alla volta. */
    private fun mapThroughLut(src: Bitmap, lut: IntArray): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val bandHeight = (BAND_PIXELS / w).coerceIn(1, h)
        val buffer = IntArray(w * bandHeight)

        var y = 0
        while (y < h) {
            val rows = min(bandHeight, h - y)
            src.getPixels(buffer, 0, w, 0, y, w, rows)
            for (i in 0 until w * rows) {
                val p = buffer[i]
                val v = lut[ToneMapping.luma((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)]
                // alfa forzata a opaco: lo sfondo del foglio deve restare bianco
                buffer[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            out.setPixels(buffer, 0, w, 0, y, w, rows)
            y += rows
        }
        return out
    }
}
