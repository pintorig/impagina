package io.github.pintorig.impagina

/**
 * Riordino delle facciate.
 *
 * Le etichette restano legate alla **posizione**, non al contenuto: spostando
 * una foto dal secondo al primo slot, quella foto diventa il "Fronte". È la
 * semantica giusta qui, perché chi riordina lo fa proprio per correggere
 * l'ordine in cui ha scansionato.
 *
 * Generico e senza dipendenze da Android: si riordina la lista dei bitmap, ma
 * la logica si verifica su liste di stringhe.
 */
object Reorder {

    /**
     * Sposta l'elemento da `from` a `to`, facendo scorrere gli altri.
     *
     * Con indici adiacenti equivale a uno scambio, che è il caso d'uso delle
     * frecce. Indici fuori range lasciano la lista intatta invece di sollevare
     * un'eccezione: il chiamante è una UI, e un tocco su un pulsante che non
     * doveva essere attivo non deve far crollare nulla.
     */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val out = list.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    /** Scambia due posizioni. Per elementi non adiacenti non è come [move]. */
    fun <T> swap(list: List<T>, a: Int, b: Int): List<T> {
        if (a !in list.indices || b !in list.indices || a == b) return list
        val out = list.toMutableList()
        val tmp = out[a]
        out[a] = out[b]
        out[b] = tmp
        return out
    }

    /** Vero se l'elemento può arretrare di una posizione. */
    fun canMoveBack(index: Int): Boolean = index > 0

    /** Vero se l'elemento può avanzare di una posizione. */
    fun <T> canMoveForward(list: List<T>, index: Int): Boolean = index < list.lastIndex
}
