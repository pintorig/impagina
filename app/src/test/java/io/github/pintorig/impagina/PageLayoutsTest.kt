// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La matematica del layout è Kotlin puro (niente RectF, niente Context),
 * quindi gira su JVM senza emulatore né Robolectric.
 */
class PageLayoutsTest {

    private val tol = 0.5f   // mezzo punto: ~0,18 mm

    @Test
    fun `ID-1 a dimensione reale misura 85 virgola 6 per 53 virgola 98 mm`() {
        val layout = PageLayouts.compute(
            LayoutSpec(documentType = DocumentType.CARTA_IDENTITA, sizing = Sizing.ACTUAL)
        )
        val slot = layout.slots.first()
        assertEquals(85.60f * PageLayouts.MM_TO_PT, slot.width, tol)
        assertEquals(53.98f * PageLayouts.MM_TO_PT, slot.height, tol)
        assertEquals(1f, layout.appliedScale, 0.001f)
        assertFalse(layout.isScaledDown)
    }

    @Test
    fun `il blocco e orizzontalmente centrato sul foglio`() {
        val layout = PageLayouts.compute(LayoutSpec(documentType = DocumentType.PATENTE))
        layout.slots.forEach {
            assertEquals(layout.pageWidthPt / 2f, it.centerX, tol)
        }
    }

    @Test
    fun `le facciate in colonna non si sovrappongono e restano nei margini`() {
        val layout = PageLayouts.compute(
            LayoutSpec(documentType = DocumentType.TESSERA_SANITARIA, showLabels = true)
        )
        val (first, second) = layout.slots
        assertTrue(second.top >= first.bottom + layout.labelHeightPt)
        assertTrue(first.top >= PageLayouts.MARGIN_PT - tol)
        assertTrue(second.bottom <= layout.pageHeightPt - PageLayouts.MARGIN_PT + tol)
    }

    @Test
    fun `due pagine di passaporto affiancate non entrano in verticale`() {
        val layout = PageLayouts.compute(
            LayoutSpec(
                documentType = DocumentType.PASSAPORTO,
                arrangement = GridArrangement.SIDE_BY_SIDE,
                orientation = PageOrientation.PORTRAIT
            )
        )
        assertTrue(layout.isScaledDown)
        assertTrue(layout.appliedScale < 0.8f)
    }

    @Test
    fun `ruotando il foglio le stesse pagine entrano a dimensione reale`() {
        val spec = LayoutSpec(
            documentType = DocumentType.PASSAPORTO,
            arrangement = GridArrangement.SIDE_BY_SIDE,
            orientation = PageOrientation.PORTRAIT
        )
        val fix = PageLayouts.orientationThatFits(spec)
        assertEquals(PageOrientation.LANDSCAPE, fix)

        val fixed = PageLayouts.compute(spec.copy(orientation = fix!!))
        assertFalse(fixed.isScaledDown)
    }

    @Test
    fun `riducendo il blocco resta comunque dentro area utile`() {
        val layout = PageLayouts.compute(
            LayoutSpec(
                documentType = DocumentType.PASSAPORTO,
                arrangement = GridArrangement.SIDE_BY_SIDE,
                orientation = PageOrientation.PORTRAIT
            )
        )
        val left = layout.slots.minOf { it.left }
        val right = layout.slots.maxOf { it.right }
        assertTrue(left >= PageLayouts.MARGIN_PT - tol)
        assertTrue(right <= layout.pageWidthPt - PageLayouts.MARGIN_PT + tol)
    }

    @Test
    fun `un documento senza dimensione nota ricade sull'adattamento`() {
        val layout = PageLayouts.compute(
            LayoutSpec(documentType = DocumentType.ALTRO, sizing = Sizing.ACTUAL)
        )
        assertEquals(Sizing.FIT, layout.effectiveSizing)
        assertFalse(layout.isScaledDown)
    }

    @Test
    fun `in modalita adattata le celle riempiono l'area utile`() {
        val layout = PageLayouts.compute(
            LayoutSpec(documentType = DocumentType.CARTA_IDENTITA, sizing = Sizing.FIT)
        )
        val usableW = layout.pageWidthPt - 2 * PageLayouts.MARGIN_PT
        assertEquals(usableW, layout.slots.first().width, tol)
    }

    @Test
    fun `le etichette mancanti vengono completate`() {
        val labels = PageLayouts.labelsFor(DocumentType.PASSAPORTO, 4)
        assertEquals(4, labels.size)
        assertEquals("Pagina dati", labels[0])
        assertEquals("Pagina 4", labels[3])
    }

    @Test
    fun `la filigrana in fondo lascia libera la fascia riservata`() {
        val layout = PageLayouts.compute(
            LayoutSpec(
                documentType = DocumentType.CARTA_IDENTITA,
                watermark = Watermark("USO INTERNO", WatermarkStyle.BELOW)
            )
        )
        val limit = layout.pageHeightPt - PageLayouts.MARGIN_PT - PageLayouts.WATERMARK_BAND_PT
        assertTrue(layout.slots.maxOf { it.bottom } <= limit + tol)
        assertEquals(PageLayouts.WATERMARK_BAND_PT, layout.reservedBottomPt, tol)
    }

    @Test
    fun `in modalita adattata la fascia riservata riduce le celle`() {
        val plain = PageLayouts.compute(LayoutSpec(sizing = Sizing.FIT))
        val banded = PageLayouts.compute(
            LayoutSpec(sizing = Sizing.FIT, watermark = Watermark("X", WatermarkStyle.BELOW))
        )
        assertTrue(banded.slots.first().height < plain.slots.first().height)
        assertEquals(
            PageLayouts.WATERMARK_BAND_PT / 2f,
            plain.slots.first().height - banded.slots.first().height,
            tol
        )
    }

    @Test
    fun `la filigrana diagonale non sottrae spazio al documento`() {
        val plain = PageLayouts.compute(LayoutSpec(sizing = Sizing.FIT))
        val diagonal = PageLayouts.compute(
            LayoutSpec(sizing = Sizing.FIT, watermark = Watermark("X", WatermarkStyle.DIAGONAL))
        )
        assertEquals(plain.slots.first().height, diagonal.slots.first().height, tol)
    }

    /* --- Multipagina ---------------------------------------------------- */

    private fun cards(n: Int, arrangement: GridArrangement = GridArrangement.STACKED) =
        PageLayouts.computePlan(
            LayoutSpec(
                documentType = DocumentType.CARTA_IDENTITA,
                slotCount = n,
                arrangement = arrangement
            )
        )

    @Test
    fun `due facciate restano su una pagina sola`() {
        val plan = cards(2)
        assertEquals(1, plan.pageCount)
        assertFalse(plan.isMultiPage)
    }

    @Test
    fun `quattro tessere a dimensione reale entrano in un solo foglio`() {
        val plan = cards(4)
        assertEquals(1, plan.pageCount)
        assertEquals(4, plan.rowsPerPage)
        assertFalse(plan.isScaledDown)
    }

    @Test
    fun `sei tessere in colonna si dividono in due fogli`() {
        val plan = cards(6)
        assertEquals(2, plan.pageCount)
        assertEquals(listOf(4, 2), plan.pages.map { it.slots.size })
    }

    @Test
    fun `sei tessere su due colonne stanno in un foglio solo`() {
        val plan = cards(6, GridArrangement.SIDE_BY_SIDE)
        assertEquals(1, plan.pageCount)
        assertEquals(2, plan.columns)
        assertEquals(3, plan.rowsPerPage)
    }

    @Test
    fun `dodici tessere su due colonne danno due fogli da otto e quattro`() {
        val plan = cards(12, GridArrangement.SIDE_BY_SIDE)
        assertEquals(listOf(8, 4), plan.pages.map { it.slots.size })
    }

    @Test
    fun `gli indici globali coprono tutte le facciate senza buchi`() {
        val plan = cards(7)
        val indices = plan.pages.flatMap { it.slotIndices }
        assertEquals((0 until 7).toList(), indices)
    }

    @Test
    fun `la cella ha la stessa dimensione su tutte le pagine`() {
        val plan = cards(6)
        val sizes = plan.pages.flatMap { page ->
            page.slots.map { it.width to it.height }
        }.distinct()
        assertEquals(1, sizes.size)
    }

    @Test
    fun `ogni pagina conosce la propria posizione nel piano`() {
        val plan = cards(6)
        plan.pages.forEachIndexed { i, page ->
            assertEquals(i, page.pageIndex)
            assertEquals(plan.pageCount, page.pageCount)
        }
    }

    @Test
    fun `nessuno slot esce dai margini con etichette e filigrana`() {
        val plan = PageLayouts.computePlan(
            LayoutSpec(
                documentType = DocumentType.CARTA_IDENTITA,
                slotCount = 12,
                arrangement = GridArrangement.SIDE_BY_SIDE,
                showLabels = true,
                watermark = Watermark("USO INTERNO", WatermarkStyle.BELOW)
            )
        )
        plan.pages.forEach { page ->
            val bottomLimit =
                page.pageHeightPt - PageLayouts.MARGIN_PT - PageLayouts.WATERMARK_BAND_PT
            page.slots.forEach {
                assertTrue(it.left >= PageLayouts.MARGIN_PT - tol)
                assertTrue(it.right <= page.pageWidthPt - PageLayouts.MARGIN_PT + tol)
                assertTrue(it.top >= PageLayouts.MARGIN_PT - tol)
                assertTrue(it.bottom + page.labelHeightPt <= bottomLimit + tol)
            }
        }
    }

    @Test
    fun `gli slot di una pagina non si sovrappongono mai`() {
        val plan = cards(8, GridArrangement.SIDE_BY_SIDE)
        plan.pages.forEach { page ->
            page.slots.forEachIndexed { i, a ->
                page.slots.drop(i + 1).forEach { b ->
                    val disjoint = a.right <= b.left + tol || b.right <= a.left + tol ||
                        a.bottom <= b.top + tol || b.bottom <= a.top + tol
                    assertTrue("slot sovrapposti: $a e $b", disjoint)
                }
            }
        }
    }

    @Test
    fun `quattro pagine di passaporto occupano due fogli`() {
        val plan = PageLayouts.computePlan(
            LayoutSpec(documentType = DocumentType.PASSAPORTO, slotCount = 4)
        )
        assertEquals(listOf(2, 2), plan.pages.map { it.slots.size })
        assertFalse(plan.isScaledDown)
    }

    @Test
    fun `in modalita adattata sei facciate si dividono in due fogli`() {
        val plan = PageLayouts.computePlan(
            LayoutSpec(documentType = DocumentType.ALTRO, slotCount = 6)
        )
        assertEquals(listOf(4, 2), plan.pages.map { it.slots.size })
    }

    @Test
    fun `una sola facciata adattata riempie tutta l'altezza utile`() {
        val plan = PageLayouts.computePlan(
            LayoutSpec(documentType = DocumentType.ALTRO, slotCount = 1)
        )
        val usableH = plan.first.pageHeightPt - 2 * PageLayouts.MARGIN_PT
        assertEquals(usableH, plan.first.slots.single().height, tol)
    }

    @Test
    fun `un numero di facciate fuori scala viene riportato nei limiti`() {
        assertEquals(PageLayouts.MAX_SLOTS, cards(99).slotCount)
        assertEquals(1, cards(0).slotCount)
    }
}

