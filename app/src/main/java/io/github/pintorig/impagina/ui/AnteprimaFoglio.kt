// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.pintorig.impagina.PagePlan

/**
 * Il foglio, che è il protagonista della schermata.
 *
 * Sta in alto e non scorre via: mentre si toccano i controlli si vede cambiare
 * la pagina, che è tutto il senso dell'app. È l'unica superficie di carta
 * dell'interfaccia — bianca anche in tema scuro, con un'ombra vera e un bordo
 * sottile, come un foglio appoggiato sulla scrivania.
 */
@Composable
fun AnteprimaFoglio(
    anteprima: Bitmap?,
    piano: PagePlan,
    pagina: Int,
    onPagina: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(
            Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            // Con fillMaxSize() aspectRatio adatta prima la larghezza, e su uno
            // spazio alto e stretto il foglio sborda in basso finendo sotto la
            // striscia delle facciate. Si sceglie quale lato comanda.
            val proporzione = piano.first.pageWidthPt / piano.first.pageHeightPt
            val comandaAltezza = maxWidth / maxHeight > proporzione
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = Ink.Paper,
                border = BorderStroke(1.dp, Ink.PaperEdge),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(proporzione, matchHeightConstraintsFirst = comandaAltezza)
            ) {
                if (anteprima != null) {
                    Image(
                        bitmap = anteprima.asImageBitmap(),
                        contentDescription = "Anteprima del foglio A4",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    FoglioVuoto()
                }
            }
        }

        if (piano.isMultiPage) {
            Row(
                Modifier.padding(top = Spazi.stretto).wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onPagina(pagina - 1) }, enabled = pagina > 0) {
                    Icon(AppIcons.FrecciaSinistra, contentDescription = "Pagina precedente")
                }
                Text(
                    "Pagina ${pagina + 1} di ${piano.pageCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { onPagina(pagina + 1) },
                    enabled = pagina < piano.pageCount - 1
                ) {
                    Icon(AppIcons.FrecciaDestra, contentDescription = "Pagina successiva")
                }
            }
        }
    }
}

/**
 * Lo stato vuoto dice che cosa fare, non che cosa manca: una casella con
 * scritto «vuoto» non ha mai aiutato nessuno.
 */
@Composable
private fun FoglioVuoto() {
    Column(
        Modifier.fillMaxSize().padding(Spazi.sezione),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            AppIcons.Fotocamera,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Ink.PaperInk
        )
        Text(
            "Scatta il fronte del documento per vedere il foglio",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = Ink.PaperInk,
            modifier = Modifier.padding(top = Spazi.fra)
        )
    }
}
