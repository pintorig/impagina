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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
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

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY)

    // --- filigrana in fondo ---
    private const val BELOW_MAX_PT = 10f
    private const val BELOW_MIN_PT = 6f
    private const val BELOW_LINE_SPACING = 1.25f

    // --- filigrana diagonale ---
    private const val DIAGONAL_COVERAGE = 0.78f   // quota della diagonale occupata
    private const val DIAGONAL_MIN_PT = 10f
    private const val DIAGONAL_MAX_PT = 90f
    private const val DIAGONAL_ALPHA = 56         // su 255: leggibile sotto, visibile sopra

    /** Scrive un PDF di una sola pagina. Le immagini `null` lasciano lo slot vuoto. */
    fun writeTo(
        out: OutputStream,
        images: List<Bitmap?>,
        spec: LayoutSpec,
        today: String = LocalDate.now().format(DATE_FORMAT)
    ): PageLayout {
        require(images.any { it != null }) { "Serve almeno una facciata" }

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
                    val baseline = slot.bottom + layout.labelHeightPt * 0.72f
                    canvas.drawText(text, slot.centerX, baseline, labelPaint)
                }
            }

            // La filigrana va per ultima: deve stare sopra al documento, non sotto.
            if (spec.watermark.isActive) {
                drawWatermark(canvas, layout, spec.watermark, today)
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

    private fun drawWatermark(
        canvas: Canvas,
        layout: PageLayout,
        watermark: Watermark,
        today: String
    ) {
        val text = WatermarkText.expand(watermark.text, today).trim()
        if (text.isEmpty()) return

        when (watermark.style) {
            WatermarkStyle.BELOW -> drawBelow(canvas, layout, text)
            WatermarkStyle.DIAGONAL -> drawDiagonal(canvas, layout, text)
            WatermarkStyle.NONE -> Unit
        }
    }

    /** Annotazione nella fascia riservata in fondo: non copre nulla. */
    private fun drawBelow(canvas: Canvas, layout: PageLayout, text: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x44, 0x44, 0x44)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = BELOW_MAX_PT
        }

        val maxWidth = layout.pageWidthPt - 2 * PageLayouts.MARGIN_PT
        val lines = WatermarkText.wrap(text, maxWidth, paint::measureText, maxLines = 2)
        if (lines.isEmpty()) return

        // Una parola singola lunghissima può eccedere comunque: si rimpicciolisce
        // invece di troncare, perché è un'annotazione e perderne un pezzo
        // ne cambierebbe il senso.
        val widest = lines.maxOf { paint.measureText(it) }
        if (widest > maxWidth) {
            paint.textSize = WatermarkText.fittingTextSize(
                targetWidth = maxWidth,
                widthAtUnitSize = widest / paint.textSize,
                min = BELOW_MIN_PT,
                max = BELOW_MAX_PT
            )
        }

        val lineHeight = paint.textSize * BELOW_LINE_SPACING
        val blockBottom = layout.pageHeightPt - PageLayouts.MARGIN_PT
        var baseline = blockBottom - (lines.size - 1) * lineHeight
        lines.forEach {
            canvas.drawText(it, layout.pageWidthPt / 2f, baseline, paint)
            baseline += lineHeight
        }
    }

    /**
     * Scritta obliqua sopra il documento.
     *
     * L'angolo segue la diagonale del foglio, così la scritta è lunga quanto
     * possibile e attraversa entrambe le facciate: una filigrana che copre solo
     * metà pagina si ritaglia via in un secondo. L'alfa resta bassa perché il
     * documento sotto deve restare leggibile — altrimenti la copia è inutile.
     */
    private fun drawDiagonal(canvas: Canvas, layout: PageLayout, text: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(DIAGONAL_ALPHA, 0x20, 0x20, 0x20)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 1f
        }

        val w = layout.pageWidthPt
        val h = layout.pageHeightPt
        val diagonal = hypot(w, h)

        paint.textSize = WatermarkText.fittingTextSize(
            targetWidth = diagonal * DIAGONAL_COVERAGE,
            widthAtUnitSize = paint.measureText(text),
            min = DIAGONAL_MIN_PT,
            max = DIAGONAL_MAX_PT
        )

        val angle = -Math.toDegrees(atan2(h.toDouble(), w.toDouble())).toFloat()

        canvas.save()
        canvas.rotate(angle, w / 2f, h / 2f)
        // baseline spostata di poco sotto il centro, per centrare otticamente
        canvas.drawText(text, w / 2f, h / 2f + paint.textSize * 0.35f, paint)
        canvas.restore()
    }
}
