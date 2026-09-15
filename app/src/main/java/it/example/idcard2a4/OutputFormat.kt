package it.example.idcard2a4

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

/** Qualità di compressione per i formati con perdita. */
const val LOSSY_QUALITY = 88

/* =========================================================================
 *  Risultato e formattazione
 * ========================================================================= */

class ExportResult(
    val bytes: ByteArray,
    val format: OutputFormat,
    val pixelWidth: Int,
    val pixelHeight: Int
) {
    val sizeLabel: String get() = Sizes.format(bytes.size)

    /** Descrizione compatta da mostrare dopo il salvataggio. */
    val summary: String get() = if (format.isRaster) {
        "${format.label} ${pixelWidth}×${pixelHeight}, $sizeLabel"
    } else {
        "${format.label}, $sizeLabel"
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
