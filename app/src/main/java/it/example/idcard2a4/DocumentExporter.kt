package it.example.idcard2a4

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Produce il file finale nel formato richiesto.
 *
 * PDF e immagini passano dallo stesso [PageRenderer], quindi non possono
 * divergere: il JPEG è la rasterizzazione esatta della stessa pagina che
 * finirebbe nel PDF.
 */
object DocumentExporter {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY)

    /** Lato lungo dell'anteprima, in pixel: abbastanza per giudicare, non di più. */
    const val PREVIEW_LONG_SIDE = 1000

    fun today(): String = LocalDate.now().format(DATE_FORMAT)

    fun export(
        images: List<Bitmap?>,
        spec: LayoutSpec,
        format: OutputFormat,
        resolution: ExportResolution,
        today: String = today()
    ): ExportResult {
        require(images.any { it != null }) { "Serve almeno una facciata" }
        val layout = PageLayouts.compute(spec.copy(slotCount = images.size))

        return if (format == OutputFormat.PDF) {
            val out = ByteArrayOutputStream()
            writePdf(out, images, layout, spec.watermark, today)
            ExportResult(
                bytes = out.toByteArray(),
                format = format,
                pixelWidth = layout.pageWidthPt.roundToInt(),
                pixelHeight = layout.pageHeightPt.roundToInt()
            )
        } else {
            val bitmap = rasterize(images, layout, spec.watermark, today, resolution.dpi)
            try {
                val out = ByteArrayOutputStream()
                bitmap.compress(compressFormatFor(format), LOSSY_QUALITY, out)
                ExportResult(out.toByteArray(), format, bitmap.width, bitmap.height)
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Anteprima a bassa densità: stesso disegno, solo più piccolo. */
    fun renderPreview(
        images: List<Bitmap?>,
        spec: LayoutSpec,
        today: String = today()
    ): Bitmap? {
        if (images.none { it != null }) return null
        val layout = PageLayouts.compute(spec.copy(slotCount = images.size))
        val longSidePt = maxOf(layout.pageWidthPt, layout.pageHeightPt)
        val dpi = Raster.dpiForLongSide(longSidePt, PREVIEW_LONG_SIDE)
        return rasterize(images, layout, spec.watermark, today, dpi)
    }

    /** Scrive direttamente su uno stream, senza passare per la memoria. */
    fun writePdf(
        out: OutputStream,
        images: List<Bitmap?>,
        layout: PageLayout,
        watermark: Watermark,
        today: String
    ) {
        val doc = PdfDocument()
        try {
            val info = PdfDocument.PageInfo.Builder(
                layout.pageWidthPt.roundToInt(),
                layout.pageHeightPt.roundToInt(),
                1
            ).create()
            val page = doc.startPage(info)
            PageRenderer.drawPage(page.canvas, images, layout, watermark, today)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /**
     * Rasterizza la pagina alla densità richiesta.
     *
     * Il canvas viene scalato di `dpi / 72` e il disegno resta espresso in punti:
     * è ciò che permette di riusare tale e quale il codice del PDF, invece di
     * mantenere una seconda implementazione destinata a divergere.
     */
    private fun rasterize(
        images: List<Bitmap?>,
        layout: PageLayout,
        watermark: Watermark,
        today: String,
        dpi: Int
    ): Bitmap {
        val scale = dpi / 72f
        val w = Raster.pixels(layout.pageWidthPt, dpi)
        val h = Raster.pixels(layout.pageHeightPt, dpi)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        PageRenderer.drawPage(canvas, images, layout, watermark, today)
        return bmp
    }

    private fun compressFormatFor(format: OutputFormat): Bitmap.CompressFormat =
        when (format) {
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.WEBP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            OutputFormat.PDF -> error("Il PDF non passa dalla compressione bitmap")
        }
}
