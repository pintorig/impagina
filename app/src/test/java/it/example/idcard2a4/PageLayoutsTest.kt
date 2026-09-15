package it.example.idcard2a4

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
                arrangement = Arrangement.SIDE_BY_SIDE,
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
            arrangement = Arrangement.SIDE_BY_SIDE,
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
                arrangement = Arrangement.SIDE_BY_SIDE,
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
}
