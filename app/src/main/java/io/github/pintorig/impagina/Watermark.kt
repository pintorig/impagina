// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

/* =========================================================================
 *  Modello
 * ========================================================================= */

enum class WatermarkStyle(val label: String, val hint: String) {
    NONE(
        "Nessuna",
        "Il foglio resta pulito."
    ),
    BELOW(
        "Sotto",
        "Riga in fondo al foglio: annota la copia senza coprire il documento."
    ),
    DIAGONAL(
        "Diagonale",
        "Scritta obliqua in grigio chiaro sopra il documento, difficile da rimuovere."
    )
}

data class Watermark(
    val text: String = "",
    val style: WatermarkStyle = WatermarkStyle.NONE
) {
    val isActive: Boolean get() = style != WatermarkStyle.NONE && text.isNotBlank()

    companion object {
        /**
         * Formule pronte. Volutamente nessuna dice "copia conforme
         * all'originale": quella è un'autentica ex art. 18 DPR 445/2000, che
         * solo un pubblico ufficiale può rilasciare. Una scritta apposta da sé
         * non autentica nulla, e dichiararlo sul foglio è scorretto.
         */
        val PRESETS = listOf(
            "USO INTERNO",
            "COPIA NON AUTENTICATA",
            "Ad uso ${WatermarkText.DATE_TOKEN}"
        )
    }
}

/* =========================================================================
 *  Testo: espansione dei segnaposto, a capo, adattamento del corpo
 *  Nessuna API Android: la misurazione arriva come funzione, quindi
 *  tutto è verificabile su JVM con un misuratore finto.
 * ========================================================================= */

object WatermarkText {

    /** Segnaposto sostituito con la data odierna al momento della generazione. */
    const val DATE_TOKEN = "{data}"

    fun expand(raw: String, today: String): String =
        raw.replace(DATE_TOKEN, today, ignoreCase = true)

    /**
     * Manda a capo su un massimo di `maxLines` righe.
     *
     * Se il testo eccede comunque, l'ultima riga raccoglie tutto il resto invece
     * di troncare: sarà poi il calcolo del corpo a rimpicciolire quanto serve.
     * Meglio una riga piccola che una frase tagliata a metà, perché qui il testo
     * è un'annotazione legale e perderne un pezzo ne cambia il senso.
     */
    fun wrap(
        text: String,
        maxWidth: Float,
        measure: (String) -> Float,
        maxLines: Int = 2
    ): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var current = words.first()

        for (word in words.drop(1)) {
            val candidate = "$current $word"
            if (measure(candidate) <= maxWidth) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        lines += current

        if (lines.size <= maxLines) return lines
        return lines.take(maxLines - 1) + lines.drop(maxLines - 1).joinToString(" ")
    }

    /**
     * Corpo del testo che porta la larghezza a `targetWidth`.
     *
     * La larghezza di una stringa cresce linearmente con il corpo, quindi basta
     * misurarla una volta a corpo unitario e dividere: niente ricerca binaria.
     */
    fun fittingTextSize(
        targetWidth: Float,
        widthAtUnitSize: Float,
        min: Float,
        max: Float
    ): Float {
        if (widthAtUnitSize <= 0f) return max
        return (targetWidth / widthAtUnitSize).coerceIn(min, max)
    }
}
