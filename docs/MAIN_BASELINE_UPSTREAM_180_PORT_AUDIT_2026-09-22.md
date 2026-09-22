# OneUIX-Mod: official-main-first upgrade audit (2026-09-22)

## Authoritative inputs

- MOD **production source**: `iSkyJIE/OneUIX-Mod` `main` at `92312ebcfe7ab36cf18a55e90af8a0f5dfebb314`.
- Upstream changes target: `SoClear/OneUIX` `main` at `7aa73a036e066e79e6e2306256229dbe9f88dbb1`.
- Correct integration branch: `upgrade/from-main-upstream-180-20260922`, created directly from the production commit above.
- `upstream-180-mod-ci-staging` at `87589ce6b95f5e726c2eb323af8a19220c2c1ad9` is **not** an acceptable MOD source. It diverges from production at historical `78e2ef1d10320aab293631f97ec636cf64d4cbea`; its #496/#515/#522 APKs and green CI do **not** establish feature preservation. No merging from this experimental branch.

## Confirmed behavior regressions in the rejected staging branch

1. Production `StatusBar.kt` uses separate double-line layouts for Fold7 (`SM-F966`) and all other models. Fold7 has independent size controls, non-Fold7 has five presets. Staging applies the independent size UI to every model and uses generic preset scales; preserve production device gating.
2. Production non-Fold7 presets specify `(upper scale, lower scale, line-height multiplier)` as small `(0.74,0.68,0.72)`, compact `(0.78,0.72,0.69)`, standard `(0.82,0.76,0.66)`, large `(0.86,0.80,0.63)`, extra-large `(0.90,0.84,0.60)`. Fold7 uses 0.66 line-height multiplier. Rejected staging uses `setLineSpacing(..., 1f)`, so 0dp only cancels *additional* spacing. #522 adds a heuristic correction but still does not reproduce the official algorithm.
3. Production clock formatting UI calls MOD `StatusBarClockFormatter.format(...)` on confirm, actually expanding `CNLUNAR`, `CNPERIOD`, `CNTIME`, `CNYEAR`, `CNZODIAC`, `CNSEASON`. Rejected staging instead echoed custom tokens; #522 introduced a live preview to compensate, but not the production double-line layout.
4. Production clock sets and restores text view height, parent and text gravity, font padding, padding and one-line state. Staging retains only a subset. Restore main's state semantics when porting to libxposed.
5. Production observes preferences by watching their actual directory for CLOSE_WRITE or MOVED_TO. Rejected staging's FileObserver watches a `/proc/self/fd/...` path while closing that descriptor immediately; do not assume it provides the same runtime semantics without verification.

## Merge conflict audit: verified on an isolated runner without source changes

GitHub Actions run `35738894785` checked that MOD main and upstream main matched the pinned commits, attempted `git merge --no-commit --no-ff upstream/main`, captured conflicts, then `git merge --abort` and confirmed a clean worktree. Eight paths require explicit manual reconciliation:

- `.github/workflows/ci.yml` — preserve MOD build and signing behavior, adapt to upstream toolchain.
- `.github/workflows/release.yml` — preserve MOD release signing, package identity and artifact format.
- `app/build.gradle.kts` — preserve package/version/signing identifiers and adopt upstream modules.
- `app/src/main/AndroidManifest.xml` — preserve declared app identity, permissions and hook entry while adapting libxposed.
- `app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt` — move and **port the official production implementation** to the upstream hook module; do not silently delete it or use experimental implementation.
- `app/src/main/java/io/github/soclear/oneuix/hook/util/PreferenceProvider.kt` — preserve official config value and live update behavior using new provider/service API.
- `hook/src/main/java/io/github/soclear/oneuix/hook/Main.kt` — integrate upstream entry-point changes with every MOD hook preference and its model gating.
- `hook/src/main/java/io/github/soclear/oneuix/hook/util/StatusBarClockFormatter.kt` — relocate the exact main formatter and calendar; preserve all six token implementations.

Auto-merged paths are **not** proof of semantic preservation. Manually inspect `DetailPaneSystemUI.kt`, `Preference.kt`, custom notification settings migration, Chinese/English string resources, launcher/recents behavior and any status bar customizations.

## Signing and package compatibility: independently verified

- Production app Gradle source explicitly sets `applicationId = "io.github.mod.oneuix"`, `versionCode = 1`, `versionName = "1.0.0"`.
- Previous staging's `gradle.properties` also sets `oneuix.applicationId=io.github.mod.oneuix`, so its application ID is **not in itself** a confirmed regression. The new branch must preserve this identity when adopting upstream's property-based ID.
- Official upstream `release.yml` hard-codes expected signing certificate SHA-256 `3cfb2d32db4526e9cce22a0092a7d6819659db433cfec613fed68d44d999682a`, which is **different** from the MOD certificate actually shown by #522 CI: `3cca67360758a4956796fcf6d8c726daf92b581532a1bb7e81211c8107bc7e09`. Do not accept upstream's release workflow certificate pin unchanged or mistake an upstream signature for a MOD-compatible update. Verify the old production certificate rather than assuming matching app IDs suffice.

## Release gate

1. Build only on the correctly main-derived integration branch. Protect `main` from all automated writes.
2. Compare each production MOD feature, old preference key, UI event, hook and runtime behavior against the post-port implementation; do not add new behavior in place of old without an explicit requirement.
3. Validate old-config migration, calendar tokens, Fold7 vs slab five-preset gating, line-height multiplier, restoring one-line layout, top/bottom offsets, power actions, MOD branding, signing/package/version upgrade paths and upstream latest changes.
4. Run tests, compile and verify actual APK certificate; then phone-test before proposing merge to main.
5. Explicitly mark #522 and preceding test artifacts as obsolete, not acceptance evidence.
