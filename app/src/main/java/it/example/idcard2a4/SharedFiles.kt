package it.example.idcard2a4

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Condivisione e apertura dei file prodotti.
 *
 * Per condividere serve un `Uri` che altre app possano leggere, e per averlo
 * bisogna passare da un `FileProvider` con un file su disco. Sono documenti
 * d'identità, quindi quel file non deve restare in giro: vive in una cartella
 * dedicata della cache, ripulita a ogni nuova condivisione e all'uscita.
 */
object SharedFiles {

    private const val DIR = "shared"

    private fun directory(ctx: Context): File =
        File(ctx.cacheDir, DIR).apply { mkdirs() }

    private fun authority(ctx: Context): String = "${ctx.packageName}.fileprovider"

    /**
     * Scrive il file da condividere e ne restituisce l'Uri.
     *
     * La cartella viene svuotata prima: si condivide una cosa alla volta, e
     * lasciare le precedenti significherebbe accumulare scansioni di documenti
     * nella cache senza motivo.
     */
    fun stage(ctx: Context, bytes: ByteArray, fileName: String): Uri {
        val dir = directory(ctx)
        dir.listFiles()?.forEach { it.delete() }

        val file = File(dir, fileName)
        file.outputStream().use { it.write(bytes) }
        return FileProvider.getUriForFile(ctx, authority(ctx), file)
    }

    /** Foglio di condivisione di sistema. */
    fun share(ctx: Context, uri: Uri, mimeType: String): Boolean {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            // nessun oggetto precompilato: finirebbe per suggerire un testo
            // che descrive un documento d'identità
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        return launch(ctx, Intent.createChooser(send, "Condividi il documento"))
    }

    /** Apre il file con l'app predefinita, per verificarlo senza cercarlo. */
    fun open(ctx: Context, uri: Uri, mimeType: String): Boolean {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return launch(ctx, view)
    }

    /** Svuota la cartella di condivisione. Da chiamare all'uscita. */
    fun clear(ctx: Context) {
        File(ctx.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * Su un dispositivo senza lettore PDF l'intent non trova destinatari:
     * si restituisce `false` e il chiamante avvisa, invece di far crollare
     * l'app con una ActivityNotFoundException.
     */
    private fun launch(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
