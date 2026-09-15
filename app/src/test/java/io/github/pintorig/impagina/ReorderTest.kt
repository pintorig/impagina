package io.github.pintorig.impagina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderTest {

    private val abc = listOf("A", "B", "C", "D")

    @Test
    fun `spostare avanti di una posizione equivale a scambiare`() {
        assertEquals(listOf("B", "A", "C", "D"), Reorder.move(abc, 0, 1))
    }

    @Test
    fun `spostare indietro di una posizione equivale a scambiare`() {
        assertEquals(listOf("A", "C", "B", "D"), Reorder.move(abc, 2, 1))
    }

    @Test
    fun `spostare di piu posizioni fa scorrere gli altri`() {
        assertEquals(listOf("D", "A", "B", "C"), Reorder.move(abc, 3, 0))
        assertEquals(listOf("B", "C", "D", "A"), Reorder.move(abc, 0, 3))
    }

    @Test
    fun `spostare sulla stessa posizione non cambia nulla`() {
        assertSame(abc, Reorder.move(abc, 2, 2))
    }

    @Test
    fun `indici fuori range lasciano la lista intatta`() {
        assertSame(abc, Reorder.move(abc, -1, 2))
        assertSame(abc, Reorder.move(abc, 1, 9))
        assertSame(abc, Reorder.swap(abc, 0, 4))
    }

    @Test
    fun `una lista vuota non manda in errore il riordino`() {
        val empty = emptyList<String>()
        assertSame(empty, Reorder.move(empty, 0, 0))
        assertSame(empty, Reorder.move(empty, 0, 1))
    }

    @Test
    fun `nessun elemento viene perso o duplicato`() {
        for (from in abc.indices) {
            for (to in abc.indices) {
                val moved = Reorder.move(abc, from, to)
                assertEquals(abc.size, moved.size)
                assertEquals(abc.sorted(), moved.sorted())
            }
        }
    }

    @Test
    fun `lo spostamento e reversibile`() {
        val moved = Reorder.move(abc, 0, 3)
        assertEquals(abc, Reorder.move(moved, 3, 0))
    }

    @Test
    fun `lo scambio agisce solo sulle due posizioni indicate`() {
        assertEquals(listOf("D", "B", "C", "A"), Reorder.swap(abc, 0, 3))
    }

    @Test
    fun `scambio e spostamento differiscono su posizioni non adiacenti`() {
        assertEquals(Reorder.swap(abc, 0, 1), Reorder.move(abc, 0, 1))
        assertTrue(Reorder.swap(abc, 0, 3) != Reorder.move(abc, 0, 3))
    }

    @Test
    fun `lo scambio applicato due volte torna al punto di partenza`() {
        assertEquals(abc, Reorder.swap(Reorder.swap(abc, 1, 3), 1, 3))
    }

    @Test
    fun `le facciate vuote si riordinano come le altre`() {
        val withHoles = listOf("A", null, "C")
        assertEquals(listOf(null, "A", "C"), Reorder.move(withHoles, 0, 1))
    }

    @Test
    fun `gli estremi non possono uscire dalla lista`() {
        assertFalse(Reorder.canMoveBack(0))
        assertTrue(Reorder.canMoveBack(1))
        assertTrue(Reorder.canMoveForward(abc, 2))
        assertFalse(Reorder.canMoveForward(abc, abc.lastIndex))
    }

    @Test
    fun `una lista di un solo elemento non offre spostamenti`() {
        val single = listOf("A")
        assertFalse(Reorder.canMoveBack(0))
        assertFalse(Reorder.canMoveForward(single, 0))
    }

    @Test
    fun `le etichette restano legate alla posizione e non al contenuto`() {
        // la foto in seconda posizione, spostata in prima, diventa il "Fronte"
        val labels = PageLayouts.labelsFor(DocumentType.CARTA_IDENTITA, 2)
        val shots = listOf("retro-scattato-per-primo", "fronte-scattato-per-secondo")
        val fixed = Reorder.swap(shots, 0, 1)

        assertEquals("Fronte", labels[0])
        assertEquals("fronte-scattato-per-secondo", fixed[0])
    }
}
