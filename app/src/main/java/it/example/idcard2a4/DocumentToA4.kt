package it.example.idcard2a4

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/* =========================================================================
 *  Geometria
 * ========================================================================= */

object PageGeom {
    private const val MM = 72f / 25.4f          // 1 mm in punti PostScript

    const val A4_W = 595f                        // 210 mm
    const val A4_H = 842f                        // 297 mm

    // Formato ID-1 (ISO/IEC 7810): CIE, patente, tessera sanitaria, bancomat…
    val ID1_W = 85.60f * MM                      // ≈ 242.6 pt
    val ID1_H = 53.98f * MM                      // ≈ 153.0 pt

    const val MARGIN = 36f                       // 12.7 mm
    const val GAP = 30f                          // spazio tra fronte e retro
}

/**
 * REAL_SIZE  -> le due facciate sono stampate a dimensione reale 1:1 (85,6 × 53,98 mm).
 *               È quello che gli uffici si aspettano da una "fotocopia" del documento.
 * FIT_PAGE   -> ogni facciata occupa metà pagina, ingrandita il più possibile.
 *               Utile per leggibilità (es. invio via email, dati piccoli).
 */
enum class ScaleMode { REAL_SIZE, FIT_PAGE }

/* =========================================================================
 *  Caricamento degli input
 * ========================================================================= */

object InputLoader {

    /** Lato lungo massimo dei bitmap in memoria: tiene la RAM sotto controllo
     *  restando ampiamente sopra la risoluzione utile per una stampa A4. */
    private const val MAX_DIM = 2400

    /** Accetta indifferentemente un'immagine o un PDF e restituisce un bitmap software. */
    fun load(ctx: Context, uri: Uri): Bitmap {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        val isPdf = mime == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)
        return if (isPdf) renderPdfFirstPage(ctx, uri) else decodeImage(ctx, uri)
    }

    /**
     * ImageDecoder applica da solo l'orientamento EXIF (niente foto ruotate a caso).
     * ALLOCATOR_SOFTWARE è obbligatorio: un bitmap hardware non può essere disegnato
     * sul Canvas di un PdfDocument e fa crashare l'app.
     */
    private fun decodeImage(ctx: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_DIM) {
                decoder.setTargetSampleSize(longest / MAX_DIM)
            }
        }
    }

    /**
     * PdfRenderer pretende un file descriptor seekable: alcuni provider (Drive,
     * allegati mail, cloud vari) non lo garantiscono, quindi copiamo prima in cache.
     */
    private fun renderPdfFirstPage(ctx: Context, uri: Uri): Bitmap {
        val tmp = File.createTempFile("in_", ".pdf", ctx.cacheDir)
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Impossibile aprire il file selezionato")

            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(renderer.pageCount > 0) { "Il PDF non contiene pagine" }
                    renderer.openPage(0).use { page ->
                        // 300 dpi, ma senza sforare MAX_DIM
                        val scale = min(
                            300f / 72f,
                            MAX_DIM.toFloat() / max(page.width, page.height)
                        )
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        // i PDF hanno sfondo trasparente: lo rendiamo bianco
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        return bmp
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }
}

/** Rotazione a step di 90°, per raddrizzare uno scatto storto. */
fun Bitmap.rotatedBy(degrees: Int): Bitmap {
    val d = ((degrees % 360) + 360) % 360
    if (d == 0) return this
    val m = Matrix().apply { postRotate(d.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

/* =========================================================================
 *  Composizione della pagina A4
 * ========================================================================= */

object A4Composer {

    /** Scrive un PDF di UNA sola pagina A4 con fronte e retro incolonnati. */
    fun writeTo(
        out: OutputStream,
        front: Bitmap,
        back: Bitmap?,
        mode: ScaleMode = ScaleMode.REAL_SIZE
    ) {
        val doc = PdfDocument()
        try {
            val info = PdfDocument.PageInfo
                .Builder(PageGeom.A4_W.toInt(), PageGeom.A4_H.toInt(), 1)
                .create()
            val page = doc.startPage(info)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                isDither = true
            }

            val slots = slots(twoSides = back != null, mode = mode)
            drawFitted(canvas, front, slots[0], paint)
            back?.let { drawFitted(canvas, it, slots[1], paint) }

            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Comodo per l'anteprima: genera in cache e restituisce il file. */
    fun writeToCache(
        ctx: Context,
        front: Bitmap,
        back: Bitmap?,
        mode: ScaleMode,
        name: String = "anteprima.pdf"
    ): File {
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { writeTo(it, front, back, mode) }
        return f
    }

    /** I due rettangoli-contenitore in cui far entrare fronte e retro. */
    private fun slots(twoSides: Boolean, mode: ScaleMode): List<RectF> {
        val usableW = PageGeom.A4_W - 2 * PageGeom.MARGIN
        val usableH = PageGeom.A4_H - 2 * PageGeom.MARGIN

        return if (mode == ScaleMode.REAL_SIZE) {
            val w = PageGeom.ID1_W
            val h = PageGeom.ID1_H
            val blockH = if (twoSides) 2 * h + PageGeom.GAP else h
            val left = (PageGeom.A4_W - w) / 2f
            // blocco leggermente sopra il centro ottico della pagina
            val top = PageGeom.MARGIN + (usableH - blockH) / 3f
            listOf(
                RectF(left, top, left + w, top + h),
                RectF(left, top + h + PageGeom.GAP, left + w, top + 2 * h + PageGeom.GAP)
            )
        } else {
            val h = if (twoSides) (usableH - PageGeom.GAP) / 2f else usableH
            val l = PageGeom.MARGIN
            val t = PageGeom.MARGIN
            listOf(
                RectF(l, t, l + usableW, t + h),
                RectF(l, t + h + PageGeom.GAP, l + usableW, t + 2 * h + PageGeom.GAP)
            )
        }
    }

    /**
     * Disegna il bitmap centrato nello slot mantenendo le proporzioni originali
     * (nessuna deformazione, nessun ritaglio).
     *
     * Nota: il backend PDF di Android incorpora il bitmap come immagine e applica
     * una trasformazione — i pixel originali NON vengono buttati via, quindi la
     * pagina resta di qualità piena anche se è "solo" 595×842 punti.
     */
    private fun drawFitted(canvas: Canvas, bmp: Bitmap, slot: RectF, paint: Paint) {
        val s = min(slot.width() / bmp.width, slot.height() / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        val left = slot.centerX() - w / 2f
        val top = slot.centerY() - h / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
    }
}
