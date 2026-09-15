package it.example.idcard2a4

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppScreen() } }
    }
}

private enum class Slot { FRONTE, RETRO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()

    var front by remember { mutableStateOf<Bitmap?>(null) }
    var back by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(ScaleMode.REAL_SIZE) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(Slot.FRONTE) }

    fun assign(slot: Slot, bmp: Bitmap) {
        if (slot == Slot.FRONTE) front = bmp else back = bmp
    }

    fun ingest(slot: Slot, uri: Uri) = scope.launch {
        busy = true
        runCatching { withContext(Dispatchers.IO) { InputLoader.load(ctx, uri) } }
            .onSuccess { assign(slot, it) }
            .onFailure { Toast.makeText(ctx, "File non leggibile: ${it.message}", Toast.LENGTH_LONG).show() }
        busy = false
    }

    // --- Picker unico per immagini e PDF (nessun permesso richiesto) ---------
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { ingest(target, it) } }

    // --- Scanner ML Kit: ritaglia da solo i bordi del documento --------------
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri
                ?.let { ingest(target, it) }
        }
    }

    fun startScan(slot: Slot) {
        target = slot
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                Toast.makeText(ctx, "Scanner non disponibile: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    // --- Salvataggio tramite SAF (l'utente sceglie dove) ---------------------
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val f = front
        if (uri != null && f != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)!!.use {
                        A4Composer.writeTo(it, f, back, mode)
                    }
                }
            }
                .onSuccess { Toast.makeText(ctx, "PDF salvato", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(ctx, "Errore: ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    // --- Anteprima: genera il PDF in cache e ne rasterizza la pagina ---------
    LaunchedEffect(front, back, mode) {
        val f = front
        preview = if (f == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val file = A4Composer.writeToCache(ctx, f, back, mode)
                InputLoader.load(ctx, Uri.fromFile(file))
            }.getOrNull()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Documento → A4") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SideCard(
                    "Fronte", front, Modifier.weight(1f),
                    onScan = { startScan(Slot.FRONTE) },
                    onPick = { target = Slot.FRONTE; pickFile.launch(arrayOf("image/*", "application/pdf")) },
                    onRotate = { front = front?.rotatedBy(90) },
                    onClear = { front = null }
                )
                SideCard(
                    "Retro", back, Modifier.weight(1f),
                    onScan = { startScan(Slot.RETRO) },
                    onPick = { target = Slot.RETRO; pickFile.launch(arrayOf("image/*", "application/pdf")) },
                    onRotate = { back = back?.rotatedBy(90) },
                    onClear = { back = null }
                )
            }

            Text("Dimensione sul foglio", fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ScaleMode.REAL_SIZE,
                    onClick = { mode = ScaleMode.REAL_SIZE },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Reale 1:1") }
                SegmentedButton(
                    selected = mode == ScaleMode.FIT_PAGE,
                    onClick = { mode = ScaleMode.FIT_PAGE },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Ingrandito") }
            }

            preview?.let {
                Text("Anteprima", fontWeight = FontWeight.SemiBold)
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Anteprima della pagina A4",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(595f / 842f)
                        .background(Color.White)
                )
            }

            Button(
                onClick = { saveFile.launch("documento-A4.pdf") },
                enabled = front != null && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva PDF") }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Text(
                "Tutta l'elaborazione avviene sul dispositivo: nessuna immagine viene inviata in rete.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SideCard(
    label: String,
    bmp: Bitmap?,
    modifier: Modifier = Modifier,
    onScan: () -> Unit,
    onPick: () -> Unit,
    onRotate: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedCard(modifier) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(85.6f / 53.98f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("vuoto", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onScan) { Text("Scatta") }
                TextButton(onClick = onPick) { Text("File") }
            }
            if (bmp != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onRotate) { Text("Ruota") }
                    TextButton(onClick = onClear) { Text("Togli") }
                }
            }
        }
    }
}
