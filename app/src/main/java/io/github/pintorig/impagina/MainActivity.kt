// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.pintorig.impagina.ui.AnteprimaFoglio
import io.github.pintorig.impagina.ui.AppIcons
import io.github.pintorig.impagina.ui.BarraAzioni
import io.github.pintorig.impagina.ui.Pannello
import io.github.pintorig.impagina.ui.PannelloOpzioni
import io.github.pintorig.impagina.ui.StrisciaFacciate
import io.github.pintorig.impagina.ui.ImpaginaTheme
import io.github.pintorig.impagina.ui.Nota
import io.github.pintorig.impagina.ui.Spazi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Da bordo a bordo: le barre di sistema le colora il tema, non un
        // valore fisso in themes.xml che in modalità scura era sbagliato.
        enableEdgeToEdge()
        setContent { ImpaginaTheme { AppScreen() } }
    }

    override fun onDestroy() {
        // Anteprime e file condivisi contengono documenti d'identità:
        // non restano in cache oltre la sessione.
        InputLoader.clearCache(this)
        SharedFiles.clear(this)
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
    val snackbar = remember { SnackbarHostState() }
    var presetName by remember { mutableStateOf("") }
    var exportResult by remember { mutableStateOf<ExportResult?>(null) }
    var weighing by remember { mutableStateOf(false) }
    var opzioniAperte by remember { mutableStateOf(false) }
    var sezioneAperta by remember { mutableStateOf<Pannello?>(null) }

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

    fun shareDocument() = scope.launch {
        busy = true
        val staged = withContext(Dispatchers.IO) {
            runCatching {
                // riusa i byte già calcolati per il peso, se ci sono
                val result = exportResult
                    ?: DocumentExporter.export(rendered, spec, format, resolution, quality)
                val name = FileNames.forDocument(spec.documentType.label, result.format)
                SharedFiles.stage(ctx, result.bytes, name) to result
            }.getOrNull()
        }
        busy = false
        if (staged == null) {
            snackbar.showSnackbar("Non è stato possibile preparare il file")
        } else if (!SharedFiles.share(ctx, staged.first, staged.second.format.mimeType)) {
            snackbar.showSnackbar("Nessuna app disponibile per la condivisione")
        }
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
            .onFailure { snackbar.showSnackbar("File non leggibile: ${it.message}") }
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
            .addOnFailureListener { error ->
                scope.launch { snackbar.showSnackbar("Scanner non disponibile: ${error.message}") }
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
                .onSuccess { result ->
                    val choice = snackbar.showSnackbar(
                        message = "Salvato — ${result.summary}",
                        actionLabel = "Apri",
                        duration = SnackbarDuration.Long
                    )
                    if (choice == SnackbarResult.ActionPerformed &&
                        !SharedFiles.open(ctx, uri, result.format.mimeType)
                    ) {
                        snackbar.showSnackbar("Nessuna app disponibile per aprire questo formato")
                    }
                }
                .onFailure { snackbar.showSnackbar("Errore: ${it.message}") }
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
                snackbar.showSnackbar(
                    "Nemmeno alla qualità minima si scende sotto ${Sizes.format(targetBytes)}: " +
                        "prova ad abbassare la risoluzione."
                )
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
    val pronto = rendered.any { it != null } && !busy && !filtering

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { opzioniAperte = true }) {
                        Icon(AppIcons.Opzioni, contentDescription = "Opzioni")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            BarraAzioni(
                peso = when {
                    weighing -> "Calcolo del peso…"
                    exportResult != null -> exportResult!!.sizeLabel
                    else -> "—"
                },
                formato = format.label,
                pronto = pronto,
                occupato = busy || filtering || weighing,
                onSalva = {
                    saveFile.launch(format.mimeType to FileNames.forDocument(type.label, format))
                },
                onCondividi = { shareDocument() }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // Il foglio si prende tutto lo spazio che avanza: e' il contenuto.
            AnteprimaFoglio(
                anteprima = preview,
                piano = plan,
                pagina = previewPage,
                onPagina = { previewPage = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spazi.bordo, vertical = Spazi.fra)
            )

            if (plan.isScaledDown) {
                AvvisoRiduzione(
                    percentuale = plan.scalePercent,
                    rimedio = PageLayouts.orientationThatFits(spec)?.takeIf { it != spec.orientation },
                    onRimedio = { spec = spec.copy(orientation = it) },
                    modifier = Modifier.padding(horizontal = Spazi.bordo, vertical = Spazi.stretto)
                )
            }

            // Le facciate restano qui: sono il contenuto, non un'impostazione.
            StrisciaFacciate(
                tipo = type,
                quante = spec.slotCount,
                scatti = shots,
                resi = rendered,
                onQuante = { setSlotCount(it) },
                onScatta = { startScan(it) },
                onScegli = {
                    targetSlot = it
                    pickFile.launch(arrayOf("image/*", "application/pdf"))
                },
                onRuota = { put(it, shots.getOrNull(it)?.rotatedBy(90)) },
                onTogli = { put(it, null) },
                onSposta = { da, a -> moveSlot(da, a) },
                modifier = Modifier.padding(bottom = Spazi.fra)
            )
        }
    }

    if (opzioniAperte) {
        PannelloOpzioni(
            spec = spec,
            filtro = filter,
            export = export,
            piano = plan,
            dimensioneRealePossibile = type.physicalSize != null,
            pesabile = rendered.any { it != null },
            inPesatura = weighing,
            inversionePossibile = shots.any { it != null },
            sezione = sezioneAperta,
            onSezione = { sezioneAperta = it },
            onSpec = { nuovo ->
                // cambiare tipo documento riallinea le facciate
                if (nuovo.documentType != spec.documentType) {
                    shots = List(nuovo.slotCount) { null }
                }
                spec = nuovo
            },
            onFiltro = { filter = it },
            onExport = { export = it },
            onFiligrana = { spec = spec.copy(watermark = it) },
            onInverti = { shots = Reorder.swap(shots, 0, 1) },
            onTetto = { fitToTarget(it) },
            onSalvaPreset = { presetName = ""; namingPreset = true },
            onChiudi = { opzioniAperte = false }
        ) {
            if (presets.isNotEmpty()) {
                RigaPreset(
                    presets = presets,
                    onApplica = { applyPreset(it) },
                    onElimina = {
                        presets = PresetCodec.remove(presets, it.name)
                        store.save(presets)
                    }
                )
            }
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
                        Spacer(Modifier.height(Spazi.stretto))
                        Nota("Sono già ${PresetCodec.MAX_PRESETS}: il più vecchio verrà scartato.")
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

/** I preset salvati, applicabili con un tocco. */
@Composable
private fun RigaPreset(
    presets: List<Preset>,
    onApplica: (Preset) -> Unit,
    onElimina: (Preset) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spazi.stretto)
    ) {
        presets.forEach { preset ->
            InputChip(
                selected = false,
                onClick = { onApplica(preset) },
                label = { Text(preset.name) },
                trailingIcon = {
                    Icon(
                        AppIcons.Chiudi,
                        contentDescription = "Elimina il preset ${preset.name}",
                        modifier = Modifier.size(16.dp).clickable { onElimina(preset) }
                    )
                }
            )
        }
    }
}

/**
 * La combinazione non entra a dimensione reale. L'avviso sta sotto il foglio,
 * dove si vede l'effetto, e propone il rimedio invece di limitarsi a constatare.
 */
@Composable
private fun AvvisoRiduzione(
    percentuale: Int,
    rimedio: PageOrientation?,
    onRimedio: (PageOrientation) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            Modifier.padding(horizontal = Spazi.fra, vertical = Spazi.stretto),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Non entra a dimensione reale: ridotta al $percentuale%.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (rimedio != null) {
                TextButton(onClick = { onRimedio(rimedio) }) {
                    Text("Foglio ${rimedio.label.lowercase()}")
                }
            }
        }
    }
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
