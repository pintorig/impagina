package it.example.idcard2a4

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Porta qualunque input — foto, scansione, PDF — a un bitmap software
 * utilizzabile dal compositore.
 */
object InputLoader {

    /** Lato lungo massimo in memoria: tiene la RAM sotto controllo restando
     *  ampiamente sopra la risoluzione utile per una stampa A4. */
    private const val MAX_DIM = 2400

    fun load(ctx: Context, uri: Uri): Bitmap {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        val isPdf = mime == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)
        return if (isPdf) renderPdfFirstPage(ctx, uri) else decodeImage(ctx, uri)
    }

    /**
     * ImageDecoder applica da solo l'orientamento EXIF, quindi niente foto
     * ruotate a caso.
     *
     * ALLOCATOR_SOFTWARE non è opzionale: un bitmap hardware non può essere
     * disegnato sul Canvas di un PdfDocument e fa crashare l'app in fase di
     * generazione, cioè nel punto più scomodo possibile.
     */
    private fun decodeImage(ctx: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_DIM) decoder.setTargetSampleSize(longest / MAX_DIM)
        }
    }

    /**
     * PdfRenderer pretende un file descriptor seekable, cosa che i provider
     * cloud (Drive, allegati mail) non garantiscono: da qui la copia in cache,
     * che evita un IllegalArgumentException sporadico e difficile da riprodurre.
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
                        val scale = min(300f / 72f, MAX_DIM.toFloat() / max(page.width, page.height))
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)   // i PDF hanno sfondo trasparente
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        return bmp
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }

    /** Rimuove i PDF di anteprima lasciati in cache. Da chiamare all'uscita. */
    fun clearCache(ctx: Context) {
        ctx.cacheDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".pdf") }
            ?.forEach { it.delete() }
    }
}

/** Rotazione a step di 90°, per raddrizzare uno scatto storto. */
fun Bitmap.rotatedBy(degrees: Int): Bitmap {
    val d = ((degrees % 360) + 360) % 360
    if (d == 0) return this
    val m = Matrix().apply { postRotate(d.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}
