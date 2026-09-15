# Documento → A4

App Android che scansiona le facciate di un documento — carta d'identità,
patente, tessera sanitaria, passaporto — e le impagina su **un unico foglio A4**,
pronto da stampare o allegare.

Tutta l'elaborazione avviene sul dispositivo. Nessuna immagine lascia il telefono,
nessun permesso runtime richiesto.

## Funzionalità

- Acquisizione da fotocamera con ritaglio automatico dei bordi (scanner ML Kit)
- Import da galleria o file manager: JPEG, PNG, HEIC, PDF
- Rotazione a 90° per raddrizzare uno scatto storto
- Filtro di resa: colore, scala di grigi, alto contrasto
- Anteprima fedele: mostra il PDF davvero generato, non una simulazione
- Salvataggio dove vuoi tramite Storage Access Framework

### Resa dell'immagine

| Filtro | Cosa fa | Quando |
|---|---|---|
| Colore | nessuna alterazione | l'ente chiede la copia a colori |
| Grigi | sola desaturazione | si vuole il bianco e nero senza toccare i toni |
| Contrasto | auto-livelli sui grigi | resa da fotocopia, file più leggero |

### Documenti e layout

| Documento | Formato | Facciate |
|---|---|---|
| Carta d'identità | ID-1, 85,60 × 53,98 mm | Fronte, Retro |
| Patente | ID-1 | Fronte, Retro |
| Tessera sanitaria | ID-1 | Fronte (codice fiscale), Retro (TEAM) |
| Passaporto | ID-3, 125 × 88 mm | Pagina dati, Pagina firma |
| Altro documento | non nota | Fronte, Retro |

Il layout di destinazione si compone di quattro scelte indipendenti:

- **Dimensione** — reale 1:1 oppure adattata al foglio
- **Disposizione** — in colonna o affiancate
- **Orientamento** — foglio verticale od orizzontale
- **Didascalie** — etichetta sotto ogni facciata, utile per il passaporto

Non tutte le combinazioni entrano in A4 a dimensione reale. Due pagine di
passaporto affiancate su foglio verticale occuperebbero 250 mm in larghezza
contro i 185 disponibili: in quel caso l'app riduce l'intero blocco in modo
uniforme al 71%, lo dichiara, e propone il passaggio al foglio orizzontale dove
invece la stampa 1:1 è possibile. Mai un ritaglio, mai una deformazione.

## Requisiti

| | |
|---|---|
| Android minimo | 9.0 (API 28) |
| JDK | 17 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.9.1 |
| Google Play Services | necessario per lo scanner da fotocamera |

## Avvio rapido

```bash
git clone https://github.com/<utente>/documento-a4.git
cd documento-a4
```

Il repository non include `gradle/wrapper/gradle-wrapper.jar`, che è un binario.
Generalo una volta sola:

```bash
gradle wrapper --gradle-version 8.11.1
```

Oppure apri semplicemente la cartella in Android Studio: il wrapper viene creato
al primo sync. Poi:

```bash
./gradlew assembleDebug
./gradlew installDebug      # con un dispositivo collegato
```

Una volta generato, **committa il wrapper** (`gradlew`, `gradlew.bat`,
`gradle/wrapper/`): è la prassi consigliata, garantisce che tutti compilino con
la stessa versione di Gradle.

## Struttura

```
documento-a4/
├── .github/workflows/build.yml     CI: compila l'APK debug e lo pubblica come artifact
├── gradle/libs.versions.toml       version catalog, tutte le versioni in un posto
├── settings.gradle.kts
├── build.gradle.kts
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/it/example/idcard2a4/
        │   ├── DocumentFormats.kt  formati, specifica e matematica dei layout
        │   ├── ImageFilters.kt     curva tonale e applicazione dei filtri
        │   ├── InputLoader.kt      foto e PDF → bitmap
        │   ├── PdfPageComposer.kt  disegno della pagina A4
        │   └── MainActivity.kt     UI Compose, picker, scanner, salvataggio
        └── res/
```

`DocumentFormats.kt` e l'oggetto `ToneMapping` sono **Kotlin puro**: niente
`RectF`, niente `Bitmap`, niente `Context`. I rettangoli sono una `data class Box`
scritta apposta, e la curva tonale lavora su un `IntArray` di 256 elementi.
Sembra una scomodità, ma è ciò che permette di testare geometria e resa su JVM in
pochi millisecondi, senza emulatore né Robolectric:

```bash
./gradlew test
```

## Come funziona

Niente librerie PDF esterne: bastano `android.graphics.pdf.PdfRenderer` per
leggere i PDF in ingresso e `android.graphics.pdf.PdfDocument` per scrivere quello
in uscita.

**La pagina si crea in punti, non in pixel.** A4 = 595 × 842 punti PostScript.
Sembra una risoluzione ridicola, ma il backend Skia incorpora il bitmap come
immagine e gli applica solo una trasformazione: i pixel originali della foto
arrivano intatti nel PDF. Una pagina "a 300 dpi" (2480 × 3508 punti) produrrebbe
un foglio grande come un manifesto.

**La dimensione reale è il default.** ID-1 è 85,60 × 53,98 mm, ID-3 è
125 × 88 mm (ISO/IEC 7810). Stampato 1:1, il risultato è indistinguibile da una
fotocopia, che è quello che gli uffici si aspettano.

**Niente binarizzazione.** La scelta ovvia per un filtro bianco/nero sarebbe la
soglia: ogni pixel diventa 0 o 255. Su un documento d'identità è una pessima
idea — cancella la fotografia del volto, gli ologrammi e i microtesti di
sicurezza, e il risultato *sembra* un documento alterato. Il filtro «Contrasto»
fa invece un'equalizzazione dei livelli in scala di grigi: lo sfondo va a bianco
pieno, il testo si scurisce, ma fra i due restano oltre cento livelli intermedi.

**Il punto di bianco si aggancia al picco della carta, non a un percentile.**
Su una scansione lo sfondo è un modo largo e basso dell'istogramma: prendere il
90° percentile cade *dentro* quel modo e lascia la carta a un grigio chiaro.
`paperWhitePoint()` individua il picco nella metà alta dell'istogramma e scende
fino alla sua spalla, così l'intero sfondo si appiattisce a 255. È anche da qui
che viene l'alleggerimento del file: uno sfondo uniforme si comprime molto
meglio di uno screziato.

**Quando si riduce, si riduce tutto.** Se il blocco non entra, scalano anche i
vuoti tra le facciate, non solo le facciate. Scalare le sole immagini lasciando
i margini fissi è l'errore classico: il blocco ridotto sborda comunque.

## Due trappole che costano un pomeriggio

`ImageDecoder` restituisce bitmap **hardware** per default, e un bitmap hardware
non può essere disegnato sul Canvas di un `PdfDocument`: l'app crasha al momento
della generazione. Va forzato `ALLOCATOR_SOFTWARE`.

`PdfRenderer` pretende un file descriptor **seekable**. I provider cloud (Drive,
allegati mail) spesso non lo garantiscono e ottieni un `IllegalArgumentException`
sporadico e difficile da riprodurre. Il PDF in ingresso viene quindi copiato in
cache prima di essere aperto.

## Roadmap

- [x] Più tipi di documento con layout dedicati
- [x] Test unitari sulla geometria
- [x] Pulizia di `cacheDir` in `onDestroy()`
- [x] Filtro bianco/nero con curva di contrasto
- [ ] Filigrana "copia conforme ad uso …", richiesta da molti enti
- [ ] Ricompressione JPEG a qualità configurabile
- [ ] Layout multipagina per documenti oltre quattro facciate
- [ ] Preset personalizzati salvabili dall'utente

## Privacy

L'app tratta documenti di identità, che sotto GDPR sono dati personali trattati in
un contesto delicato. Il codice attuale non fa uscire nulla dal dispositivo e non
dichiara permessi. Se in futuro aggiungi backup automatico, analytics o crash
reporting con screenshot, quelle immagini finiscono fuori dal telefono e ti porti
dietro obblighi seri: valutalo prima di introdurre la dipendenza, non dopo.

`android:allowBackup` è impostato a `false` proprio per questo.

## Licenza

MIT — vedi [LICENSE](LICENSE).
