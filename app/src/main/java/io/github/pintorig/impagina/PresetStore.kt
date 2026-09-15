package io.github.pintorig.impagina

import android.content.Context

/**
 * Persistenza dei preset.
 *
 * Strato sottile su `SharedPreferences`: tutta la logica sta in [PresetCodec],
 * che è puro e verificato su JVM. Qui non c'è niente da testare se non la
 * lettura e scrittura di due stringhe.
 *
 * Quello che finisce su disco sono solo impostazioni — **mai le immagini**.
 * Il testo della filigrana però può contenere dati personali ("ad uso
 * iscrizione di …"), quindi vive nell'archivio privato dell'app e [clear] esiste
 * per cancellarlo davvero.
 */
class PresetStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Preset> =
        PresetCodec.decodeAll(prefs.getString(KEY_PRESETS, "").orEmpty())

    fun save(presets: List<Preset>) {
        prefs.edit().putString(KEY_PRESETS, PresetCodec.encodeAll(presets)).apply()
    }

    /**
     * L'ultima configurazione usata, ripristinata all'avvio.
     *
     * È la parte che serve davvero a chi ripete la stessa pratica: rimettere a
     * mano formato, risoluzione e filigrana ogni volta è la seccatura vera,
     * più che non avere preset con un nome.
     */
    fun loadLast(): Preset? =
        prefs.getString(KEY_LAST, null)?.let { PresetCodec.decode(it) }

    fun saveLast(preset: Preset) {
        prefs.edit().putString(KEY_LAST, PresetCodec.encode(preset)).apply()
    }

    /** Cancella preset e ultima configurazione. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "preset"
        const val KEY_PRESETS = "presets"
        const val KEY_LAST = "last"

        /** Nome riservato all'ultima configurazione, mai mostrato all'utente. */
        const val LAST_NAME = "@ultima"
    }

    /** Involucro per l'ultima configurazione, che un nome lo deve avere. */
    fun lastFrom(layout: LayoutSpec, filter: ImageFilter, export: ExportSpec): Preset =
        Preset(name = LAST_NAME, layout = layout, filter = filter, export = export)
}
