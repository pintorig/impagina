# Changelog

Formato secondo [Keep a Changelog](https://keepachangelog.com/it/1.1.0/).
Il progetto segue il [versionamento semantico](https://semver.org/lang/it/).

## [1.9.0]

### Aggiunto
- Condivisione diretta tramite `FileProvider`, senza passare dal salvataggio
- Azione **Apri** dopo il salvataggio, per verificare il file senza cercarlo
- `FileNames`: nomi file sanificati (accenti ripiegati, separatori di percorso
  rimossi, lunghezza limitata)

### Modificato
- Tutti gli avvisi passano da snackbar: eliminati i `Toast` residui
- Il `FileProvider` espone solo la sottocartella di condivisione, non l'intera
  cache, così non può finire condiviso un PDF temporaneo di input

## [1.8.0]

### Aggiunto
- Preset con nome, fino a otto, applicabili con un tocco
- Ripristino automatico dell'ultima configurazione all'avvio
- `PresetCodec`: serializzazione su riga singola con escaping completo e
  decodifica tollerante ai campi mancanti o sconosciuti

### Modificato
- Formato, risoluzione e qualità raccolti in `ExportSpec`

## [1.7.0]

### Aggiunto
- Riordino delle facciate con frecce di posizione
- Inversione rapida quando le facciate sono due

### Corretto
- Riordinare non rifiltra più tutte le immagini: cache su `(bitmap, filtro)`

## [1.6.0]

### Aggiunto
- Layout multipagina, da 1 a 12 facciate
- Capienza per foglio derivata dalla dimensione fisica del documento
- Sfogliamento dell'anteprima

### Modificato
- I formati immagine si disabilitano oltre la prima pagina, invece di produrre
  un file monco

## [1.5.0]

### Aggiunto
- Qualità regolabile per JPEG e WebP
- Peso del file misurato davvero, non stimato
- Ricerca binaria della qualità massima sotto un tetto di peso

## [1.4.0]

### Aggiunto
- Uscita in JPEG, PNG e WebP oltre al PDF
- Risoluzione selezionabile per i formati immagine

### Modificato
- `PageRenderer` diventa il punto unico di disegno per PDF e raster

## [1.3.0]

### Aggiunto
- Filigrana a testo libero, in fondo al foglio o in diagonale
- Segnaposto `{data}` sostituito alla generazione

## [1.2.0]

### Aggiunto
- Filtri di resa: scala di grigi e alto contrasto con auto-livelli

## [1.1.0]

### Aggiunto
- Più tipi di documento: carta d'identità, patente, tessera sanitaria,
  passaporto, altro
- Riduzione controllata quando la combinazione non entra in A4

## [1.0.0]

Prima versione: fronte e retro di un documento ID-1 su una pagina A4.
