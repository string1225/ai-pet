# Quiz care and two-color star shop implementation plan

**Goal:** Complete feeding, play and cleaning through questions; let children redeem decorations and a level boost with either gold knowledge stars or green growth stars.

**Architecture:** Reuse the existing lesson presenter and curriculum. A pure Java reward catalog drives both pet-wallet purchases and the persisted family gift queue. Pet saves hold cleanliness and owned/equipped decorations; Canvas renders equipped items.

**Tech stack:** Java 17, Android Canvas, SharedPreferences JSON, JUnit 4, Android instrumentation.

## Tasks

- [x] Add failing domain tests in `PetEngineTest.java` and `RewardCatalogTest.java` for cleaning, zero-stock quiz feeding, affordable/insufficient knowledge purchases, persistent learning totals, duplicate decorations and repeatable level boosts. Extend `GiftAndPinTest.java` for growth purchases and idempotent deliveries. Run `gradlew.bat testDebugUnitTest` and confirm missing features fail.
- [x] Implement `model/RewardCatalog.java`, extend `PetState.java`, `PetEngine.java`, `PetStore.java`, `family/FamilyState.java`, and `GiftDelivery.java`. Reuse catalog price/effect checks in both wallets. Save new fields with backwards-compatible defaults. Run the unit suite to green.
- [x] Update `MainActivity.java` to launch one-question care activities, suppress normal lesson bonuses for care, guard finished callbacks, expose cleaning and the reward shop, and present separate gold/green purchase buttons. Connect the existing family gifts screen to the same shop through `FamilyController.Host`.
- [x] Update `ui/RoomView.java` to draw equipped decorations and the wash basin, and `ui/Ui.java` for cleaning and colored star icons. Add simulator coverage in `CareShopInstrumentation.java` for actual view interaction, curriculum use, rejected answers, interrupted care, both wallet choices, rotation, restart and save migration.
- [x] Run `tools/build.ps1 -VersionCode 5 -VersionName 0.3.0`, debug/androidTest builds and instrumentation on the disposable API 36 emulator. Inspect landscape/portrait screenshots. Update README, architecture and verification notes with actual results.
- [x] Review the diff, commit only intended files and push `codex/quiz-care-star-shop` to origin. Keep the user’s existing main checkout history intact.

## Verification result

82 unit tests, 49 care/shop UI checks, 27 family/learning checks, 41 touch smoke checks and 16 rotation/rename/reset checks passed. Debug and release lint both report zero errors. Review identified the SharedPreferences commit-failure memory issue; a real unwritable-directory test failed before the fix and passed after rollback was added.
