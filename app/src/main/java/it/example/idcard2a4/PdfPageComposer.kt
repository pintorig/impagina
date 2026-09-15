package it.example.idcard2a4

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Disegna le facciate acquisite su una singola pagina A4, secondo un
 * [PageLayout] già calcolato.
 *
 * La pagina si crea in punti PostScript (595 × 842), non in pixel: il backend
 * PDF di Android incorpora il bitmap come immagine e gli applica solo una
 * trasformazione, quindi i pixel originali della foto arrivano intatti nel file.
 * Una pagina "a 300 dpi" produrrebbe un foglio grande come un manifesto.
 */
object PdfPageComposer {

    /** Scrive un PDF di una sola pagina. Le immagini `null` lasciano lo slot vuoto. */
    fun writeTo(out: OutputStream, images: List<Bitmap?>, spec: LayoutSpec): PageLayout {
        val filled = images.filterNotNull()
        require(filled.isNotEmpty()) { "Serve almeno una facciata" }

        val layout = PageLayouts.compute(spec.copy(slotCount = images.size))

        val doc = PdfDocument()
        try {
            val info = PdfDocument.PageInfo.Builder(
                layout.pageWidthPt.roundToInt(),
                layout.pageHeightPt.roundToInt(),
                1
            ).create()

            val page = doc.startPage(info)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                isDither = true
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0x60, 0x60, 0x60)
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textSize = (layout.labelHeightPt * 0.62f).coerceAtLeast(6f)
            }

            images.forEachIndexed { i, bmp ->
                val slot = layout.slots.getOrNull(i) ?: return@forEachIndexed
                if (bmp != null) drawFitted(canvas, bmp, slot, imagePaint)
                if (layout.labelHeightPt > 0f) {
                    val text = layout.labels.getOrNull(i).orEmpty()
                    // baseline poco sotto lo slot, dentro la fascia riservata
                    val baseline = slot.bottom + layout.labelHeightPt * 0.72f
                    canvas.drawText(text, slot.centerX, baseline, labelPaint)
                }
            }

            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return layout
    }

    /** Genera in cache: serve all'anteprima, che rasterizza il PDF reale. */
    fun writeToCache(
        ctx: Context,
        images: List<Bitmap?>,
        spec: LayoutSpec,
        name: String = "anteprima.pdf"
    ): File {
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { writeTo(it, images, spec) }
        return f
    }

    /**
     * Disegna il bitmap centrato nello slot mantenendo le proporzioni originali:
     * nessuna deformazione, nessun ritaglio. Se la foto ha proporzioni diverse
     * dal formato nominale, resta un margine invece di una carta stirata.
     */
    private fun drawFitted(canvas: Canvas, bmp: Bitmap, slot: Box, paint: Paint) {
        val s = min(slot.width / bmp.width, slot.height / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        val left = slot.centerX - w / 2f
        val top = slot.centerY - h / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
    }
}
