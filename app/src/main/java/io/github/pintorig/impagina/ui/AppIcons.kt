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
        tratto { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    }

    val Meno: ImageVector = icona("Meno") {
        tratto { moveTo(5f, 12f); lineTo(19f, 12f) }
    }

    /** Fotocamera: corpo con il gradino del mirino e l'obiettivo. */
    val Fotocamera: ImageVector = icona("Fotocamera") {
        tratto {
            moveTo(2.9f, 7.4f); lineTo(7.3f, 7.4f); lineTo(8.9f, 5.1f); lineTo(15.1f, 5.1f)
            lineTo(16.7f, 7.4f); lineTo(21.1f, 7.4f); lineTo(21.1f, 20.1f)
            lineTo(2.9f, 20.1f); close()
            moveTo(15.4f, 13.8f)
            arcTo(3.4f, 3.4f, 0f, true, true, 8.6f, 13.8f)
            arcTo(3.4f, 3.4f, 0f, true, true, 15.4f, 13.8f)
            close()
        }
    }

    /** Documento con l'angolo ripiegato. */
    val Documento: ImageVector = icona("Documento") {
        tratto {
            moveTo(6f, 2.9f); lineTo(13.8f, 2.9f); lineTo(18f, 7.1f); lineTo(18f, 21.1f)
            lineTo(6f, 21.1f); close()
            moveTo(13.8f, 2.9f); lineTo(13.8f, 7.1f); lineTo(18f, 7.1f)
        }
    }

    /** Ruota: tre quarti di giro, con la punta che indica il verso orario. */
    val Ruota: ImageVector = icona("Ruota") {
        tratto {
            moveTo(19f, 12f)
            arcTo(7f, 7f, 0f, true, false, 12f, 5f)
        }
        pieno { moveTo(12f, 2.9f); lineTo(12f, 7.1f); lineTo(15.2f, 5f); close() }
    }

    val Cestino: ImageVector = icona("Cestino") {
        tratto {
            moveTo(5.2f, 6.6f); lineTo(18.8f, 6.6f)
            moveTo(9.6f, 6.6f); lineTo(9.6f, 4.3f); lineTo(14.4f, 4.3f); lineTo(14.4f, 6.6f)
            moveTo(7.1f, 6.6f); lineTo(8f, 20.9f); lineTo(16f, 20.9f); lineTo(16.9f, 6.6f)
        }
    }

    /** Condividi: i tre nodi dell'icona di sistema Android. */
    val Condividi: ImageVector = icona("Condividi") {
        tratto {
            moveTo(8.3f, 10.9f); lineTo(15.7f, 6.7f)
            moveTo(8.3f, 13.1f); lineTo(15.7f, 17.3f)
            moveTo(20.2f, 5.4f)
            arcTo(2.4f, 2.4f, 0f, true, true, 15.4f, 5.4f)
            arcTo(2.4f, 2.4f, 0f, true, true, 20.2f, 5.4f)
            moveTo(8.4f, 12f)
            arcTo(2.4f, 2.4f, 0f, true, true, 3.6f, 12f)
            arcTo(2.4f, 2.4f, 0f, true, true, 8.4f, 12f)
            moveTo(20.2f, 18.6f)
            arcTo(2.4f, 2.4f, 0f, true, true, 15.4f, 18.6f)
            arcTo(2.4f, 2.4f, 0f, true, true, 20.2f, 18.6f)
        }
    }

    /** Opzioni: tre cursori. Le manopole sono vuote e le linee si interrompono,
     *  per restare nella stessa famiglia a tratto di tutte le altre. */
    val Opzioni: ImageVector = icona("Opzioni") {
        tratto {
            moveTo(3f, 7f); lineTo(5.4f, 7f); moveTo(10.6f, 7f); lineTo(21f, 7f)
            moveTo(3f, 12f); lineTo(13.4f, 12f); moveTo(18.6f, 12f); lineTo(21f, 12f)
            moveTo(3f, 17f); lineTo(3.9f, 17f); moveTo(9.1f, 17f); lineTo(21f, 17f)
            moveTo(10.3f, 7f)
            arcTo(2.3f, 2.3f, 0f, true, true, 5.7f, 7f)
            arcTo(2.3f, 2.3f, 0f, true, true, 10.3f, 7f)
            moveTo(18.3f, 12f)
            arcTo(2.3f, 2.3f, 0f, true, true, 13.7f, 12f)
            arcTo(2.3f, 2.3f, 0f, true, true, 18.3f, 12f)
            moveTo(8.8f, 17f)
            arcTo(2.3f, 2.3f, 0f, true, true, 4.2f, 17f)
            arcTo(2.3f, 2.3f, 0f, true, true, 8.8f, 17f)
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
