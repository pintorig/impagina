// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0

package io.github.pintorig.impagina.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.pintorig.impagina.PagePlan

/**
 * Il foglio, che è insieme l'anteprima e la superficie di lavoro.
 *
 * Gli slot vuoti non vengono disegnati dal renderer — che dipinge solo le
 * facciate acquisite — quindi li mostra questo strato, con i comandi proprio
 * dove finirà il documento. Così il template si vede subito e non serve una
 * striscia di schede sotto a rubare spazio.
 */
@Composable
fun AnteprimaFoglio(
    anteprima: Bitmap?,
    piano: PagePlan,
    pagina: Int,
    scatti: List<Bitmap?>,
    onPagina: (Int) -> Unit,
    onScatta: (Int) -> Unit,
    onScegli: (Int) -> Unit,
    onRuota: (Int) -> Unit,
    onTogli: (Int) -> Unit,
    onSposta: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val layout = piano.pages.getOrNull(pagina) ?: piano.first

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
            // spazio alto e stretto il foglio sborda. Si sceglie quale lato comanda.
            val proporzione = layout.pageWidthPt / layout.pageHeightPt
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
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Un punto PostScript quanti dp vale su questo foglio.
                    val scala = maxWidth / layout.pageWidthPt

                    anteprima?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Anteprima del foglio A4",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    layout.slots.forEachIndexed { posizione, slot ->
                        val indice = layout.slotIndices.getOrNull(posizione) ?: return@forEachIndexed
                        Slot(
                            etichetta = layout.labels.getOrNull(posizione).orEmpty(),
                            pieno = scatti.getOrNull(indice) != null,
                            larghezza = scala * slot.width,
                            altezza = scala * slot.height,
                            spostaX = scala * slot.left,
                            spostaY = scala * slot.top,
                            indietroPossibile = indice > 0,
                            avantiPossibile = indice < scatti.size - 1,
                            onScatta = { onScatta(indice) },
                            onScegli = { onScegli(indice) },
                            onRuota = { onRuota(indice) },
                            onTogli = { onTogli(indice) },
                            onIndietro = { onSposta(indice, indice - 1) },
                            onAvanti = { onSposta(indice, indice + 1) }
                        )
                    }
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
 * Uno slot sul foglio. Vuoto mostra i comandi per riempirlo; pieno apre le
 * azioni sulla facciata. In entrambi i casi il tocco cade dove sta il
 * documento, che è il punto di tutta questa schermata.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Slot(
    etichetta: String,
    pieno: Boolean,
    larghezza: androidx.compose.ui.unit.Dp,
    altezza: androidx.compose.ui.unit.Dp,
    spostaX: androidx.compose.ui.unit.Dp,
    spostaY: androidx.compose.ui.unit.Dp,
    indietroPossibile: Boolean,
    avantiPossibile: Boolean,
    onScatta: () -> Unit,
    onScegli: () -> Unit,
    onRuota: () -> Unit,
    onTogli: () -> Unit,
    onIndietro: () -> Unit,
    onAvanti: () -> Unit
) {
    var menuAperto by remember { mutableIntStateOf(0) }
    val tratteggio = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
    val bordo = Ink.PaperInk
    // Sotto una certa misura non c'è posto per i pulsanti: si tocca lo slot.
    val spazioPerPulsanti = altezza >= 78.dp && larghezza >= 104.dp

    Box(
        Modifier
            .offset(x = spostaX, y = spostaY)
            .size(larghezza, altezza)
            .then(
                if (pieno) Modifier
                else Modifier.drawBehind {
                    drawRoundRect(
                        color = bordo,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 3f, pathEffect = tratteggio)
                    )
                }
            )
            .clickable { menuAperto = 1 },
        contentAlignment = Alignment.Center
    ) {
        if (!pieno) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    etichetta,
                    style = MaterialTheme.typography.labelSmall,
                    color = bordo,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (spazioPerPulsanti) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spazi.stretto)) {
                        IconButton(onClick = onScatta, modifier = Modifier.size(40.dp)) {
                            Icon(
                                AppIcons.Fotocamera,
                                contentDescription = "Scatta $etichetta",
                                tint = bordo
                            )
                        }
                        IconButton(onClick = onScegli, modifier = Modifier.size(40.dp)) {
                            Icon(
                                AppIcons.Documento,
                                contentDescription = "Scegli un file per $etichetta",
                                tint = bordo
                            )
                        }
                    }
                } else {
                    Icon(
                        AppIcons.Piu,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = bordo
                    )
                }
            }
        }

        DropdownMenu(expanded = menuAperto == 1, onDismissRequest = { menuAperto = 0 }) {
            if (pieno) {
                DropdownMenuItem(
                    text = { Text("Ruota") },
                    onClick = { menuAperto = 0; onRuota() },
                    leadingIcon = { Icon(AppIcons.Ruota, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Togli") },
                    onClick = { menuAperto = 0; onTogli() },
                    leadingIcon = { Icon(AppIcons.Cestino, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Sposta indietro") },
                    enabled = indietroPossibile,
                    onClick = { menuAperto = 0; onIndietro() },
                    leadingIcon = { Icon(AppIcons.FrecciaSinistra, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Sposta avanti") },
                    enabled = avantiPossibile,
                    onClick = { menuAperto = 0; onAvanti() },
                    leadingIcon = { Icon(AppIcons.FrecciaDestra, contentDescription = null) }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Scatta") },
                    onClick = { menuAperto = 0; onScatta() },
                    leadingIcon = { Icon(AppIcons.Fotocamera, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Scegli un file") },
                    onClick = { menuAperto = 0; onScegli() },
                    leadingIcon = { Icon(AppIcons.Documento, contentDescription = null) }
                )
            }
        }
    }
}
