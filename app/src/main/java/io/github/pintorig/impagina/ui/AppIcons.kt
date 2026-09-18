// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Le icone dell'app, disegnate qui.
 *
 * Material 3 1.4.0 non porta più con sé `material-icons-core`, e
 * `material-icons-extended` sono parecchi megabyte per una manciata di glifi.
 * Il progetto non ha dipendenze oltre Compose e lo scanner, e non è il caso di
 * aggiungerne una per disegnare una freccia: sono una decina di percorsi.
 *
 * Griglia 24×24, come le icone di sistema, così le dimensioni standard di
 * Material continuano a valere.
 */
object AppIcons {

    private fun icona(nome: String, blocco: ImageVector.Builder.() -> ImageVector.Builder) =
        ImageVector.Builder(
            name = nome,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).blocco().build()

    /** I glifi a tratto condividono spessore e terminazioni: è ciò che li fa
     *  sembrare una famiglia sola invece di dieci disegni diversi. */
    private fun ImageVector.Builder.tratto(blocco: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = blocco
        )

    private fun ImageVector.Builder.pieno(
        riempimento: PathFillType = PathFillType.NonZero,
        blocco: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) = path(fill = SolidColor(Color.Black), pathFillType = riempimento, pathBuilder = blocco)

    val Piu: ImageVector = icona("Piu") {
        pieno {
            moveTo(11f, 5f); lineTo(13f, 5f); lineTo(13f, 11f); lineTo(19f, 11f)
            lineTo(19f, 13f); lineTo(13f, 13f); lineTo(13f, 19f); lineTo(11f, 19f)
            lineTo(11f, 13f); lineTo(5f, 13f); lineTo(5f, 11f); lineTo(11f, 11f); close()
        }
    }

    val Meno: ImageVector = icona("Meno") {
        pieno {
            moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 13f); lineTo(5f, 13f); close()
        }
    }

    /** Fotocamera: corpo con il gradino del mirino, obiettivo come foro. */
    val Fotocamera: ImageVector = icona("Fotocamera") {
        pieno(PathFillType.EvenOdd) {
            moveTo(9f, 3f); lineTo(15f, 3f); lineTo(16.8f, 5f); lineTo(20f, 5f)
            lineTo(22f, 7f); lineTo(22f, 19f); lineTo(20f, 21f); lineTo(4f, 21f)
            lineTo(2f, 19f); lineTo(2f, 7f); lineTo(4f, 5f); lineTo(7.2f, 5f); close()
            moveTo(15.5f, 13f)
            arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 13f)
            arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 13f)
            close()
        }
    }

    /** Documento con l'angolo ripiegato, anch'esso ottenuto come foro. */
    val Documento: ImageVector = icona("Documento") {
        pieno(PathFillType.EvenOdd) {
            moveTo(5f, 2f); lineTo(14f, 2f); lineTo(19f, 7f); lineTo(19f, 22f)
            lineTo(5f, 22f); close()
            moveTo(14f, 3.6f); lineTo(14f, 7f); lineTo(17.4f, 7f); close()
        }
    }

    /** Ruota: tre quarti di giro e la punta che indica il verso, orario. */
    val Ruota: ImageVector = icona("Ruota") {
        tratto {
            moveTo(19f, 12f)
            arcTo(7f, 7f, 0f, true, false, 12f, 5f)
        }
        pieno {
            moveTo(12f, 2.6f); lineTo(12f, 7.4f); lineTo(15.6f, 5f); close()
        }
    }

    val Cestino: ImageVector = icona("Cestino") {
        pieno {
            moveTo(9f, 2f); lineTo(15f, 2f); lineTo(16f, 4f); lineTo(20f, 4f)
            lineTo(20f, 6f); lineTo(4f, 6f); lineTo(4f, 4f); lineTo(8f, 4f); close()
            moveTo(6f, 8f); lineTo(18f, 8f); lineTo(16.8f, 22f); lineTo(7.2f, 22f); close()
        }
    }

    /** Condividi: i tre nodi dell'icona di sistema Android. */
    val Condividi: ImageVector = icona("Condividi") {
        tratto {
            moveTo(8.2f, 10.8f); lineTo(15.8f, 6.6f)
            moveTo(8.2f, 13.2f); lineTo(15.8f, 17.4f)
        }
        pieno {
            moveTo(20.2f, 5.4f)
            arcTo(2.4f, 2.4f, 0f, true, true, 15.4f, 5.4f)
            arcTo(2.4f, 2.4f, 0f, true, true, 20.2f, 5.4f); close()
            moveTo(8.4f, 12f)
            arcTo(2.4f, 2.4f, 0f, true, true, 3.6f, 12f)
            arcTo(2.4f, 2.4f, 0f, true, true, 8.4f, 12f); close()
            moveTo(20.2f, 18.6f)
            arcTo(2.4f, 2.4f, 0f, true, true, 15.4f, 18.6f)
            arcTo(2.4f, 2.4f, 0f, true, true, 20.2f, 18.6f); close()
        }
    }

    val Chiudi: ImageVector = icona("Chiudi") {
        tratto {
            moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
            moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
        }
    }

    val FrecciaGiu: ImageVector = icona("FrecciaGiu") {
        tratto { moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f) }
    }

    val FrecciaSinistra: ImageVector = icona("FrecciaSinistra") {
        tratto { moveTo(14.5f, 6.5f); lineTo(9f, 12f); lineTo(14.5f, 17.5f) }
    }

    val FrecciaDestra: ImageVector = icona("FrecciaDestra") {
        tratto { moveTo(9.5f, 6.5f); lineTo(15f, 12f); lineTo(9.5f, 17.5f) }
    }
}
