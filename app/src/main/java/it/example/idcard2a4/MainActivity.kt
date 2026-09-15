package it.example.idcard2a4

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
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
    var filter by remember { mutableStateOf(ImageFilter.NONE) }
    var export by remember { mutableStateOf(ExportSpec()) }
    // alias di sola lettura: il resto della schermata resta invariato
    val format = export.format
    val resolution = export.resolution
    val quality = export.quality

    val store = remember(ctx) { PresetStore(ctx) }
    var presets by remember { mutableStateOf(emptyList<Preset>()) }
    var namingPreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var exportResult by remember { mutableStateOf<ExportResult?>(null) }
    var weighing by remember { mutableStateOf(false) }

    // `shots` resta la sorgente intatta; `rendered` è la versione filtrata che
    // finisce nell'anteprima e nel PDF. Cambiare filtro non degrada l'originale.
    var shots by remember {
        mutableStateOf(List<Bitmap?>(DocumentType.CARTA_IDENTITA.slotLabels.size) { null })
    }
    var rendered by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var plan by remember { mutableStateOf(PageLayouts.computePlan(LayoutSpec())) }
    var previewPage by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    var targetSlot by remember { mutableIntStateOf(0) }

    fun selectType(type: DocumentType) {
        spec = spec.copy(documentType = type, slotCount = type.slotLabels.size)
        shots = List(type.slotLabels.size) { null }
    }

    /** Cambia il numero di facciate conservando quelle già acquisite. */
    fun setSlotCount(count: Int) {
        val n = count.coerceIn(1, PageLayouts.MAX_SLOTS)
        spec = spec.copy(slotCount = n)
        shots = List(n) { shots.getOrNull(it) }
    }

    fun put(index: Int, bmp: Bitmap?) {
        shots = shots.toMutableList().also { it[index] = bmp }
    }

    fun moveSlot(from: Int, to: Int) {
        shots = Reorder.move(shots, from, to)
    }

    /** Applica una configurazione salvata senza toccare le facciate acquisite. */
    fun applyPreset(preset: Preset) {
        spec = preset.layout
        filter = preset.filter
        export = preset.export
        shots = List(preset.layout.slotCount) { shots.getOrNull(it) }
        previewPage = 0
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
    val saveFile = rememberLauncherForActivityResult(CreateDocumentWithMime()) { uri ->
        if (uri != null && rendered.any { it != null }) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    // se il peso è già stato calcolato, quei byte sono il file:
                    // non c'è motivo di rigenerarli
                    val result = exportResult
                        ?: DocumentExporter.export(rendered, spec, format, resolution, quality)
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(result.bytes) }
                    result
                }
            }
                .onSuccess { Toast.makeText(ctx, "Salvato — ${it.summary}", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(ctx, "Errore: ${it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

    // --- Applicazione del filtro: pesante, fuori dal main thread ------------
    // La cache è indicizzata su (bitmap, filtro): riordinare le facciate cambia
    // solo le posizioni, quindi non ricomincia da capo la filtratura. La potatura
    // per insieme voluto evita di dipendere dall'ordine fra LaunchedEffect.
    val filterCache = remember { mutableMapOf<Pair<Bitmap, ImageFilter>, Bitmap>() }

    LaunchedEffect(shots, filter) {
        filtering = shots.any { it != null } && filter != ImageFilter.NONE
        rendered = withContext(Dispatchers.IO) {
            val wanted = shots.filterNotNull().map { it to filter }.toSet()
            filterCache.keys.retainAll(wanted)
            shots.map { src ->
                src?.let { filterCache.getOrPut(it to filter) { ImageFilters.apply(it, filter) } }
            }
        }
        filtering = false
    }

    // --- Anteprima: genera il PDF vero e ne rasterizza la pagina ------------
    LaunchedEffect(rendered, spec) {
        plan = PageLayouts.computePlan(spec.copy(slotCount = shots.size))
        previewPage = previewPage.coerceIn(0, plan.pageCount - 1)
        // Digitare la filigrana cambia `spec` a ogni tasto. LaunchedEffect annulla
        // l'effetto precedente quando la chiave cambia, quindi questa attesa si
        // comporta da debounce: il PDF si rigenera solo a digitazione ferma.
        delay(250)
        preview = withContext(Dispatchers.IO) {
            runCatching { DocumentExporter.renderPreview(rendered, spec, previewPage) }.getOrNull()
        }
    }

    // Rigenera solo l'anteprima quando si sfoglia, senza rifare il piano.
    LaunchedEffect(previewPage) {
        preview = withContext(Dispatchers.IO) {
            runCatching { DocumentExporter.renderPreview(rendered, spec, previewPage) }.getOrNull()
        }
    }

    // Un'immagine non ha pagine: se il piano ne prevede più di una si torna al PDF.
    LaunchedEffect(plan.isMultiPage) {
        if (plan.isMultiPage && format.isRaster) export = export.copy(format = OutputFormat.PDF)
    }

    // --- Peso reale del file, calcolato in sottofondo -----------------------
    // Chiave separata dall'anteprima: cambiare qualità o formato non impone di
    // ridisegnare l'anteprima, e cambiare layout non impone di ricomprimere
    // finché il debounce non scade.
    LaunchedEffect(rendered, spec, export) {
        exportResult = null
        if (rendered.none { it != null }) return@LaunchedEffect
        delay(400)
        weighing = true
        exportResult = withContext(Dispatchers.IO) {
            runCatching {
                DocumentExporter.export(rendered, spec, format, resolution, quality)
            }.getOrNull()
        }
        weighing = false
    }

    fun fitToTarget(targetBytes: Int) = scope.launch {
        weighing = true
        val fit = withContext(Dispatchers.IO) {
            runCatching {
                DocumentExporter.fitQuality(rendered, spec, format, resolution, targetBytes)
            }.getOrNull()
        }
        weighing = false
        if (fit != null) {
            export = export.copy(quality = fit.quality)
            if (!fit.withinTarget) {
                Toast.makeText(
                    ctx,
                    "Nemmeno alla qualità minima si scende sotto ${Sizes.format(targetBytes)}: " +
                        "prova ad abbassare la risoluzione.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // --- Preset: caricamento all'avvio e memoria dell'ultima configurazione ---
    LaunchedEffect(Unit) {
        presets = store.load()
        store.loadLast()?.let { applyPreset(it) }
    }

    LaunchedEffect(spec, filter, export) {
        // Attesa breve: non si scrive su disco a ogni tasto della filigrana.
        delay(600)
        store.saveLast(store.lastFrom(spec, filter, export))
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

            // ---------- Preset ----------
            if (presets.isNotEmpty()) {
                Text("Preset", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        InputChip(
                            selected = false,
                            onClick = { applyPreset(preset) },
                            label = { Text(preset.name) },
                            trailingIcon = {
                                Text(
                                    "×",
                                    modifier = Modifier.clickable {
                                        presets = PresetCodec.remove(presets, preset.name)
                                        store.save(presets)
                                    }
                                )
                            }
                        )
                    }
                }
            }

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

            // ---------- Numero di facciate ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Facciate: ${spec.slotCount}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedIconButton(
                    onClick = { setSlotCount(spec.slotCount - 1) },
                    enabled = spec.slotCount > 1
                ) { Text("−") }
                Spacer(Modifier.width(8.dp))
                OutlinedIconButton(
                    onClick = { setSlotCount(spec.slotCount + 1) },
                    enabled = spec.slotCount < PageLayouts.MAX_SLOTS
                ) { Text("+") }
            }

            // ---------- Facciate ----------
            PageLayouts.labelsFor(type, spec.slotCount).chunked(2).forEachIndexed { rowIndex, labelsInRow ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    labelsInRow.forEachIndexed { colIndex, label ->
                        val i = rowIndex * 2 + colIndex
                        SideCard(
                            label = label,
                            // la miniatura mostra il filtro, così l'effetto è
                            // visibile subito senza scorrere fino all'anteprima
                            bmp = rendered.getOrNull(i) ?: shots.getOrNull(i),
                            ratio = type.previewRatio,
                            modifier = Modifier.weight(1f),
                            onScan = { startScan(i) },
                            onPick = {
                                targetSlot = i
                                pickFile.launch(arrayOf("image/*", "application/pdf"))
                            },
                            onRotate = { put(i, shots.getOrNull(i)?.rotatedBy(90)) },
                            onClear = { put(i, null) },
                            canMoveBack = Reorder.canMoveBack(i),
                            canMoveForward = Reorder.canMoveForward(shots, i),
                            onMoveBack = { moveSlot(i, i - 1) },
                            onMoveForward = { moveSlot(i, i + 1) }
                        )
                    }
                    if (labelsInRow.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // ---------- Resa ----------
            Text("Resa", fontWeight = FontWeight.SemiBold)
            ChoiceRow(
                labels = ImageFilter.entries.map { it.label },
                selected = ImageFilter.entries.indexOf(filter),
                onSelect = { filter = ImageFilter.entries[it] }
            )
            Text(filter.hint, style = MaterialTheme.typography.bodySmall)

            if (spec.slotCount == 2) {
                TextButton(
                    onClick = { shots = Reorder.swap(shots, 0, 1) },
                    enabled = shots.any { it != null }
                ) { Text("Inverti le due facciate") }
            }

            // ---------- Layout di destinazione ----------
            Text("Layout del foglio", fontWeight = FontWeight.SemiBold)

            ChoiceRow(
                labels = Sizing.entries.map { it.label },
                selected = Sizing.entries.indexOf(spec.sizing),
                enabled = type.physicalSize != null,
                onSelect = { spec = spec.copy(sizing = Sizing.entries[it]) }
            )
            ChoiceRow(
                labels = Arrangement.entries.map { it.label },
                selected = Arrangement.entries.indexOf(spec.arrangement),
                onSelect = { spec = spec.copy(arrangement = Arrangement.entries[it]) }
            )
            ChoiceRow(
                labels = PageOrientation.entries.map { it.label },
                selected = PageOrientation.entries.indexOf(spec.orientation),
                onSelect = { spec = spec.copy(orientation = PageOrientation.entries[it]) }
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = spec.showLabels,
                    onCheckedChange = { spec = spec.copy(showLabels = it) }
                )
                Spacer(Modifier.width(12.dp))
                Text("Didascalie sotto ogni facciata")
            }

            // ---------- Filigrana ----------
            Text("Filigrana", fontWeight = FontWeight.SemiBold)
            ChoiceRow(
                labels = WatermarkStyle.entries.map { it.label },
                selected = WatermarkStyle.entries.indexOf(spec.watermark.style),
                onSelect = {
                    spec = spec.copy(
                        watermark = spec.watermark.copy(style = WatermarkStyle.entries[it])
                    )
                }
            )
            Text(spec.watermark.style.hint, style = MaterialTheme.typography.bodySmall)

            if (spec.watermark.style != WatermarkStyle.NONE) {
                OutlinedTextField(
                    value = spec.watermark.text,
                    onValueChange = {
                        spec = spec.copy(watermark = spec.watermark.copy(text = it))
                    },
                    label = { Text("Testo della filigrana") },
                    supportingText = {
                        Text("${WatermarkText.DATE_TOKEN} viene sostituito con la data di oggi.")
                    },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Watermark.PRESETS.forEach { preset ->
                        AssistChip(
                            onClick = {
                                spec = spec.copy(watermark = spec.watermark.copy(text = preset))
                            },
                            label = { Text(preset) }
                        )
                    }
                }
                Text(
                    "«Copia conforme all'originale» è un'autentica che solo un pubblico " +
                        "ufficiale può rilasciare: una scritta apposta qui non la sostituisce.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---------- Avviso di riduzione, con la correzione proposta ----------
            if (plan.isScaledDown) {
                val fix = PageLayouts.orientationThatFits(spec)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Questa combinazione non entra a dimensione reale: " +
                                "ridotta al ${plan.scalePercent}%.",
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (plan.isMultiPage) "Anteprima — pagina ${previewPage + 1} di ${plan.pageCount}"
                        else "Anteprima",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (plan.isMultiPage) {
                        OutlinedIconButton(
                            onClick = { previewPage-- },
                            enabled = previewPage > 0
                        ) { Text("‹") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedIconButton(
                            onClick = { previewPage++ },
                            enabled = previewPage < plan.pageCount - 1
                        ) { Text("›") }
                    }
                }
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Anteprima della pagina A4",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(plan.first.pageWidthPt / plan.first.pageHeightPt)
                        .background(Color.White)
                )
            }

            // ---------- Formato di uscita ----------
            Text("File di uscita", fontWeight = FontWeight.SemiBold)
            ChoiceRow(
                labels = OutputFormat.entries.map { it.label },
                selected = OutputFormat.entries.indexOf(format),
                // i formati immagine non reggono più di una pagina
                enabled = !plan.isMultiPage,
                onSelect = { export = export.copy(format = OutputFormat.entries[it]) }
            )
            Text(
                if (plan.isMultiPage) {
                    "Il piano occupa ${plan.pageCount} pagine: solo il PDF può contenerle tutte."
                } else {
                    format.hint
                },
                style = MaterialTheme.typography.bodySmall
            )

            // La risoluzione riguarda solo i formati immagine: il PDF incorpora
            // i pixel originali e non ha una densità propria.
            if (format.isRaster) {
                ChoiceRow(
                    labels = ExportResolution.entries.map { it.label },
                    selected = ExportResolution.entries.indexOf(resolution),
                    onSelect = { export = export.copy(resolution = ExportResolution.entries[it]) }
                )
                Text(resolution.hint, style = MaterialTheme.typography.bodySmall)
            }

            if (format.isLossy) {
                Text("Qualità $quality", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { export = export.copy(quality = it.toInt()) },
                    valueRange = QUALITY_MIN.toFloat()..QUALITY_MAX.toFloat(),
                    steps = QUALITY_STEPS.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Sotto 60 gli artefatti iniziano a intaccare i caratteri piccoli.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SIZE_TARGETS.forEach { target ->
                        AssistChip(
                            enabled = !weighing && rendered.any { it != null },
                            onClick = { fitToTarget(target) },
                            label = { Text("≤ ${Sizes.format(target)}") }
                        )
                    }
                }
            }

            Text(
                when {
                    weighing -> "Calcolo del peso…"
                    exportResult != null -> "Peso del file: ${exportResult!!.sizeLabel}"
                    else -> "Peso del file: —"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Button(
                onClick = { saveFile.launch(format.mimeType to suggestedFileName(type, format)) },
                enabled = rendered.any { it != null } && !busy && !filtering,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva ${format.label}") }

            TextButton(
                onClick = { presetName = ""; namingPreset = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Salva queste impostazioni come preset") }

            if (busy || filtering || weighing) LinearProgressIndicator(Modifier.fillMaxWidth())

            Text(
                "Tutta l'elaborazione avviene sul dispositivo: nessuna immagine viene inviata in rete. " +
                    "I preset contengono solo impostazioni, mai le scansioni.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (namingPreset) {
        AlertDialog(
            onDismissRequest = { namingPreset = false },
            title = { Text("Nuovo preset") },
            text = {
                Column {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Nome") },
                        singleLine = true
                    )
                    if (presets.size >= PresetCodec.MAX_PRESETS) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sono già ${PresetCodec.MAX_PRESETS}: il più vecchio verrà scartato.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = presetName.isNotBlank(),
                    onClick = {
                        presets = PresetCodec.upsert(
                            presets,
                            Preset(presetName, spec, filter, export)
                        )
                        store.save(presets)
                        namingPreset = false
                    }
                ) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { namingPreset = false }) { Text("Annulla") }
            }
        )
    }
}

private fun suggestedFileName(type: DocumentType, format: OutputFormat): String {
    val slug = type.label.lowercase()
        .replace("'", "-")
        .replace(" ", "-")
        .replace(Regex("[^a-z0-9-]"), "")
    return "$slug-A4.${format.extension}"
}

/**
 * `CreateDocument` fissa il MIME type alla costruzione, mentre qui cambia con il
 * formato scelto. Un contratto su misura evita di registrare quattro launcher
 * o di ripiegare su un generico che confonde certi file manager.
 */
private class CreateDocumentWithMime : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.first)
            .putExtra(Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Gruppo di scelte mutuamente esclusive, da due a quattro voci. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, labels.size)
            ) { Text(label) }
        }
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
    onClear: () -> Unit,
    canMoveBack: Boolean = false,
    canMoveForward: Boolean = false,
    onMoveBack: () -> Unit = {},
    onMoveForward: () -> Unit = {}
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
                // Frecce di posizione e non trascinamento: le schede stanno in una
                // colonna scorrevole, dove un drag dopo long-press litigherebbe
                // con lo scroll.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onMoveBack,
                        enabled = canMoveBack,
                        contentPadding = PaddingValues(8.dp)
                    ) { Text("◀") }
                    TextButton(
                        onClick = onMoveForward,
                        enabled = canMoveForward,
                        contentPadding = PaddingValues(8.dp)
                    ) { Text("▶") }
                }
            }
        }
    }
}
