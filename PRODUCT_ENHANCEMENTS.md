# Product Enhancements

A running list of planned improvements and new features, to be prioritised and picked up in future sprints.

---

## PE-001 · Collapsible image picker buttons after a photo is added

**Added:** March 2026
**Resolved:** March 2026 — Implemented a three-state image section pattern on both `AddCardFragment` and `EditCardFragment`. Before a photo is set, the full `160dp` picker (Gallery / Camera) is shown. After a photo is confirmed, the picker collapses: a `180dp` preview with a ✕ remove button appears, and a compact `56dp` change strip (Gallery icon + Camera icon with `10sp` labels) is shown directly beneath the preview. Tapping a change-strip icon opens the gallery or camera immediately without re-expanding the full picker. Tapping ✕ deletes the temp file and restores the full picker. The pattern is identical for the front and back image sections on both screens. Both `Finish` (Add) and `Save` (Edit) buttons moved out of the `ScrollView` into a sticky footer `LinearLayout` with `elevation="8dp"` and `?attr/colorSurface` background, so the primary action is always visible regardless of scroll position. Regression covered by `AddCardFragmentTest` (Espresso, `fragment-testing 1.6.1`).

---

## PE-002 · Remove native camera acceptance screen from the capture flow

**Added:** March 2026
**Resolved:** March 2026 — `CameraFragment` introduced using CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` 1.3.4). The native `TakePicture` contract removed from `CardImagePickerHelper`; camera button now navigates to `CameraFragment` which owns permission checking, the live `PreviewView`, and shutter capture. Captured URI is returned via `FragmentResult` key `"camera_capture"` to `AddCardFragment` / `EditCardFragment`, which delegate to `CardImagePickerHelper.onCameraResult()` → existing confirmation dialog. Fragment is locked to landscape in `onResume` / `onPause`, laying the groundwork for PE-003.

---

## PE-003 · Enforce landscape aspect ratio for all captured card images

**Added:** March 2026
**Resolved:** March 2026 (v3) — Root cause of previous broken crop identified: `cropCenterToCardRatio` was cropping the full sensor output to card ratio, but the frame overlay shows only 85 % / 60 % of the preview width — a ~2.5× mismatch in visible area. Fix has two parts: (1) `CameraFragment` now defers `startCamera()` via `doOnLayout` so `PreviewView.viewPort` is non-null, then binds `Preview` + `ImageCapture` in a `UseCaseGroup` with that `ViewPort` — this makes the saved JPEG contain exactly the pixels visible in the `PreviewView`; (2) a new `CardImagePickerHelper.cropToFrameRegion` function replaces `cropCenterToCardRatio` for camera results: it applies EXIF rotation, then center-crops using `FRAME_FRACTION_PORTRAIT = 0.85` (portrait capture) or `FRAME_FRACTION_LANDSCAPE = 0.60` (landscape capture) — the same constants as `CardFrameOverlayView` — so the output matches exactly what was inside the on-screen frame. Gallery picks continue to use `cropCenterToCardRatio` unchanged.

---

## PE-004 · In-app photo confirmation screen — add image cropping

**Added:** March 2026

**Description:** After the user takes a photo with the camera, an in-app confirmation screen ("Use this photo?" / "Cancel") is shown before the image is accepted. The screen currently only allows accepting or discarding the photo — no editing is possible at this stage.

**Suggested improvement:** Enhance the existing in-app confirmation screen in `CardImagePickerHelper` (and its associated dialog/fragment) with an interactive crop tool. This allows the user to frame the card precisely before saving it, avoiding the need to retake the photo. A library such as [uCrop](https://github.com/Yalantis/uCrop) or Android's built-in `ImageDecoder` + `Canvas` approach could be used. The crop result should replace the raw camera output as the confirmed image URI passed back to `AddCardFragment` / `EditCardFragment`.

---

## PE-005 · Export and import the card list to/from an external file

**Added:** March 2026
**Resolved:** March 2026 — Added a Settings screen (`SettingsFragment`) accessible from the overflow menu on the main card-list screen only (`FirstFragment`); the menu item is hidden via `OnDestinationChangedListener` on all other destinations. The screen exposes two actions: **Export cards** and **Import cards**.

Export serialises all Room cards into a ZIP archive (`mycards_backup_YYYYMMDD.zip`) containing a `manifest.json` (card names + relative image filenames) and the URI-backed image files; the save location is chosen via `ACTION_CREATE_DOCUMENT`. Resource-backed seed cards are exported by name only (their drawables are not owned by the user).

Import reads a ZIP via `ACTION_OPEN_DOCUMENT`, re-saves extracted images to `filesDir/images/`, and inserts non-conflicting cards immediately. For each card whose name already exists in the database a per-card `AlertDialog` is shown in sequence, letting the user choose **Skip** (keep existing) or **Overwrite** (replace data and images) independently for every conflict. A Snackbar reports the final count of imported cards. Implementation lives in `CardBackupManager` (ZIP + JSON logic, zero new dependencies — uses `java.util.zip` and `org.json`), `SettingsFragment`, and supporting additions to `CardDao`/`CardRepository`.

---

## PE-006 · Ordering and sorting the card list

**Added:** March 2026

**Description:** Cards are currently displayed in insertion order (Room default). As the collection grows, there is no way to reorder or find cards quickly.

**Suggested improvement:** Add a sort control to the card list screen (e.g. a small dropdown or toggle in the toolbar) offering at least: alphabetical A→Z, alphabetical Z→A, and most-recently-added. Persist the selected sort order in `SharedPreferences` or `DataStore` so it survives app restarts.

---

## PE-007 · Multi-language support (i18n)

**Added:** March 2026

**Description:** All user-visible strings are currently hard-coded in English inside `res/values/strings.xml`. There is no support for other locales.

**Suggested improvement:** Introduce additional `res/values-<locale>/strings.xml` resource files (e.g. `values-pl` for Polish) and ensure all strings are externalised (no hard-coded text in layouts or Kotlin code). Use the Android `LocaleList` API if runtime language switching is desired without relying solely on the system locale.

---
