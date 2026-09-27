# Digital Companion for Field Drug Testing — SIH PS 26231

A single, end-to-end Android Studio project assembled from the five
teammate modules supplied for this project, wired together for real (no
mocked camera, classifier, crypto, or database in the main flow):

| Person | Module | Where it lives in this project |
|---|---|---|
| 1 — Camera & Image Capture | `CameraCaptureModule.zip` | `com.sih.drugtestclassifier.camera.CameraCaptureFragment` |
| 2 — Image Processing & Classification | `DrugTestClassifier-Android.zip` | `com.sih.drugtestclassifier.{models,color,calibration,classification,pipeline}` |
| 3 — Digital Evidence & Security | `security.zip` | `com.sih.drugtestclassifier.security.DigitalEvidence.kt` |
| 4 — Database & Search | `FieldDrugTest.zip` | `database.*` (Room: `AppDatabase`, `TestDao`, `TestRepository`) |
| 5 — UI & Demo Flow | `Codex__2_.zip` | `app.ui.*` (Compose screens + navigation) |
| — | Integration glue (new) | `com.sih.drugtestclassifier.integration.*` |

## What changed from each module, and why

Each module was kept as close to what was supplied as possible. The
changes below were the minimum needed to make five independently-built
pieces compile and run as one app:

- **One shared contract package.** All three of the locked contracts —
  `TestImage`, `ClassificationResult`, `DigitalTestRecord` (project doc,
  section 5) — were each defined *four times* across the five zips (once
  in the classification module, once in `security.zip`, once in the UI
  module's `TestModels.kt`, once in `FieldDrugTest.zip`'s `database`
  package). They now live in exactly one place:
  `com.sih.drugtestclassifier.models.ClassificationModels.kt`. Every other
  module imports from there instead of declaring its own copy.
  `ClassificationResult.confidence` was changed from `Double` to `Float`
  to match the locked contract and what security/UI/database already
  assumed. `DigitalTestRecord` is now `@Entity`-annotated directly (Room
  needs the annotation on the actual class it persists) and carries one
  extra field beyond the locked contract, `localImageUri` (defaulted to
  `""` so every existing call site still compiles) — see "Verify" below
  for why.
- **Camera → UI bridge.** Person 1's `CameraCaptureFragment` is a
  View-based CameraX `Fragment`; Person 5's `CaptureScreen` is a Compose
  composable. `CaptureScreen.kt` now hosts the real fragment inside Compose
  via a `FragmentContainerView` + `AndroidView` bridge, so `MainActivity`
  needs to be a `FragmentActivity` (it extends `AppCompatActivity`, which
  is one).
- **Real classification.** `CaptureScreen` → `DemoTestViewModel.classify()`
  → `RealTestRecordRepository.classify()` now decodes the actual captured
  JPEG and runs Person 2's `ClassificationPipeline` (HSV/Lab colour
  calibration against the reference card, then colour-distance
  classification) instead of the UI module's `FakeTestRecordRepository`
  random-outcome cycle. `FakeTestRecordRepository` is still included,
  unused by the real app, in case you want to preview screens without a
  device/camera.
- **Real hashing + signing.** `RealTestRecordRepository.createRecord()`
  calls Person 3's `hashImage` / `buildCanonicalRecord` / `hashRecord` /
  `signRecordHash` on the real captured bytes, a best-effort GPS fix (see
  below), and the real classification result — the Android Keystore
  ECDSA key is generated on first use, exactly as Person 3 wrote it.
- **Real database.** `RealTestRecordRepository` now persists through
  Person 4's actual Room stack (`database.DatabaseProvider` →
  `database.TestRepository` → `database.TestDao`) instead of a placeholder.
  Room's DAO methods are `suspend`; the `TestRecordRepository` contract's
  `getRecords()` isn't, so the repository loads once at construction and
  keeps an in-memory cache it refreshes after every `saveRecord()` — the
  one pragmatic compromise here, documented in code.
- **Real verification.** `VerificationDetailScreen`'s "Verify" button now
  calls `verifyDetailed()`, which reads `record.localImageUri` (the extra
  Room column mentioned above) to re-read the original photo and re-runs
  `security.verifyRecord`, showing the same three-way breakdown (image
  hash / record hash / signature) as Person 3's original
  `VerifyRecordScreen` composable (kept, unused by the nav graph, as a
  standalone drop-in at `security/VerifyRecordScreen.kt`). The "Demo
  tamper-detected state" button corrupts the stored `recordHash` and
  re-verifies — genuine tamper detection through the real crypto path, not
  a canned animation.
- **GPS.** `location/LocationHelper.kt` is new — a small, dependency-free
  wrapper around the platform `LocationManager` (no Google Play Services,
  keeping the zero-budget/open-source-only constraint) that returns a
  best-effort last-known fix or `(0.0, 0.0)` if permission/fix isn't
  available, per the doc's "never crash, degrade to a disclosed
  limitation" rule.
- **`TestRecordRepository` interface gained one method.**
  `fun verifyDetailed(record): VerificationResult`, with a default body so
  the interface change doesn't break `FakeTestRecordRepository`. This is
  what makes the real three-way tamper breakdown visible in the UI.
- **Room + KSP added to Gradle.** `androidx.room:room-runtime`/`room-ktx`
  2.7.2 plus the `com.google.devtools.ksp` plugin (version
  `1.9.24-1.0.20`, matched to this project's Kotlin 1.9.24), matching what
  `FieldDrugTest.zip`'s own `build.gradle.kts` used.

Person 4's own `TestHistoryScreen.kt`, `DatabaseTest.kt`, and
`DatabaseVerificationTest.kt` are kept as supplied (imports fixed) but not
wired into the nav graph — Person 5's `HistoryScreen` already covers
search/filter with the app's actual styling, so wiring both would give two
competing history UIs.

## Why results were inaccurate — and how to fix it

This is almost certainly **not a bug in the merge** — it's
`assets/kit_profiles/default_kit_profile.json`. Open it and read its own
`_TODO` field:

> *"These Lab values and the offsets below are derived from THIS
> PROJECT'S SYNTHETIC sample images... Before the demo, replace
> 'references' with values measured/derived from the ACTUAL field-test
> kit's own documented colour-interpretation reference sheet."*

In other words, the positive/negative reference colours the classifier
compares every photo against were never real — they were tuned to match a
synthetic-image generator, not an actual camera photo of an actual kit
next to an actual printed reference card. Any real photo is very likely to
either land far from *both* references (→ **Inconclusive**, the
`maxConfidentDeltaE` distance check correctly refusing to guess) or, more
worryingly, land closer to the wrong one.

**I added a "Calibration / diagnostics" screen to fix this properly**
(new button on the Home screen). Instead of just a verdict, it shows every
intermediate value the pipeline computed for the photo you just took:

- Whether the reference card was detected at all, and its pixel bounding
  box (so you can see if it's even finding the right thing).
- The test-area rectangle it sampled, relative to that card.
- Raw sampled RGB, white-balance-calibrated RGB, and the resulting **Lab**
  colour — this is the number to copy into the kit profile.
- The colour distance (ΔE) from that Lab value to *every* configured
  reference, and the `maxConfidentDeltaE` threshold it's compared against.

**To fix real-world accuracy:** photograph an actual known-negative strip
next to the reference card, open Calibration, read off the Lab line, and
put it in `default_kit_profile.json`'s `references` array under
`"negative"`. Repeat with a known-positive strip for `"positive"`. If the
card is consistently not detected, or the test-area box in the diagnostic
output clearly isn't landing on the strip, the `testAreaOffset*`/
`testArea*Fraction*` geometry fields in that same file need adjusting to
match where `CameraCaptureFragment`'s on-screen guide actually places the
card vs. the strip — the diagnostic screen's boxes make that easy to
eyeball against the photo.

If you can tell me more specifically what's happening (always
Inconclusive? consistently wrong verdict? card never detected?) I can help
narrow it down further — but the calibration screen above is the fastest
way to see it yourself.

## Opening the project

1. Android Studio → **Open** → select this folder (the one with
   `settings.gradle.kts`).
2. This sandbox couldn't reach Gradle's download servers, so
   `gradle-wrapper.jar` itself isn't included — only
   `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.7). Android Studio
   will offer to regenerate the wrapper the first time you open the
   project ("Gradle wrapper is not found... regenerate?") — accept that,
   or click **Sync Project with Gradle Files**.
3. Run on a device or emulator with a camera. The AndroidKeyStore signing
   step needs a real keystore provider, so an emulator with Google APIs
   (or a physical device) is safest — some bare AOSP emulator images have
   flaky `AndroidKeyStore` support.

## Permissions requested at launch

`CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` — all requested
together in `MainActivity.onCreate()`. If location is denied, capture and
classification still work; the record's GPS fields fall back to
`(0.0, 0.0)`.

## What's still exactly as supplied

- Classification pipeline (`color`, `calibration`, `classification`,
  `pipeline`, `models/KitProfile*`): byte-for-byte unchanged from the
  earlier Dart→Kotlin port, including `default_kit_profile.json`'s
  placeholder Lab values — see the accuracy section above.
- `CameraCaptureFragment`: unchanged except the package line and the
  `TestImage` import.
- `DigitalEvidence.kt`'s hashing/signing/verification functions: unchanged
  except the removed duplicate data classes.
- `database.*` (Room): unchanged except imports pointed at the shared
  `DigitalTestRecord` and the duplicate entity class removed.
- Every Compose screen's layout/logic: unchanged except import fixes,
  `CaptureScreen` (real camera), `ResultScreen` (real image thumbnail
  instead of a placeholder box), `VerificationDetailScreen` (real
  three-way check breakdown), and one new screen (`CalibrationScreen`).

