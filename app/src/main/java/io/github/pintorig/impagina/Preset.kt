// Copyright 2026 pintorig
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

/* =========================================================================
 *  Modello
 * ========================================================================= */

/** Le scelte che riguardano il file prodotto, raccolte insieme. */
data class ExportSpec(
    val format: OutputFormat = OutputFormat.PDF,
    val resolution: ExportResolution = ExportResolution.STANDARD,
    val quality: Int = DEFAULT_QUALITY
)

/**
 * Una configurazione completa salvata dall'utente: tutto tranne le immagini.
 *
 * I bitmap non entrano mai in un preset, né su disco: sono documenti d'identità
 * e non hanno motivo di sopravvivere alla sessione.
 */
data class Preset(
    val name: String,
    val layout: LayoutSpec = LayoutSpec(),
    val filter: ImageFilter = ImageFilter.NONE,
    val export: ExportSpec = ExportSpec()
)

/* =========================================================================
 *  Serializzazione
 * ========================================================================= */

/**
 * Codifica un preset su una riga sola, in campi `chiave=valore` separati da `;`.
 *
 * Il formato è fatto a mano di proposito: niente dipendenze, e soprattutto
 * niente `org.json`, che essendo API Android non sarebbe verificabile su JVM.
 * Il prezzo è che l'escaping va fatto bene, perché il testo della filigrana è
 * input libero e contiene tranquillamente `=`, `;`, a capo o backslash.
 */
object PresetCodec {

    const val MAX_PRESETS = 8

    private const val FIELD_SEPARATOR = ';'
    private const val KEY_SEPARATOR = '='
    private const val ESCAPE = '\\'
    private const val RECORD_SEPARATOR = "\n"

    /* --- escaping ---------------------------------------------------- */

    /**
     * Protegge i caratteri che hanno un ruolo nel formato, più gli a capo:
     * i preset si concatenano una riga ciascuno, quindi un a capo non protetto
     * spaccherebbe il record in due.
     */
    fun escape(value: String): String = buildString(value.length) {
        value.forEach { c ->
            when (c) {
                ESCAPE -> append("\\\\")
                FIELD_SEPARATOR -> append("\\s")
                KEY_SEPARATOR -> append("\\e")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    fun unescape(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == ESCAPE && i + 1 < value.length) {
                when (value[i + 1]) {
                    ESCAPE -> append(ESCAPE)
                    's' -> append(FIELD_SEPARATOR)
                    'e' -> append(KEY_SEPARATOR)
                    'n' -> append('\n')
                    'r' -> append('\r')
                    // sequenza sconosciuta: si tiene il letterale invece di
                    // perdere caratteri, così un formato futuro degrada piano
                    else -> append(ESCAPE).append(value[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    /* --- codifica ------------------------------------------------------ */

    fun encode(preset: Preset): String {
        val fields = linkedMapOf(
            "n" to preset.name,
            "t" to preset.layout.documentType.name,
            "z" to preset.layout.sizing.name,
            "a" to preset.layout.arrangement.name,
            "o" to preset.layout.orientation.name,
            "c" to preset.layout.slotCount.toString(),
            "l" to preset.layout.showLabels.toString(),
            "wt" to preset.layout.watermark.text,
            "ws" to preset.layout.watermark.style.name,
            "f" to preset.filter.name,
            "xf" to preset.export.format.name,
            "xr" to preset.export.resolution.name,
            "xq" to preset.export.quality.toString()
        )
        return fields.entries.joinToString(FIELD_SEPARATOR.toString()) { (k, v) ->
            "$k$KEY_SEPARATOR${escape(v)}"
        }
    }

    /**
     * Decodifica tollerante: campi mancanti o valori non riconosciuti ricadono
     * sul default invece di far fallire tutto.
     *
     * Serve alla compatibilità nel tempo: un preset salvato oggi deve restare
     * caricabile dopo che un `enum` avrà guadagnato o perso una voce. L'unico
     * campo indispensabile è il nome.
     */
    fun decode(line: String): Preset? {
        if (line.isBlank()) return null

        val fields = HashMap<String, String>()
        splitUnescaped(line).forEach { field ->
            val at = indexOfUnescaped(field, KEY_SEPARATOR)
            if (at > 0) {
                fields[field.substring(0, at)] = unescape(field.substring(at + 1))
            }
        }

        val name = fields["n"]?.trim().orEmpty()
        if (name.isEmpty()) return null

        val layout = LayoutSpec(
            documentType = enumOrDefault(fields["t"], DocumentType.CARTA_IDENTITA),
            sizing = enumOrDefault(fields["z"], Sizing.ACTUAL),
            arrangement = enumOrDefault(fields["a"], Arrangement.STACKED),
            orientation = enumOrDefault(fields["o"], PageOrientation.PORTRAIT),
            slotCount = (fields["c"]?.toIntOrNull() ?: 2).coerceIn(1, PageLayouts.MAX_SLOTS),
            showLabels = fields["l"].toBoolean(),
            watermark = Watermark(
                text = fields["wt"].orEmpty(),
                style = enumOrDefault(fields["ws"], WatermarkStyle.NONE)
            )
        )

        return Preset(
            name = name,
            layout = layout,
            filter = enumOrDefault(fields["f"], ImageFilter.NONE),
            export = ExportSpec(
                format = enumOrDefault(fields["xf"], OutputFormat.PDF),
                resolution = enumOrDefault(fields["xr"], ExportResolution.STANDARD),
                quality = (fields["xq"]?.toIntOrNull() ?: DEFAULT_QUALITY)
                    .coerceIn(QUALITY_MIN, QUALITY_MAX)
            )
        )
    }

    fun encodeAll(presets: List<Preset>): String =
        presets.joinToString(RECORD_SEPARATOR) { encode(it) }

    fun decodeAll(text: String): List<Preset> =
        text.split(RECORD_SEPARATOR).mapNotNull { decode(it) }

    /* --- inserimento ordinato ------------------------------------------ */

    /**
     * Aggiunge o sostituisce un preset omonimo, mantenendo il più recente in
     * testa e scartando i più vecchi oltre [MAX_PRESETS].
     */
    fun upsert(presets: List<Preset>, preset: Preset): List<Preset> {
        val name = preset.name.trim()
        if (name.isEmpty()) return presets
        val cleaned = preset.copy(name = name)
        return (listOf(cleaned) + presets.filterNot { it.name.equals(name, ignoreCase = true) })
            .take(MAX_PRESETS)
    }

    fun remove(presets: List<Preset>, name: String): List<Preset> =
        presets.filterNot { it.name.equals(name.trim(), ignoreCase = true) }

    /* --- utilità ------------------------------------------------------- */

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    /** Divide sui separatori non preceduti da un escape. */
    private fun splitUnescaped(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == ESCAPE && i + 1 < line.length -> {
                    current.append(c).append(line[i + 1]); i += 2
                }
                c == FIELD_SEPARATOR -> {
                    out += current.toString(); current.clear(); i++
                }
                else -> {
                    current.append(c); i++
                }
            }
        }
        out += current.toString()
        return out
    }

    /** Primo separatore non protetto da escape, oppure -1. */
    private fun indexOfUnescaped(text: String, target: Char): Int {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == ESCAPE && i + 1 < text.length) {
                i += 2
            } else {
                if (c == target) return i
                i++
            }
        }
        return -1
    }
}
