// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Il criterio: **il foglio è l'unica carta, tutto il resto è la scrivania.**
 *
 * Nessun colore è inventato. Il blu è quello che l'app ha già nell'icona
 * (`ic_launcher_background`), che è a sua volta il blu della CIE; l'azzurro
 * chiaro viene dalla tessera sanitaria e il rosso dal bordeaux del passaporto
 * europeo. Sono i documenti che questa app impagina.
 *
 * `Paper` sta fuori dallo schema di proposito: il foglio resta bianco anche di
 * notte, perché è carta, non una superficie dell'interfaccia.
 */
object Ink {
    val Ink = Color(0xFF143A52)          // inchiostro: testo e azioni primarie
    val InkLight = Color(0xFF8EC3DE)     // lo stesso blu, per fondo scuro
    val Azure = Color(0xFFD3E2EC)        // tessera sanitaria
    val AzureDeep = Color(0xFF0C2433)
    val Desk = Color(0xFFD8DDE3)         // la scrivania, fredda: non è crema
    val DeskDark = Color(0xFF0E1418)
    val Panel = Color(0xFFF2F5F8)
    val PanelDark = Color(0xFF16202A)
    val Edge = Color(0xFFCBD2D8)
    val EdgeDark = Color(0xFF2C3A47)
    val Stamp = Color(0xFF7B2D3B)        // bordeaux del passaporto: un solo accento
    val StampLight = Color(0xFFE7B4BB)
    val StampField = Color(0xFFF6E3E6)
    val StampFieldDark = Color(0xFF45161F)

    /** Il colore della carta. Identico nei due temi: la carta è bianca. */
    val Paper = Color(0xFFFFFFFF)
    val PaperEdge = Color(0xFFD6D9DE)

    /** Scritte sul foglio vuoto: piu' scuro del bordo, o non si legge. */
    val PaperInk = Color(0xFF8A96A0)
}

private val Chiaro = lightColorScheme(
    primary = Ink.Ink,
    onPrimary = Color.White,
    primaryContainer = Ink.Azure,
    onPrimaryContainer = Ink.AzureDeep,
    secondary = Color(0xFF4A6B80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6ED),
    onSecondaryContainer = Ink.AzureDeep,
    background = Ink.Desk,
    onBackground = Color(0xFF131A20),
    surface = Ink.Panel,
    onSurface = Color(0xFF131A20),
    surfaceVariant = Color(0xFFDDE2E7),
    onSurfaceVariant = Color(0xFF47535D),
    outline = Ink.Edge,
    outlineVariant = Color(0xFFDDE2E7),
    error = Ink.Stamp,
    onError = Color.White,
    errorContainer = Ink.StampField,
    onErrorContainer = Color(0xFF4A1620)
)

private val Scuro = darkColorScheme(
    primary = Ink.InkLight,
    onPrimary = Color(0xFF04283C),
    primaryContainer = Color(0xFF1E4A64),
    onPrimaryContainer = Ink.Azure,
    secondary = Color(0xFFA9C3D4),
    onSecondary = Color(0xFF12303F),
    secondaryContainer = Color(0xFF244152),
    onSecondaryContainer = Color(0xFFD5E4EE),
    background = Ink.DeskDark,
    onBackground = Color(0xFFE2E8ED),
    surface = Ink.PanelDark,
    onSurface = Color(0xFFE2E8ED),
    surfaceVariant = Color(0xFF243039),
    onSurfaceVariant = Color(0xFFB4C1CB),
    outline = Ink.EdgeDark,
    outlineVariant = Color(0xFF243039),
    error = Ink.StampLight,
    onError = Color(0xFF45161F),
    errorContainer = Ink.StampFieldDark,
    onErrorContainer = Ink.StampLight
)

/**
 * Scala tipografica stretta: tre gradini appena, perché la schermata è fatta di
 * titoli di sezione, valori e note. Il carattere resta quello di sistema —
 * aggiungerne uno significherebbe una dipendenza o mezzo megabyte nell'APK, e
 * questo progetto non ha dipendenze oltre Compose e lo scanner.
 */
private val Caratteri = Typography().run {
    copy(
        titleMedium = titleMedium.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        ),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        // Le note sotto i controlli sono il testo più presente della schermata:
        // hanno bisogno di respiro, non di essere schiacciate.
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium)
    )
}

/** Misure ricorrenti, in un posto solo. */
object Spazi {
    val bordo = 16.dp
    val fra = 12.dp
    val stretto = 8.dp
    val sezione = 20.dp
}

@Composable
fun ImpaginaTheme(
    scuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (scuro) Scuro else Chiaro,
        typography = Caratteri,
        content = content
    )
}
