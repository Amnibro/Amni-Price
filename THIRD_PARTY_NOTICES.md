# Third-party notices

## Fonts

Both fonts are bundled in `app/src/main/res/font/` and licensed under the
[SIL Open Font License 1.1](https://openfontlicense.org).

- **Archivo** (variable, weight + width axes): Copyright 2020 The Archivo Project Authors
  (https://github.com/Omnibus-Type/Archivo). The Amniscient brand typeface.
- **JetBrains Mono**: Copyright 2020 The JetBrains Mono Project Authors
  (https://github.com/JetBrains/JetBrainsMono). Used for prices and numbers.

## Map

- **osmdroid** (Apache 2.0), https://github.com/osmdroid/osmdroid
- Map data © OpenStreetMap contributors, available under the
  [Open Database License](https://www.openstreetmap.org/copyright). Attribution is shown on the map.

## Libraries

AndroidX, Jetpack Compose, CameraX and Room (Apache 2.0); Kotlin coroutines (Apache 2.0).
Test-only: JUnit, Robolectric, Roborazzi.

## Scanning

- **`play` flavor:** Google ML Kit text recognition and barcode scanning
  ([ML Kit terms](https://developers.google.com/ml-kit/terms)). Not open source.
- **`fdroid` flavor:** fully open source.
  - [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) (Apache 2.0), built from source
    from the `external/Tesseract4Android` submodule, bundling
    [Tesseract](https://github.com/tesseract-ocr/tesseract) (Apache 2.0),
    [Leptonica](http://leptonica.org) (BSD 2-Clause), libjpeg-turbo (IJG/BSD) and libpng (libpng license).
  - English model `eng.traineddata` from [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) (Apache 2.0).
  - [ZXing](https://github.com/zxing/zxing) core (Apache 2.0) for barcodes.
