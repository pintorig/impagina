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
        quality: Int = DEFAULT_QUALITY,
        today: String = today()
    ): ExportResult {
        require(images.any { it != null }) { "Serve almeno una facciata" }
        val plan = PageLayouts.computePlan(spec.copy(slotCount = images.size))

        return if (format == OutputFormat.PDF) {
            val out = ByteArrayOutputStream()
            writePdf(out, images, plan, spec.watermark, today)
            ExportResult(
                bytes = out.toByteArray(),
                format = format,
                pixelWidth = plan.first.pageWidthPt.roundToInt(),
                pixelHeight = plan.first.pageHeightPt.roundToInt(),
                quality = null,
                pageCount = plan.pageCount
            )
        } else {
            // Un JPEG non ha pagine: se il piano ne prevede più di una, il file
            // ne perderebbe silenziosamente una parte. Meglio fermarsi.
            require(!plan.isMultiPage) {
                "${format.label} non può contenere ${plan.pageCount} pagine: usa il PDF"
            }
            val bitmap = rasterize(images, plan.first, spec.watermark, today, resolution.dpi)
            try {
                val out = ByteArrayOutputStream()
                bitmap.compress(compressFormatFor(format), quality, out)
                // il PNG ignora il parametro: riportarlo nel riepilogo sarebbe fuorviante
                ExportResult(
                    out.toByteArray(), format, bitmap.width, bitmap.height,
                    quality.takeIf { format.isLossy }, pageCount = 1
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Cerca la qualità più alta che stia sotto il tetto di peso indicato.
     *
     * La pagina si rasterizza una volta sola e poi si ricomprime a qualità
     * diverse: il disegno è la parte cara, la compressione no. Insieme alla
     * ricerca binaria significa un rendering e quattro compressioni, invece di
     * tredici rendering completi.
     */
    fun fitQuality(
        images: List<Bitmap?>,
        spec: LayoutSpec,
        format: OutputFormat,
        resolution: ExportResolution,
        targetBytes: Int,
        today: String = today()
    ): QualityFit {
        require(format.isLossy) { "La qualità riguarda solo i formati con perdita" }
        require(images.any { it != null }) { "Serve almeno una facciata" }

        val plan = PageLayouts.computePlan(spec.copy(slotCount = images.size))
        require(!plan.isMultiPage) { "La ricerca della qualità vale su una pagina sola" }
        val bitmap = rasterize(images, plan.first, spec.watermark, today, resolution.dpi)
        val compressFormat = compressFormatFor(format)
        try {
            return QualitySearch.highestUnder(targetBytes) { q ->
                val out = ByteArrayOutputStream()
                bitmap.compress(compressFormat, q, out)
                out.size()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Anteprima a bassa densità: stesso disegno, solo più piccolo. */
    fun renderPreview(
        images: List<Bitmap?>,
        spec: LayoutSpec,
        pageIndex: Int = 0,
        today: String = today()
    ): Bitmap? {
        if (images.none { it != null }) return null
        val plan = PageLayouts.computePlan(spec.copy(slotCount = images.size))
        val layout = plan.pages.getOrNull(pageIndex) ?: plan.first
        val longSidePt = maxOf(layout.pageWidthPt, layout.pageHeightPt)
        val dpi = Raster.dpiForLongSide(longSidePt, PREVIEW_LONG_SIDE)
        return rasterize(images, layout, spec.watermark, today, dpi)
    }

    /** Scrive l'intero piano su uno stream, una pagina PDF per pagina del piano. */
    fun writePdf(
        out: OutputStream,
        images: List<Bitmap?>,
        plan: PagePlan,
        watermark: Watermark,
        today: String
    ) {
        val doc = PdfDocument()
        try {
            plan.pages.forEach { layout ->
                val info = PdfDocument.PageInfo.Builder(
                    layout.pageWidthPt.roundToInt(),
                    layout.pageHeightPt.roundToInt(),
                    layout.pageIndex + 1        // il PDF numera le pagine da 1
                ).create()
                val page = doc.startPage(info)
                PageRenderer.drawPage(page.canvas, images, layout, watermark, today)
                doc.finishPage(page)
            }
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
