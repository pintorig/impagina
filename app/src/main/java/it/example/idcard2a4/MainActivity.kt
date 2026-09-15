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
import androidx.compose.foundation.horizontalScroll
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

    override fun onDestroy() {
        // I PDF di anteprima contengono documenti d'identità: non restano in cache.
        InputLoader.clearCache(this)
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()

    var spec by remember { mutableStateOf(LayoutSpec()) }
    var shots by remember {
        mutableStateOf(List<Bitmap?>(DocumentType.CARTA_IDENTITA.slotLabels.size) { null })
    }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var layout by remember { mutableStateOf(PageLayouts.compute(LayoutSpec())) }
    var busy by remember { mutableStateOf(false) }
    var targetSlot by remember { mutableIntStateOf(0) }

    fun selectType(type: DocumentType) {
        spec = spec.copy(documentType = type, slotCount = type.slotLabels.size)
        shots = List(type.slotLabels.size) { null }
    }

    fun put(index: Int, bmp: Bitmap?) {
        shots = shots.toMutableList().also { it[index] = bmp }
    }

    fun ingest(index: Int, uri: Uri) = scope.launch {
        busy = true
        runCatching { withContext(Dispatchers.IO) { InputLoader.load(ctx, uri) } }
            .onSuccess { put(index, it) }
            .onFailure {
                Toast.makeText(ctx, "File non leggibile: ${it.message}", Toast.LENGTH_LONG).show()
            }
        busy = false
    }

    // --- Import da galleria o file manager, senza permessi ------------------
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { ingest(targetSlot, it) }
    }

    // --- Scanner ML Kit: ritaglia da solo i bordi del documento -------------
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri
                ?.let { ingest(targetSlot, it) }
        }
    }

    fun startScan(index: Int) {
        targetSlot = index
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

    // --- Salvataggio: l'utente sceglie dove --------------------------------
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && shots.any { it != null }) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)!!.use {
                        PdfPageComposer.writeTo(it, shots, spec)
                    }
                }
            }
                .onSuccess { Toast.makeText(ctx, "PDF salvato", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(ctx, "Errore: ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    // --- Anteprima: genera il PDF vero e ne rasterizza la pagina ------------
    LaunchedEffect(shots, spec) {
        layout = PageLayouts.compute(spec.copy(slotCount = shots.size))
        preview = if (shots.none { it != null }) null else withContext(Dispatchers.IO) {
            runCatching {
                val file = PdfPageComposer.writeToCache(ctx, shots, spec)
                InputLoader.load(ctx, Uri.fromFile(file))
            }.getOrNull()
        }
    }

    val type = spec.documentType

    Scaffold(topBar = { TopAppBar(title = { Text("Impagina") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(0.dp))

            // ---------- Tipo di documento ----------
            Text("Documento", fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DocumentType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { selectType(t) },
                        label = { Text(t.label) }
                    )
                }
            }
            Text(type.hint, style = MaterialTheme.typography.bodySmall)

            // ---------- Facciate ----------
            type.slotLabels.chunked(2).forEachIndexed { rowIndex, labelsInRow ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    labelsInRow.forEachIndexed { colIndex, label ->
                        val i = rowIndex * 2 + colIndex
                        SideCard(
                            label = label,
                            bmp = shots.getOrNull(i),
                            ratio = type.previewRatio,
                            modifier = Modifier.weight(1f),
                            onScan = { startScan(i) },
                            onPick = {
                                targetSlot = i
                                pickFile.launch(arrayOf("image/*", "application/pdf"))
                            },
                            onRotate = { put(i, shots.getOrNull(i)?.rotatedBy(90)) },
                            onClear = { put(i, null) }
                        )
                    }
                    if (labelsInRow.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // ---------- Layout di destinazione ----------
            Text("Layout del foglio", fontWeight = FontWeight.SemiBold)

            TwoWayChoice(
                left = Sizing.ACTUAL.label,
                right = Sizing.FIT.label,
                leftSelected = spec.sizing == Sizing.ACTUAL,
                enabled = type.physicalSize != null,
                onLeft = { spec = spec.copy(sizing = Sizing.ACTUAL) },
                onRight = { spec = spec.copy(sizing = Sizing.FIT) }
            )
            TwoWayChoice(
                left = Arrangement.STACKED.label,
                right = Arrangement.SIDE_BY_SIDE.label,
                leftSelected = spec.arrangement == Arrangement.STACKED,
                onLeft = { spec = spec.copy(arrangement = Arrangement.STACKED) },
                onRight = { spec = spec.copy(arrangement = Arrangement.SIDE_BY_SIDE) }
            )
            TwoWayChoice(
                left = PageOrientation.PORTRAIT.label,
                right = PageOrientation.LANDSCAPE.label,
                leftSelected = spec.orientation == PageOrientation.PORTRAIT,
                onLeft = { spec = spec.copy(orientation = PageOrientation.PORTRAIT) },
                onRight = { spec = spec.copy(orientation = PageOrientation.LANDSCAPE) }
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = spec.showLabels,
                    onCheckedChange = { spec = spec.copy(showLabels = it) }
                )
                Spacer(Modifier.width(12.dp))
                Text("Didascalie sotto ogni facciata")
            }

            // ---------- Avviso di riduzione, con la correzione proposta ----------
            if (layout.isScaledDown) {
                val fix = PageLayouts.orientationThatFits(spec)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Questa combinazione non entra a dimensione reale: " +
                                "ridotta al ${layout.scalePercent}%.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (fix != null && fix != spec.orientation) {
                            TextButton(onClick = { spec = spec.copy(orientation = fix) }) {
                                Text("Passa al foglio ${fix.label.lowercase()}")
                            }
                        }
                    }
                }
            }

            // ---------- Anteprima ----------
            preview?.let {
                Text("Anteprima", fontWeight = FontWeight.SemiBold)
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Anteprima della pagina A4",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(layout.pageWidthPt / layout.pageHeightPt)
                        .background(Color.White)
                )
            }

            Button(
                onClick = { saveFile.launch(suggestedFileName(type)) },
                enabled = shots.any { it != null } && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva PDF") }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Text(
                "Tutta l'elaborazione avviene sul dispositivo: nessuna immagine viene inviata in rete.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun suggestedFileName(type: DocumentType): String {
    val slug = type.label.lowercase()
        .replace("'", "-")
        .replace(" ", "-")
        .replace(Regex("[^a-z0-9-]"), "")
    return "$slug-A4.pdf"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoWayChoice(
    left: String,
    right: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    enabled: Boolean = true
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = leftSelected,
            onClick = onLeft,
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(0, 2)
        ) { Text(left) }
        SegmentedButton(
            selected = !leftSelected,
            onClick = onRight,
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(1, 2)
        ) { Text(right) }
    }
}

@Composable
private fun SideCard(
    label: String,
    bmp: Bitmap?,
    ratio: Float,
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
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
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
                TextButton(onClick = onScan, contentPadding = PaddingValues(8.dp)) { Text("Scatta") }
                TextButton(onClick = onPick, contentPadding = PaddingValues(8.dp)) { Text("File") }
            }
            if (bmp != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onRotate, contentPadding = PaddingValues(8.dp)) { Text("Ruota") }
                    TextButton(onClick = onClear, contentPadding = PaddingValues(8.dp)) { Text("Togli") }
                }
            }
        }
    }
}
