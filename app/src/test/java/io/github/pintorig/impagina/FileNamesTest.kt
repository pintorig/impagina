// Copyright 2026 pintorig
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun `le etichette dei documenti diventano slug leggibili`() {
        assertEquals("carta-d-identita", FileNames.sanitize("Carta d'identità"))
        assertEquals("tessera-sanitaria", FileNames.sanitize("Tessera sanitaria"))
        assertEquals("passaporto", FileNames.sanitize("Passaporto"))
    }

    @Test
    fun `gli accenti vengono ridotti a lettere semplici`() {
        assertEquals("perche-cosi-piu-citta", FileNames.sanitize("Perché così più città"))
        assertEquals("ancora-un-po", FileNames.sanitize("Àncora un pò"))
    }

    @Test
    fun `i separatori di percorso non sopravvivono`() {
        val slug = FileNames.sanitize("../../etc/passwd")
        assertFalse(slug.contains('/'))
        assertFalse(slug.contains('.'))
        assertEquals("etc-passwd", slug)
    }

    @Test
    fun `i caratteri riservati vengono sostituiti`() {
        val slug = FileNames.sanitize("""a:b*c?d"e<f>g|h\i""")
        assertTrue(slug.all { it in 'a'..'z' || it in '0'..'9' || it == '-' })
    }

    @Test
    fun `i trattini non si accumulano mai`() {
        assertEquals("a-b", FileNames.sanitize("a   ---   b"))
        assertFalse(FileNames.sanitize("!!!a!!!b!!!").contains("--"))
    }

    @Test
    fun `il nome non inizia ne finisce con un trattino`() {
        val slug = FileNames.sanitize("   ...ciao...   ")
        assertEquals("ciao", slug)
    }

    @Test
    fun `un testo senza caratteri utili ricade su un nome generico`() {
        assertEquals("documento", FileNames.sanitize(""))
        assertEquals("documento", FileNames.sanitize("   "))
        assertEquals("documento", FileNames.sanitize("!!!???"))
        assertEquals("documento", FileNames.sanitize("🇮🇹📄"))
    }

    @Test
    fun `i nomi lunghissimi vengono accorciati`() {
        val slug = FileNames.sanitize("parola ".repeat(40))
        assertTrue("lungo ${slug.length}", slug.length <= FileNames.MAX_BASE_LENGTH)
        assertFalse(slug.endsWith("-"))
    }

    @Test
    fun `le cifre restano intatte`() {
        assertEquals("pratica-2026-2027", FileNames.sanitize("Pratica 2026/2027"))
    }

    @Test
    fun `il nome completo porta l'estensione del formato`() {
        assertEquals(
            "carta-d-identita-A4.pdf",
            FileNames.forDocument("Carta d'identità", OutputFormat.PDF)
        )
        assertEquals(
            "passaporto-A4.jpg",
            FileNames.forDocument("Passaporto", OutputFormat.JPEG)
        )
        assertEquals(
            "passaporto-A4.webp",
            FileNames.forDocument("Passaporto", OutputFormat.WEBP)
        )
    }

    @Test
    fun `ogni tipo di documento produce un nome valido`() {
        DocumentType.entries.forEach { type ->
            OutputFormat.entries.forEach { format ->
                val name = FileNames.forDocument(type.label, format)
                assertFalse("separatore in $name", name.contains('/'))
                assertFalse("separatore in $name", name.contains('\\'))
                assertTrue("estensione mancante in $name", name.endsWith(".${format.extension}"))
                assertTrue("nome vuoto", name.length > format.extension.length + 1)
            }
        }
    }
}
