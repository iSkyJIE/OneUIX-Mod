# Upstream v1.8.0 integration

Target: adopt upstream SoClear/OneUIX at ea319495c139f416e9a25a9e983923c521734e64 as the functional and architectural baseline, then port OneUIX MOD's customized SystemUI status-bar features onto the new `common`/`hook`/`app` modules.

## Protected MOD behavior

- Custom status-bar clock layout, dual-line formatting, lunar/calendar support, sizing and margins, and live preference refresh.
- MOD network-speed behavior including separate upload/download and thresholds.
- Status-bar settings UI, preference model and persistence, strings and supporting utilities.
- MOD application identity, signing and build artifact configuration.

## Integration rule

Use upstream implementations for all unrelated features. Do not copy the old monolithic `Main.kt`, `Preference.kt`, or `StatusBar.kt` wholesale over upstream modularized files. Port only the MOD-specific behavior and necessary dependencies. Do not restore removed Fold7 UI wording.

## Branch safety and validation

Work only on `TEST-upstream-180-integration` until an integrated commit builds and MOD status-bar behavior is verified. Keep `TEST` and `main` unchanged until then. CI run #451 succeeded on the pre-integration baseline SHA 159171af716ba452cd6f9ddaaa50b16d6cae0a2e and does **not** validate upstream integration. Draft PR #9 currently has merge conflicts. A new APK must not be described as upgraded until the integrated source is committed and its CI succeeds.
