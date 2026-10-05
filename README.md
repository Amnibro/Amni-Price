# Amni-Price

**Know which store is actually cheapest.** Point your phone at a shelf tag or a receipt
and Amni-Price reads the price with on-device OCR, matches it to the product by barcode or
name, and builds your own price comparison across every store you shop at.

Retailers now use algorithmic and AI-driven pricing that changes prices by store, day and
shopper. Amni-Price is the consumer side of that fight: a fast, private record of what
things *actually* cost, so you can see which business has the most affordable goods.

## Features

- **Live shelf-tag scanning.** CameraX + ML Kit read the price, product name, package size
  and barcode in real time. Multi-buy deals ("2 for $5", "3/$5.00") become per-item prices,
  unit-price lines ("$0.21 / oz") are ignored, and sale tags are flagged.
- **Quick save.** Set the store you're in once and save each item with one tap, with no forms.
- **Receipt scanning.** Snap a receipt (or pick a photo) and every line item is extracted.
  Coupons are applied to the item above them, and "2 @ 1.99" quantities become unit prices.
  The store name is read from the header and matched to your saved stores.
- **Instant comparison.** When you scan a barcode you've seen before, the app shows the
  best known price and where it was.
- **Cheapest-store ranking.** A price index across every product you've seen at more than
  one store ("Store B is +12% vs. the cheapest").
- **Unit prices.** Different package sizes can be compared per oz / 100 g / each.
- **CSV import & export.** Share price lists with family or a community, or back them up.
- **Private by default.** OCR models are bundled in the APK. Nothing leaves the phone.

## Build

Requirements: JDK 17+ and the Android SDK (platform 35). Android Studio works out of the box.

```bash
./gradlew :app:testDebugUnitTest   # parser unit tests
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug        # install on a connected device
```

Every push runs CI (`.github/workflows/android.yml`), and the debug APK is attached to the
workflow run as an artifact.

## Project layout

```
app/src/main/java/com/amniscient/price/
├── domain/         Pure Kotlin, no Android, fully unit-tested
│   ├── PriceParser     shelf tag → price, name, size, sale flag
│   ├── ReceiptParser   receipt rows → line items, total, store name
│   ├── RowGrouper      re-joins OCR column blocks into receipt rows
│   ├── UnitParser      "12 oz", "1.5L", "6 ct" → normalized quantity, unit price
│   ├── StoreRanker     cheapest-store price index
│   └── Csv, Money      helpers
├── data/           Room database (stores, products, prices), repository, settings
├── scan/           ML Kit wrapper (VisionEngine) and scan hand-off (ScanSession)
└── ui/             Jetpack Compose screens: scan, review, compare, stores
```

## Extending it

The code is organized so new features slot in without touching unrelated code:

| Want to… | Change |
| --- | --- |
| Improve price detection | `domain/PriceParser.kt` (add a test in `PriceParserTest`) |
| Support a store's receipt quirks | `domain/ReceiptParser.kt` + a test with a sample receipt |
| Add a unit (e.g. "dozen") | `UnitParser` regex + `factor()` |
| Swap the OCR engine | Implement the same `analyze`/`readText` as `scan/VisionEngine.kt`; everything downstream uses `OcrLine` |
| Add a screen | A composable + ViewModel in `ui/`, a route in `ui/AmniPriceRoot.kt`; get dependencies via `appViewModel { … }` |
| Add a database field | Update `data/Entities.kt`, bump the version in `AppDatabase` and add a `Migration` |
| Add a cloud/community sync | Build on `PriceRepository.exportCsv()` / `importCsv()`, or add a new source alongside it |

### Roadmap ideas

- Community price sharing (opt-in backend) so prices crowd-source across users
- Store locations via GPS to auto-select the current store
- Price-change alerts and shopping-list optimizer ("buy these 3 at Store A, the rest at B")
- Product matching across stores by fuzzy name when no barcode is available
- Online/retailer listing capture via share-sheet

## License

MIT
