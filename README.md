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

