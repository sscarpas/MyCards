# Tech Debt

A running list of known shortcuts and deferred work, to be addressed in future iterations.

---

## TD-001 · Code duplication between AddCardFragment and EditCardFragment

**Added:** Sprint 4 (Edit Card feature)
**Resolved:** Sprint 5 (March 2026) — `CardImagePickerHelper` extracted. Both fragments now delegate all launcher / permission / dialog logic to it as a property initialiser.

---

## TD-002 · Orphaned images when navigating away from AddCardFragment mid-flow

**Added:** Sprint 3 (Add Card feature)
**Resolved:** Sprint 5 (March 2026) — `saved` flag and `onDestroyView()` cleanup added to `AddCardFragment`, matching the existing pattern in `EditCardFragment`.

---

## TD-003 · No size cap or eviction policy for filesDir/images/

**Added:** Sprint 3 (Add Card feature)

Every card photo (front + back) is copied to `context.filesDir/images/`. There is no upper limit on how many files can accumulate, and no periodic cleanup of unreferenced files (e.g. from TD-002 above).

**Suggested fix:** On app startup (in `MyCardsApplication.onCreate()`), enumerate all files in `filesDir/images/` and delete any whose path does not appear in the `loyalty_cards` table. This is a cheap O(n) scan that runs once per launch.

---

