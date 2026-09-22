// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Una facciata del documento: miniatura e comandi.
 *
 * L'etichetta è legata alla posizione, non al contenuto: spostando una foto dal
 * secondo al primo posto quella foto diventa il «Fronte». Chi riordina lo fa
 * proprio per correggere l'ordine di scansione.
 */
@Composable
fun SchedaFacciata(
    etichetta: String,
    bitmap: Bitmap?,
    proporzione: Float,
    modifier: Modifier = Modifier,
    onScatta: () -> Unit,
    onScegli: () -> Unit,
    onRuota: () -> Unit,
    onTogli: () -> Unit,
    indietroPossibile: Boolean = false,
    avantiPossibile: Boolean = false,
    onIndietro: () -> Unit = {},
    onAvanti: () -> Unit = {}
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(Spazi.fra),
            verticalArrangement = Arrangement.spacedBy(Spazi.stretto)
        ) {
            Text(
                etichetta,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(proporzione)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (bitmap != null) Ink.Paper else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = etichetta,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        AppIcons.Fotocamera,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bitmap == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spazi.stretto)) {
                    TextButton(
                        onClick = onScatta,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("Scatta", maxLines = 1) }
                    TextButton(
                        onClick = onScegli,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("File", maxLines = 1) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRuota, modifier = Modifier.size(36.dp)) {
                        Icon(AppIcons.Ruota, contentDescription = "Ruota di 90 gradi")
                    }
                    IconButton(onClick = onTogli, modifier = Modifier.size(36.dp)) {
                        Icon(AppIcons.Cestino, contentDescription = "Togli questa facciata")
                    }
                    Box(Modifier.weight(1f))
                    // Frecce di posizione e non trascinamento: le schede stanno in
                    // una colonna scorrevole, dove un drag dopo long-press
                    // litigherebbe con lo scroll.
                    IconButton(
                        onClick = onIndietro,
                        enabled = indietroPossibile,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(AppIcons.FrecciaSinistra, contentDescription = "Sposta indietro")
                    }
                    IconButton(
                        onClick = onAvanti,
                        enabled = avantiPossibile,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(AppIcons.FrecciaDestra, contentDescription = "Sposta avanti")
                    }
                }
            }
        }
    }
}
