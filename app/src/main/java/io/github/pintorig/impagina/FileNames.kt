package io.github.pintorig.impagina

/**
 * Nomi di file proposti all'utente.
 *
 * Non è pignoleria: il nome finisce in un file vero, viene passato a un
 * `ContentProvider` e può arrivare da un'etichetta con apostrofi e accenti.
 * Separatori di percorso e caratteri riservati vanno via, non "di solito" ma
 * sempre.
 */
object FileNames {

    /** Oltre questa lunghezza il nome diventa ingestibile in un file manager. */
    const val MAX_BASE_LENGTH = 60

    private const val FALLBACK = "documento"

    /** Corrispondenze per le lettere accentate più comuni in italiano. */
    private val FOLDED = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a',
        'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n'
    )

    /**
     * Riduce un testo qualsiasi a uno slug sicuro: minuscole, solo lettere e
     * cifre ASCII, trattini al posto di tutto il resto.
     *
     * Un testo che non lascia nemmeno un carattere utile (emoji, ideogrammi,
     * sola punteggiatura) ricade su un nome generico invece di produrre un file
     * senza nome.
     */
    fun sanitize(raw: String): String {
        val slug = buildString(raw.length) {
            raw.lowercase().forEach { c ->
                val folded = FOLDED[c] ?: c
                when {
                    folded in 'a'..'z' || folded in '0'..'9' -> append(folded)
                    else -> append('-')
                }
            }
        }
            .split('-')
            .filter { it.isNotEmpty() }
            .joinToString("-")
            .take(MAX_BASE_LENGTH)
            .trim('-')

        return slug.ifEmpty { FALLBACK }
    }

    /** Nome proposto per l'esportazione di un documento. */
    fun forDocument(label: String, format: OutputFormat): String =
        "${sanitize(label)}-A4.${format.extension}"
}
