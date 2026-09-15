// Copyright 2026 pintorig
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import java.util.Locale
import kotlin.math.roundToInt

/* =========================================================================
 *  Formati di uscita
 * ========================================================================= */

enum class OutputFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val hint: String
) {
    PDF(
        "PDF", "pdf", "application/pdf",
        "Formato di pagina: conserva i pixel originali e si stampa in scala esatta."
    ),
    JPEG(
        "JPEG", "jpg", "image/jpeg",
        "Il più accettato dai portali di caricamento. Compressione con perdita."
    ),
    PNG(
        "PNG", "png", "image/png",
        "Senza perdita. Leggerissimo con il filtro Contrasto, pesante a colori."
    ),
    WEBP(
        "WebP", "webp", "image/webp",
        "Circa un terzo più leggero del JPEG, ma non tutti i portali lo accettano."
    );

    /** Il PDF è un contenitore di pagina; gli altri sono immagini rasterizzate. */
    val isRaster: Boolean get() = this != PDF

    /** Solo per questi la qualità ha un significato: PNG e PDF non perdono nulla. */
    val isLossy: Boolean get() = this == JPEG || this == WEBP
}

/**
 * Densità di rasterizzazione. Non si applica al PDF, che non ha una risoluzione
 * propria: incorpora le immagini alla loro dimensione originale.
 */
enum class ExportResolution(val label: String, val dpi: Int, val hint: String) {
    SCREEN("150 dpi", 150, "Per invio via email o caricamento con limiti di peso stretti."),
    STANDARD("200 dpi", 200, "Compromesso abituale: leggibile alla stampa, file contenuto."),
    PRINT("300 dpi", 300, "Qualità di stampa piena. File sensibilmente più pesante.")
}

/* --- Qualità di compressione, per i soli formati con perdita --- */

const val QUALITY_MIN = 40
const val QUALITY_MAX = 100
const val QUALITY_STEP = 5

/** Valori selezionabili. Una griglia discreta rende la ricerca automatica
 *  deterministica e i test riproducibili. */
val QUALITY_STEPS: List<Int> = (QUALITY_MIN..QUALITY_MAX step QUALITY_STEP).toList()

/** Sotto questa soglia gli artefatti iniziano a intaccare i caratteri piccoli. */
const val DEFAULT_QUALITY = 85

/** Tetti di peso proposti, calibrati sui limiti abituali dei portali. */
val SIZE_TARGETS: List<Int> = listOf(500 * 1024, 1024 * 1024, 2 * 1024 * 1024, 5 * 1024 * 1024)

/* =========================================================================
 *  Risultato e formattazione
 * ========================================================================= */

class ExportResult(
    val bytes: ByteArray,
    val format: OutputFormat,
    val pixelWidth: Int,
    val pixelHeight: Int,
    /** Qualità usata, `null` per i formati che non la prevedono. */
    val quality: Int? = null,
    /** Pagine contenute: sempre 1 per i formati immagine. */
    val pageCount: Int = 1
) {
    val sizeLabel: String get() = Sizes.format(bytes.size)

    /** Descrizione compatta da mostrare dopo il salvataggio. */
    val summary: String get() = buildString {
        append(format.label)
        if (pageCount > 1) append(" $pageCount pagine")
        if (format.isRaster) append(" ${pixelWidth}×${pixelHeight}")
        if (quality != null) append(" q$quality")
        append(", $sizeLabel")
    }
}

object Sizes {
    private const val KB = 1024f
    private const val MB = KB * KB

    fun format(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ITALY, "%.0f kB", bytes / KB)
        else -> String.format(Locale.ITALY, "%.1f MB", bytes / MB)
    }
}

/** Conversione fra punti PostScript e pixel. Pura, quindi verificabile su JVM. */
object Raster {

    /** Pixel corrispondenti a una misura in punti, alla densità data. */
    fun pixels(points: Float, dpi: Int): Int =
        (points * dpi / 72f).roundToInt().coerceAtLeast(1)

    /** Densità che porta il lato lungo della pagina al numero di pixel voluto. */
    fun dpiForLongSide(longSidePt: Float, targetPx: Int): Int =
        (targetPx * 72f / longSidePt).roundToInt().coerceAtLeast(1)
}

/* =========================================================================
 *  Ricerca della qualità sotto un tetto di peso
 * ========================================================================= */

/** Esito della ricerca: la qualità scelta, il peso ottenuto, e se il tetto è stato rispettato. */
data class QualityFit(val quality: Int, val sizeBytes: Int, val withinTarget: Boolean)

object QualitySearch {

    /**
     * Qualità più alta che sta sotto `targetBytes`.
     *
     * Il peso di un JPEG cresce in modo monotono con la qualità, quindi basta
     * una ricerca binaria sulla griglia: quattro compressioni invece di tredici.
     * Se nemmeno il valore minimo rientra, restituisce comunque il minimo con
     * `withinTarget = false` — meglio un file un po' troppo pesante che nessun
     * file e un messaggio di errore.
     */
    fun highestUnder(
        targetBytes: Int,
        steps: List<Int> = QUALITY_STEPS,
        sizeAt: (Int) -> Int
    ): QualityFit {
        require(steps.isNotEmpty()) { "Serve almeno un valore di qualità" }
        val ordered = steps.sorted()

        // Il ripiego rimisurerebbe un valore già provato: una memoizzazione
        // minima lo evita, e in un test rende verificabile che ogni qualità
        // venga compressa una volta sola.
        val measured = HashMap<Int, Int>()
        val measure: (Int) -> Int = { q -> measured.getOrPut(q) { sizeAt(q) } }

        var low = 0
        var high = ordered.lastIndex
        var bestIndex = -1
        var bestSize = -1

        while (low <= high) {
            val mid = (low + high) / 2
            val size = measure(ordered[mid])
            if (size <= targetBytes) {
                bestIndex = mid
                bestSize = size
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (bestIndex >= 0) return QualityFit(ordered[bestIndex], bestSize, true)

        val fallback = ordered.first()
        return QualityFit(fallback, measure(fallback), false)
    }
}
