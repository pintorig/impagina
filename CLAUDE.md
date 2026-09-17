# CLAUDE.md

Contesto per Claude Code. Questo file viene letto a ogni sessione: contiene le
decisioni che il codice da solo non spiega, e le trappole già scoperte.

---

## Cos'è

**Impagina** — app Android che acquisisce le facciate di un documento
(carta d'identità, patente, tessera sanitaria, passaporto) e le impagina su fogli
A4, esportabili in PDF, JPEG, PNG o WebP.

Tutto avviene sul dispositivo. **Nessun permesso dichiarato**, nessuna rete,
nessuna dipendenza oltre Compose e lo scanner ML Kit.

Stato: `v1.9.0` (`versionCode` 10), 13 sorgenti, **120 test** su JVM.
Repository: <https://github.com/pintorig/impagina> — Apache-2.0, © 2026 Giuliano
Pintori.

**Il codice è stato compilato per la prima volta solo di recente**, e quella
prima compilazione ha fatto emergere una collisione di nomi con Compose (vedi
«Trappole»). Non escludere che ne restino altre: se qualcosa non torna, il
compilatore ha ragione lui.

---

## Comandi

```bash
./gradlew test              # 120 test, pochi secondi, nessun emulatore
./gradlew lintDebug
./gradlew assembleDebug
./gradlew installDebug      # con un dispositivo collegato
```

**Serve l'Android SDK anche solo per i test unitari**, perché compilano contro
`android.jar`. Se manca, `ANDROID_HOME` non è impostata o `local.properties` non
esiste, ogni task fallisce con «SDK location not found». `local.properties`
contiene un percorso valido solo su questa macchina e non va committato: il
`.gitignore` lo esclude già.

Il wrapper è nel repository: `./gradlew` funziona su un clone pulito, senza
Gradle installato. È fissato alla versione 9.7.1, la stessa che invoca la CI.

**In locale gira JDK 21, in CI JDK 17.** Il progetto produce comunque bytecode 17
(`jvmTarget`), quindi di norma non cambia nulla. Ma se un test passa in locale e
fallisce in pipeline, quello è il primo sospetto: si elimina aggiungendo
`kotlin { jvmToolchain(17) }` a `app/build.gradle.kts`.

---

## Architettura

Il criterio che regge tutto: **la logica sta in Kotlin puro, l'Android è uno
strato sottile sopra.** Niente `RectF`, `Bitmap`, `Paint` o `Context` nei file
puri. È ciò che permette di testare geometria, curva tonale, impaginazione del
testo, serializzazione e nomi file su JVM in pochi secondi, senza emulatore né
Robolectric.

### Kotlin puro — testabile, ~850 righe

| File | Contenuto | Test |
|---|---|---|
| `DocumentFormats.kt` | formati fisici, `LayoutSpec`, pianificazione multipagina | 26 |
| `Preset.kt` | `ExportSpec`, `Preset`, codec di serializzazione | 21 |
| `OutputFormat.kt` | formati, risoluzioni, `QualitySearch`, `Raster`, `Sizes` | 21 |
| `Watermark.kt` | modello filigrana, wrap e dimensionamento del testo | 15 |
| `Reorder.kt` | riordino delle facciate | 15 |
| `ImageFilters.kt` | `ToneMapping` (la parte pura; il resto è Android) | 11 |
| `FileNames.kt` | slug sicuri per i nomi file | 11 |

### Strato Android — non testato su JVM

| File | Contenuto |
|---|---|
| `MainActivity.kt` | UI Compose, ~800 righe, il file più grande |
| `PageRenderer.kt` | disegno della pagina su un `Canvas` qualsiasi |
| `DocumentExporter.kt` | PDF multipagina, rasterizzazione, anteprima |
| `InputLoader.kt` | foto e PDF → bitmap |
| `PresetStore.kt` | `SharedPreferences` |
| `SharedFiles.kt` | `FileProvider`, intent di condivisione e apertura |

**Quando aggiungi una funzionalità**: estrai la parte decidibile in un oggetto
puro e testala. Se stai per scrivere logica dentro un `@Composable` o dentro una
funzione che prende un `Context`, fermati e chiediti cosa può uscirne.

---

## Invarianti da non rompere

Ognuna è stata una decisione, non un caso. Cambiarle è legittimo, ma
consapevolmente.

### 1. La pagina PDF si crea in punti, non in pixel

A4 = 595 × 842 punti PostScript. Sembra una risoluzione ridicola, ma il backend
Skia incorpora il bitmap come immagine e gli applica solo una trasformazione: i
pixel originali della foto arrivano intatti. Una pagina "a 300 dpi"
(2480 × 3508 punti) produrrebbe un foglio grande come un manifesto.

### 2. `PageRenderer.drawPage()` è il punto unico di disegno

Lavora sempre in punti e riceve un `Canvas` qualsiasi. Il PDF gli passa il canvas
della pagina; i formati immagine gli passano un canvas su bitmap scalato di
`dpi / 72`; l'anteprima idem, a densità bassa. **Non creare una seconda
implementazione del disegno**: il JPEG deve restare la rasterizzazione esatta
della stessa pagina che finirebbe nel PDF.

### 3. Niente binarizzazione nel filtro bianco/nero

La soglia secca cancella la foto del volto, gli ologrammi e i microtesti, e il
risultato *sembra* un documento alterato. Il filtro fa auto-livelli in scala di
grigi e mantiene oltre cento livelli intermedi. C'è un test che lo verifica: se
alzi `CONTRAST_GAIN` troppo, quel test si rompe — ed è voluto.

### 4. Il punto di bianco si aggancia al picco della carta

Non a un percentile fisso. Su una scansione lo sfondo è un modo largo e basso
dell'istogramma: il 90° percentile cade *dentro* quel modo e lascia la carta
grigia. `ToneMapping.paperWhitePoint()` trova il picco nella metà alta e scende
alla sua spalla. Il modo va pesato per **massa**, non per altezza del singolo
bin — questo bug c'è già stato.

### 5. Quando il blocco si riduce, scalano anche i vuoti

Scalare le sole immagini lasciando i margini fissi è l'errore classico: il blocco
ridotto sborda comunque.

### 6. La cella ha la stessa dimensione su tutte le pagine

Anche quando l'ultima è parziale. Due facciate rimaste da sole non si gonfiano a
riempire il foglio: il blocco parziale viene centrato. Celle di dimensione
diversa fra le pagine rendono la stampa visibilmente disomogenea.

### 7. I formati immagine si disabilitano oltre la prima pagina

Un JPEG non ha pagine. `DocumentExporter.export()` ha una `require(!plan.isMultiPage)`
e la UI torna automaticamente al PDF. Non rimuovere il controllo per "farlo
funzionare comunque": produrrebbe un file monco che l'utente scopre allo
sportello.

### 8. Il testo della filigrana non si tronca mai

Se eccede due righe, l'eccedenza confluisce nell'ultima e poi è il corpo a
scendere fino a 6 pt. Tagliare a metà una frase come «Copia ad uso iscrizione
scolastica anno 2026/2027» ne cambia il senso giuridico.

### 9. Nessun preset dice «copia conforme all'originale»

È un'autentica ex art. 18 DPR 445/2000 che solo un pubblico ufficiale può
rilasciare. Offrirla come formula pronta indurrebbe l'utente in errore. Il campo
resta libero, ma i suggerimenti no. C'è un test che presidia la regola.

### 10. La decodifica dei preset è tollerante

Campi mancanti, numeri fuori scala ed `enum` non riconosciuti ricadono sul
default. Un preset salvato oggi deve restare leggibile dopo che un `enum` avrà
guadagnato o perso una voce. L'unico campo indispensabile è il nome.

### 11. Le etichette sono legate alla posizione, non al contenuto

Spostando una foto dal secondo al primo slot, quella foto diventa il «Fronte».
Chi riordina lo fa proprio per correggere l'ordine di scansione.

### 12. Il peso del file si misura, non si stima

Estrapolare da un provino a bassa risoluzione sbaglia del ~15%, che è lo scarto
che fa sforare un limite di caricamento da 2 MB. Si comprime davvero in
sottofondo, con debounce, e quegli stessi byte vengono riusati al salvataggio.

### 13. Su disco solo impostazioni, mai le immagini

I preset contengono configurazioni. I file temporanei di anteprima e condivisione
vengono cancellati in `onDestroy()`, e la cartella di condivisione si svuota a
ogni nuova condivisione. `android:allowBackup="false"`.

---

## Trappole già scoperte

Costano un pomeriggio ciascuna se ci si ricasca.

**`ImageDecoder` restituisce bitmap hardware per default.** Un bitmap hardware
non può essere disegnato sul `Canvas` di un `PdfDocument`: l'app crasha al
momento della generazione, cioè nel punto più scomodo. Va forzato
`ALLOCATOR_SOFTWARE`.

**`PdfRenderer` pretende un file descriptor seekable.** I provider cloud (Drive,
allegati mail) spesso non lo garantiscono: `IllegalArgumentException` sporadico e
difficile da riprodurre. Il PDF in ingresso viene copiato in cache prima di
essere aperto.

**Il `FileProvider` espone solo `cache/shared/`**, non l'intera cache. Con
`path="."` esporrebbe anche i PDF temporanei di input, che sono anch'essi
scansioni di documenti.

**Non dare ai tipi di dominio nomi di API Compose comuni.** `Arrangement` e `Box`
erano entrambi in collisione con `androidx.compose.foundation.layout`, importato
con wildcard in `MainActivity`. Per i **qualificatori di tipo** Kotlin preferisce
le dichiarazioni dello stesso package agli import con wildcard, quindi
`Arrangement.spacedBy(...)` risolveva sul nostro enum e non compilava. `Box` non
dava errore solo per fortuna: era una chiamata di funzione, dove la risoluzione
degli overload sceglieva il candidato applicabile. Ora si chiamano
`GridArrangement` e `LayoutBox`. Prima di introdurre un tipo, controlla che il
nome non esista in Compose.

**CodeQL non analizza le PR impilate.** `codeql.yml` filtrava su
`pull_request: branches: [main]`: una PR la cui base è un'altra PR non veniva
analizzata affatto, e il buco si vede solo a merge avvenuto. Il filtro è stato
tolto. Da ricordare perché l'unico check obbligatorio su `main` è «Test e
analisi»: CodeQL può restare rosso senza che nulla si fermi.

**L'estrattore Kotlin di CodeQL è indietro rispetto al compilatore, e detta la
versione di Kotlin.** Con la 2.4.20 l'analisi muore con «Kotlin version 2.4.20 is
too recent. CodeQL currently supports versions below 2.4.20». Non è un problema
della action: `codeql-action@v4` dà lo stesso errore della v3. Per questo il
catalogo resta a **2.4.0**, ed è la voce `kotlin` del catalogo a pilotare il
compilatore anche sotto AGP 9, che pure porta Kotlin con sé: abbassarla fa
comparire `kotlin-build-tools-impl-2.4.0.jar` al posto della 2.4.20.

**`build-mode: none` non è una scorciatoia per aggirarlo.** Estrae solo il Java,
e qui non c'è Java: l'analisi finisce con «CodeQL could not process any code
written in Java/Kotlin», cioè verde in apparenza e vuota nei fatti. Provato,
fallito, e il motivo sta in fondo al log, non nell'errore in cima.

**AGP 9 porta Kotlin con sé.** Dalla 9.0 il plugin
`org.jetbrains.kotlin.android` non va più applicato: se resta, il build muore con
«Cannot add extension with name 'kotlin'» (con Kotlin 2.1) o con un messaggio
esplicito che chiede di rimuoverlo (con Kotlin 2.4). Il blocco
`kotlin { compilerOptions { jvmTarget } }` dentro `android {}` continua a valere,
ed è quello che tiene il bytecode a 17: verificato, `major 61`.

**L'ordine fra `LaunchedEffect` è fragile.** La cache dei filtri è indicizzata su
`(bitmap, filtro)` e potata per *insieme voluto*, proprio per non dipendere da
quale effetto parte per primo. Non sostituirla con un `clear()` in un effetto
separato.

**Il salvataggio automatico dei preset attende 600 ms.** Serve a non scrivere su
disco a ogni tasto della filigrana, e fa sì che il ripristino iniziale non venga
sovrascritto dai valori di default.

**`ActivityResultContracts.CreateDocument` fissa il MIME alla costruzione.** Qui
cambia col formato scelto, da cui il contratto su misura `CreateDocumentWithMime`
in fondo a `MainActivity.kt`.

---

## Come si scrivono i test qui

Tre regole, seguite in tutto il progetto.

**Verifica i numeri prima di scrivere l'asserzione.** Ogni costante nei test
(2479 px, scala 0,714, q75 sotto 500 kB) è stata calcolata e controllata, non
dedotta a mente. Più di una asserzione scritta "a occhio" si è rivelata sbagliata
di un pixel o di un caso limite.

**Inietta le dipendenze difficili come funzioni.** `WatermarkText.wrap()` riceve
la misurazione del testo, `QualitySearch.highestUnder()` riceve la compressione.
Nei test si sostituiscono con un misuratore finto e una curva sintetica: niente
`Paint`, niente `Bitmap`.

**I test presidiano le decisioni, non solo il codice.** «Nessun preset dichiara
una conformità all'originale», «i toni intermedi non collassano come in una
binarizzazione»: servono a impedire che qualcuno, in buona fede, smonti sei mesi
dopo una scelta ragionata.

---

## Licenza e intestazioni

**Apache-2.0.** Ogni sorgente inizia con due righe:

```kotlin
// Copyright 2026 Giuliano Pintori
// SPDX-License-Identifier: Apache-2.0
```

**I file nuovi devono averle.** Non c'è ancora un controllo automatico che lo
verifichi: aggiungerne uno alla CI è un candidato ragionevole per la roadmap.

Non esiste un file `NOTICE`, di proposito: senza attribuzioni di terzi da
riportare, crearlo imporrebbe un obbligo a chi ridistribuisce senza dare niente
in cambio.

## Decisioni ancora aperte

**Il nome.** `strings.xml` dice *Impagina*, e la top bar lo legge da lì
(`stringResource(R.string.app_name)`), quindi il nome vive in un posto solo. Le
altre due occorrenze sono `settings.gradle.kts` e il titolo del README. Erano
stati valutati anche *Bifronte*, *Ricomponi* e *Unifoglio*.

**`applicationId` è ancora `io.github.pintorig.impagina`.** Va cambiato prima di
pubblicare: dopo il primo upload sul Play Store è **definitivo**. Usa un
reverse-domain che controlli davvero.

**`gradle-wrapper.jar` è nel repository**, insieme a `gradlew` e `gradlew.bat`,
ed è il Gradle che usa anche la CI. Resta da aggiungere
`gradle/actions/wrapper-validation`, che verifica che quel binario sia davvero
quello ufficiale: ora che il jar è committato ed è nel percorso di build di
tutti, è il candidato più sensato per il prossimo giro sulla pipeline.

**`lint { abortOnError = true }`** ha girato in CI ed è pulito, quindi la
domanda se partire morbido non si pone più: resta a `true`. Il report HTML viene
pubblicato come artifact anche in caso di fallimento.

**Niente ktlint né detekt**, di proposito: una CI rossa al primo push è una CI
che si impara a ignorare. Il modo corretto è aggiungerli in locale, generare una
baseline, sistemare e solo allora metterli in pipeline. `.editorconfig` c'è già.

---

## Prossimi passi

La pipeline è confermata sul campo: `Build` e `CodeQL` sono verdi sullo stesso
commit, 120 test, lint compreso. Non è più il primo punto da sbrigare.

Dalla roadmap del README, in ordine di utilità:

1. **Voce «Dimentica tutto»** — `PresetStore.clear()` esiste già, manca solo
   esporla nella UI insieme a `SharedFiles.clear()`.
2. **Traduzione inglese** — le stringhe sono attualmente scritte a mano nei
   composable; vanno estratte in `strings.xml` prima di poter tradurre. È anche
   l'occasione per ridurre `MainActivity.kt`, che a 800 righe è il vero debito
   del progetto.
3. **Scomporre `MainActivity.kt`** — separare stato e azioni in un `ViewModel` e
   i composable in file per sezione. Gran parte della logica è già fuori; quello
   che resta è orchestrazione.
4. **Test di strumentazione** su `SharedFiles` e `DocumentExporter`, le uniche
   due aree senza copertura.
5. **Riordino per trascinamento**, se le frecce si rivelano scomode. `Reorder.move()`
   è già l'unico punto da chiamare.

---

## Cronologia

Dettagli in [CHANGELOG.md](CHANGELOG.md).

| Versione | Contenuto |
|---|---|
| 1.0.0 | fronte/retro ID-1 su una pagina A4 |
| 1.1.0 | più tipi di documento, riduzione controllata |
| 1.2.0 | filtri di resa con auto-livelli |
| 1.3.0 | filigrana a testo configurabile |
| 1.4.0 | JPEG, PNG, WebP; `PageRenderer` come punto unico di disegno |
| 1.5.0 | qualità regolabile, peso reale, ricerca sotto tetto |
| 1.6.0 | layout multipagina |
| 1.7.0 | riordino delle facciate |
| 1.8.0 | preset e ripristino dell'ultima configurazione |
| 1.9.0 | condivisione, apertura del file salvato, nomi sanificati |
