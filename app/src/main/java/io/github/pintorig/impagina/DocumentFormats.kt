// Copyright 2026 pintorig
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/* =========================================================================
 *  Formati fisici
 * ========================================================================= */

/** Dimensioni reali di un documento, in millimetri. */
data class PhysicalSize(val widthMm: Float, val heightMm: Float) {
    val aspectRatio: Float get() = widthMm / heightMm
}

object Formats {
    /** ISO/IEC 7810 ID-1 — 85,60 × 53,98 mm. CIE, patente, tessera sanitaria, bancomat. */
    val ID1 = PhysicalSize(85.60f, 53.98f)

    /** ISO/IEC 7810 ID-3 — 125 × 88 mm. Pagina del libretto di passaporto. */
    val ID3 = PhysicalSize(125.00f, 88.00f)
}

/**
 * I documenti supportati. La dimensione fisica è ciò che permette la stampa 1:1;
 * quando è `null` l'impaginazione può solo adattarsi alla pagina.
 */
enum class DocumentType(
    val label: String,
    val physicalSize: PhysicalSize?,
    val slotLabels: List<String>,
    val hint: String
) {
    CARTA_IDENTITA(
        label = "Carta d'identità",
        physicalSize = Formats.ID1,
        slotLabels = listOf("Fronte", "Retro"),
        hint = "CIE in formato tessera. Per la vecchia carta cartacea usa «Altro»."
    ),
    PATENTE(
        label = "Patente",
        physicalSize = Formats.ID1,
        slotLabels = listOf("Fronte", "Retro"),
        hint = "Patente in formato tessera. Per il vecchio modello cartaceo a tre ante usa «Altro» in orizzontale."
    ),
    TESSERA_SANITARIA(
        label = "Tessera sanitaria",
        physicalSize = Formats.ID1,
        slotLabels = listOf("Fronte (codice fiscale)", "Retro (tessera TEAM)"),
        hint = "Stesso formato tessera. Il retro è la tessera europea di assicurazione malattia."
    ),
    PASSAPORTO(
        label = "Passaporto",
        physicalSize = Formats.ID3,
        slotLabels = listOf("Pagina dati", "Pagina firma"),
        hint = "Pagina singola del libretto (ID-3). Aggiungi facciate per visti e timbri."
    ),
    ALTRO(
        label = "Altro documento",
        physicalSize = null,
        slotLabels = listOf("Fronte", "Retro"),
        hint = "Dimensione non nota: l'impaginazione si adatta alla pagina mantenendo le proporzioni."
    );

    /** Proporzioni da usare per i segnaposto nell'anteprima. */
    val previewRatio: Float get() = physicalSize?.aspectRatio ?: 1.5f
}

/* =========================================================================
 *  Specifica del layout
 * ========================================================================= */

/** Dimensione sul foglio. */
enum class Sizing(val label: String) {
    /** Stampa 1:1 rispetto al documento reale. Il default: è la resa "fotocopia". */
    ACTUAL("Reale 1:1"),

    /** Ogni facciata occupa la sua cella per intero. Più leggibile a schermo. */
    FIT("Adatta al foglio")
}

/** Quante colonne compone la griglia. */
enum class Arrangement(val label: String, val columns: Int) {
    STACKED("In colonna", 1),
    SIDE_BY_SIDE("Affiancate", 2)
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Verticale"),
    LANDSCAPE("Orizzontale")
}

data class LayoutSpec(
    val documentType: DocumentType = DocumentType.CARTA_IDENTITA,
    val sizing: Sizing = Sizing.ACTUAL,
    val arrangement: Arrangement = Arrangement.STACKED,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val slotCount: Int = 2,
    val showLabels: Boolean = false,
    val watermark: Watermark = Watermark()
)

/* =========================================================================
 *  Risultato del calcolo
 * ========================================================================= */

/** Rettangolo in punti PostScript. Volutamente non è `RectF`: così la
 *  matematica del layout resta Kotlin puro e testabile senza emulatore. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class PageLayout(
    val pageIndex: Int,
    val pageCount: Int,
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val slots: List<Box>,
    /** Indice globale della facciata che occupa ogni slot di questa pagina. */
    val slotIndices: List<Int>,
    val labels: List<String>,
    val labelHeightPt: Float,
    /** Fascia in fondo sottratta all'area utile e riservata alla filigrana. */
    val reservedBottomPt: Float,
    /** 1.0 = dimensione reale rispettata; < 1 = il blocco è stato ridotto per entrare. */
    val appliedScale: Float,
    /** Cosa è stato applicato davvero: può differire da quanto richiesto. */
    val effectiveSizing: Sizing
) {
    val isScaledDown: Boolean get() = effectiveSizing == Sizing.ACTUAL && appliedScale < 0.999f

    /** Percentuale da mostrare all'utente quando la riduzione è avvenuta. */
    val scalePercent: Int get() = Math.round(appliedScale * 100f)
}

/**
 * Il piano completo: una o più pagine con la stessa griglia e la stessa
 * dimensione di cella. Le celle restano identiche anche quando l'ultima pagina
 * è parziale, altrimenti la stampa risulterebbe disomogenea.
 */
data class PagePlan(
    val pages: List<PageLayout>,
    val columns: Int,
    val rowsPerPage: Int,
    val slotCount: Int
) {
    val pageCount: Int get() = pages.size
    val slotsPerPage: Int get() = columns * rowsPerPage
    val isMultiPage: Boolean get() = pages.size > 1
    val first: PageLayout get() = pages.first()

    val isScaledDown: Boolean get() = pages.any { it.isScaledDown }
    val appliedScale: Float get() = pages.minOf { it.appliedScale }
    val scalePercent: Int get() = Math.round(appliedScale * 100f)
}

/* =========================================================================
 *  Calcolo dei layout
 * ========================================================================= */

object PageLayouts {

    const val MM_TO_PT = 72f / 25.4f

    /** A4 in punti: 210 × 297 mm arrotondati all'intero (scarto < 0,15 mm). */
    const val A4_SHORT_PT = 595f
    const val A4_LONG_PT = 842f

    const val MARGIN_PT = 36f          // 12,7 mm
    const val GAP_PT = 24f             // spazio tra le facciate
    const val LABEL_PT = 15f           // fascia per la didascalia sotto ogni slot
    const val WATERMARK_BAND_PT = 34f  // fascia in fondo, riservata alla filigrana

    /** Oltre questo numero le facciate sono troppe per essere gestite a mano. */
    const val MAX_SLOTS = 12

    /**
     * Righe per pagina in modalità adattata.
     *
     * Qui non esiste un limite fisico — qualunque numero di celle "entra",
     * rimpicciolendosi — quindi il tetto è una scelta di leggibilità: oltre
     * quattro righe la facciata diventa una striscia schiacciata, e tanto vale
     * passare alla pagina successiva.
     */
    const val FIT_MAX_ROWS = 4

    /** Il piano completo delle pagine. */
    fun computePlan(spec: LayoutSpec): PagePlan {
        val n = spec.slotCount.coerceIn(1, MAX_SLOTS)

        val portrait = spec.orientation == PageOrientation.PORTRAIT
        val pageW = if (portrait) A4_SHORT_PT else A4_LONG_PT
        val pageH = if (portrait) A4_LONG_PT else A4_SHORT_PT
        val usableW = pageW - 2 * MARGIN_PT

        // La filigrana in fondo non si sovrappone al documento: le si riserva
        // una fascia, che in modalità adattata sottrae spazio alle facciate.
        val reservedBottom = reservedBottomFor(spec.watermark)
        val usableH = pageH - 2 * MARGIN_PT - reservedBottom

        val physical = spec.documentType.physicalSize
        // Un documento senza dimensione nota non può essere stampato 1:1:
        // si ricade sull'adattamento, e lo si dichiara nel risultato.
        val effectiveSizing =
            if (spec.sizing == Sizing.ACTUAL && physical == null) Sizing.FIT else spec.sizing

        val baseLabel = if (spec.showLabels) LABEL_PT else 0f

        val columns = min(spec.arrangement.columns, n)
        val maxRows = maxRowsPerPage(effectiveSizing, physical, usableH, baseLabel)
        // Non si riservano righe che non servono: con due facciate la griglia
        // resta di due righe, non di quattro con mezza pagina vuota.
        val rowsPerPage = min(maxRows, ceilDiv(n, columns))
        val perPage = columns * rowsPerPage
        val pageCount = ceilDiv(n, perPage)

        // Dimensione della cella: calcolata una volta sulla griglia piena e poi
        // riusata anche dall'ultima pagina, che può essere parziale.
        val slotW: Float
        val slotH: Float
        val gap: Float
        val labelH: Float
        val scale: Float

        if (effectiveSizing == Sizing.ACTUAL) {
            val w0 = physical!!.widthMm * MM_TO_PT
            val h0 = physical.heightMm * MM_TO_PT
            val blockW0 = columns * w0 + (columns - 1) * GAP_PT
            val blockH0 = rowsPerPage * (h0 + baseLabel) + (rowsPerPage - 1) * GAP_PT

            // Se la combinazione non entra (es. due pagine di passaporto affiancate
            // su foglio verticale), si riduce l'intero blocco in modo uniforme
            // invece di tagliare o deformare. Anche i vuoti scalano, altrimenti
            // il blocco ridotto sborderebbe comunque.
            scale = min(1f, min(usableW / blockW0, usableH / blockH0))
            slotW = w0 * scale
            slotH = h0 * scale
            gap = GAP_PT * scale
            labelH = baseLabel * scale
        } else {
            scale = 1f
            gap = GAP_PT
            labelH = baseLabel
            slotW = (usableW - (columns - 1) * gap) / columns
            slotH = (usableH - (rowsPerPage - 1) * gap) / rowsPerPage - labelH
        }

        val labels = labelsFor(spec.documentType, n)

        val pages = (0 until pageCount).map { pageIndex ->
            val firstSlot = pageIndex * perPage
            val countHere = min(perPage, n - firstSlot)
            val colsUsed = min(columns, countHere)
            val rowsUsed = ceilDiv(countHere, columns)

            val blockW = colsUsed * slotW + (colsUsed - 1) * gap
            val blockH = rowsUsed * (slotH + labelH) + (rowsUsed - 1) * gap

            val originX = (pageW - blockW) / 2f
            // A dimensione reale il blocco sta leggermente sopra il centro geometrico:
            // è il centro ottico, e lascia spazio in basso per timbri o annotazioni.
            val originY = if (effectiveSizing == Sizing.ACTUAL) {
                MARGIN_PT + (usableH - blockH) / 3f
            } else {
                MARGIN_PT
            }

            val slots = (0 until countHere).map { i ->
                val r = i / columns
                val c = i % columns
                val l = originX + c * (slotW + gap)
                val t = originY + r * (slotH + labelH + gap)
                Box(l, t, l + slotW, t + slotH)
            }
            val indices = (0 until countHere).map { firstSlot + it }

            PageLayout(
                pageIndex = pageIndex,
                pageCount = pageCount,
                pageWidthPt = pageW,
                pageHeightPt = pageH,
                slots = slots,
                slotIndices = indices,
                labels = indices.map { labels[it] },
                labelHeightPt = labelH,
                reservedBottomPt = reservedBottom,
                appliedScale = scale,
                effectiveSizing = effectiveSizing
            )
        }

        return PagePlan(pages, columns, rowsPerPage, n)
    }

    /** Scorciatoia per la prima pagina, che nei casi a pagina singola è l'unica. */
    fun compute(spec: LayoutSpec): PageLayout = computePlan(spec).first

    /**
     * Quante righe stanno su una pagina.
     *
     * A dimensione reale è un fatto fisico: quante tessere entrano davvero in un
     * A4. In modalità adattata è invece una scelta di leggibilità.
     */
    fun maxRowsPerPage(
        sizing: Sizing,
        physical: PhysicalSize?,
        usableH: Float,
        labelHeight: Float
    ): Int {
        if (sizing != Sizing.ACTUAL || physical == null) return FIT_MAX_ROWS
        val rowHeight = physical.heightMm * MM_TO_PT + labelHeight
        val rows = floor((usableH + GAP_PT) / (rowHeight + GAP_PT)).toInt()
        return max(1, rows)
    }

    /** Spazio da sottrarre in fondo al foglio per la filigrana. */
    fun reservedBottomFor(watermark: Watermark): Float =
        if (watermark.isActive && watermark.style == WatermarkStyle.BELOW) WATERMARK_BAND_PT else 0f

    /** Etichette del tipo documento, completate se gli slot sono più del previsto. */
    fun labelsFor(type: DocumentType, count: Int): List<String> =
        (0 until count).map { i ->
            type.slotLabels.getOrNull(i) ?: "Pagina ${i + 1}"
        }

    /**
     * Suggerisce l'orientamento che consente la stampa 1:1, quando esiste.
     * Serve alla UI per proporre la correzione invece di limitarsi ad avvisare.
     */
    fun orientationThatFits(spec: LayoutSpec): PageOrientation? {
        if (spec.documentType.physicalSize == null) return null
        return PageOrientation.entries.firstOrNull { o ->
            computePlan(spec.copy(orientation = o, sizing = Sizing.ACTUAL)).appliedScale >= 0.999f
        }
    }

    private fun ceilDiv(a: Int, b: Int): Int = ceil(a.toDouble() / b).toInt()
}
