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
- Riordino delle facciate, con inversione rapida per fronte/retro
- Filtro di resa: colore, scala di grigi, alto contrasto
- Filigrana a testo libero, in fondo al foglio o in diagonale
- Uscita in PDF, JPEG, PNG o WebP, con risoluzione e qualità selezionabili
- Ricerca automatica della qualità massima sotto un tetto di peso
- Da 1 a 12 facciate, impaginate su più fogli quando serve
- Anteprima fedele: mostra il PDF davvero generato, non una simulazione
- Salvataggio dove vuoi tramite Storage Access Framework

### Resa dell'immagine

| Filtro | Cosa fa | Quando |
|---|---|---|
| Colore | nessuna alterazione | l'ente chiede la copia a colori |
| Grigi | sola desaturazione | si vuole il bianco e nero senza toccare i toni |
| Contrasto | auto-livelli sui grigi | resa da fotocopia, file più leggero |

### Riordino

Ogni facciata acquisita ha due frecce `◀ ▶` che la spostano nella sequenza; con
due sole facciate c'è anche un pulsante che le inverte in un tocco, perché
scansionare il retro per primo è l'errore più comune.

**Le etichette restano legate alla posizione, non al contenuto.** Spostando una
foto dal secondo al primo slot, quella foto diventa il "Fronte". È la semantica
giusta qui: chi riordina lo fa proprio per correggere l'ordine in cui ha
scansionato, quindi l'etichetta deve descrivere la destinazione sul foglio.

Niente trascinamento, ed è una scelta: le schede stanno dentro una colonna
scorrevole, dove un drag dopo long-press litiga con lo scroll e produce codice
fragile che non si riesce a testare. Le frecce fanno la stessa cosa, sono
accessibili da lettore di schermo e la logica sottostante è verificabile.

### Formati di uscita

| Formato | Quando |
|---|---|
| PDF | stampa e archiviazione; conserva i pixel originali e la scala esatta |
| JPEG | il più accettato dai portali di caricamento |
| PNG | senza perdita; leggerissimo con il filtro Contrasto, pesante a colori |
| WebP | circa un terzo più leggero del JPEG, non sempre accettato |

La risoluzione (150 / 200 / 300 dpi) compare solo per i formati immagine, e non
è una svista: il PDF non ha una densità propria, incorpora le immagini alla loro
dimensione originale e le scala in fase di stampa. A 300 dpi un A4 misura
2479 × 3508 pixel, a 150 dpi 1240 × 1754.

### Qualità e peso

Per JPEG e WebP compare uno slider di qualità da 40 a 100 a passi di 5. Il peso
mostrato sotto i controlli è **reale**, non stimato: viene calcolato comprimendo
davvero la pagina in sottofondo, con un debounce di 400 ms. Quegli stessi byte
vengono poi scritti al salvataggio, quindi il conto non si fa due volte.

La parte utile però sono i tetti di peso. Toccando `≤ 1 MB` l'app cerca la
qualità più alta che ci sta sotto. Il peso di un JPEG cresce in modo monotono con
la qualità, quindi basta una ricerca binaria sulla griglia: la pagina si
rasterizza una volta e si ricomprime quattro volte, invece di tredici rendering
completi. Se nemmeno alla qualità minima si rientra, l'app lo dice e suggerisce
di abbassare la risoluzione, invece di restituire un errore.

### Filigrana

Il testo è libero, con il segnaposto `{data}` sostituito alla generazione. Due
collocazioni:

- **Sotto** — riga in fondo al foglio, in una fascia riservata che sottrae spazio
  al documento invece di sovrapporsi. Non copre nulla, ma si ritaglia via.
- **Diagonale** — scritta obliqua lungo la diagonale del foglio, in grigio al 22%
  di opacità. Attraversa entrambe le facciate: una filigrana che ne copre una
  sola si elimina in un secondo.

I preset proposti sono `USO INTERNO`, `COPIA NON AUTENTICATA` e `Ad uso {data}`.
Nessuno dice «copia conforme all'originale», ed è deliberato: quella è
un'autentica ex art. 18 DPR 445/2000 che solo un pubblico ufficiale può
rilasciare, e offrirla come formula pronta indurrebbe l'utente in errore. Un test
lo verifica, così la regola non si perde in un refactor.

### Documenti e layout

| Documento | Formato | Facciate |
|---|---|---|
| Carta d'identità | ID-1, 85,60 × 53,98 mm | Fronte, Retro |
| Patente | ID-1 | Fronte, Retro |
| Tessera sanitaria | ID-1 | Fronte (codice fiscale), Retro (TEAM) |
| Passaporto | ID-3, 125 × 88 mm | Pagina dati, Pagina firma |
| Altro documento | non nota | Fronte, Retro |

Le facciate vanno da 1 a 12. Il layout di destinazione si compone di quattro
scelte indipendenti:

- **Dimensione** — reale 1:1 oppure adattata al foglio
- **Disposizione** — in colonna o affiancate
- **Orientamento** — foglio verticale od orizzontale
- **Didascalie** — etichetta sotto ogni facciata, utile per il passaporto

### Più fogli

Quando le facciate non stanno in una pagina, il piano ne produce altre. La
capienza non è una costante arbitraria: **a dimensione reale è un fatto fisico**,
cioè quante tessere entrano davvero in un A4. In colonna ne entrano 4, su due
colonne 6 o 8 a seconda di etichette e filigrana. In modalità adattata il limite
è invece una scelta di leggibilità (4 righe), perché lì qualunque numero di celle
"entra" rimpicciolendosi fino a diventare una striscia illeggibile.

La cella mantiene la stessa dimensione su tutte le pagine, anche quando l'ultima
è parziale: due facciate rimaste da sole non si gonfiano a riempire il foglio,
altrimenti la stampa risulterebbe disomogenea. Il blocco parziale viene invece
centrato, così sembra voluto.

**I formati immagine si disabilitano oltre la prima pagina.** Un JPEG non ha
pagine: esportarne uno da un piano di tre fogli ne perderebbe due in silenzio.
L'app disattiva i formati raster, torna al PDF e spiega perché, invece di
produrre un file monco.

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
        │   ├── PageRenderer.kt     disegno della pagina, condiviso da tutti i formati
        │   ├── Reorder.kt          riordino delle facciate
        │   ├── DocumentExporter.kt PDF e rasterizzazione
        │   ├── OutputFormat.kt     formati, risoluzioni, conversione punti/pixel
        │   ├── Watermark.kt        modello della filigrana e gestione del testo
        │   └── MainActivity.kt     UI Compose, picker, scanner, salvataggio
        └── res/
```

`DocumentFormats.kt`, `Watermark.kt` e l'oggetto `ToneMapping` sono **Kotlin
puro**: niente `RectF`, niente `Bitmap`, niente `Context`. I rettangoli sono una
`data class Box` scritta apposta, la curva tonale lavora su un `IntArray` di 256
elementi, e il calcolo del testo riceve la misurazione come funzione — nei test
la si sostituisce con un misuratore finto. Sembra una scomodità, ma è ciò che
permette di verificare geometria, resa e impaginazione del testo su JVM in pochi
millisecondi, senza emulatore né Robolectric:

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

**Il peso non si stima, si misura.** Estrapolare la dimensione di un JPEG da un
provino a bassa risoluzione sbaglia facilmente del 15%, ed è esattamente lo
scarto che fa sforare un limite di caricamento fissato a 2 MB. Comprimere sul
serio costa qualche centinaio di millisecondi, li si spende in sottofondo, e il
numero mostrato è quello che finirà sul disco.

**Un solo punto di disegno.** `PageRenderer.drawPage()` lavora sempre in punti
PostScript e riceve un `Canvas` qualsiasi. Il PDF gli passa il canvas della
pagina; i formati immagine gli passano un canvas su bitmap scalato di `dpi / 72`.
Il JPEG è quindi la rasterizzazione esatta della stessa pagina che finirebbe nel
PDF, e non esiste una seconda implementazione che possa andare fuori sincrono.
Anche l'anteprima passa di lì, a densità bassa.

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

**Il testo della filigrana non si tronca mai.** Se eccede due righe, l'eccedenza
confluisce nell'ultima e poi è il corpo a rimpicciolirsi fino a 6 pt. Tagliare
una frase come «Copia ad uso iscrizione scolastica anno 2026/2027» a metà ne
cambierebbe il senso giuridico, il che è peggio di una riga scritta in piccolo.

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
- [x] Filigrana a testo configurabile
- [x] Uscita in formati immagine oltre al PDF
- [x] Qualità regolabile con peso reale e ricerca sotto tetto
- [x] Layout multipagina oltre le quattro facciate
- [x] Riordino delle facciate
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
