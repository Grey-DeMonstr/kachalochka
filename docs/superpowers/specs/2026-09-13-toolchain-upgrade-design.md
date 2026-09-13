# Toolchain upgrade: AGP 9, Gradle 9.7, Compose Multiplatform 1.12

**Date:** 2026-09-13

Moves the build onto current tooling. The version bumps are routine; the module layout change is
not, and it is the reason this design exists.

---

## 1. Why the layout has to change

AGP 9 removes the ability to apply `com.android.application` or `com.android.library` beside
`org.jetbrains.kotlin.multiplatform` in one module. The replacement for a library is
`com.android.kotlin.multiplatform.library`, which declares its Android target inside the
`kotlin { }` block. There is no multiplatform equivalent for an application: an APK is still built
by `com.android.application`, which cannot be a Kotlin Multiplatform module.

So the Android application becomes its own Gradle module, and the shared Compose code stays a
library. This is the layout JetBrains' AGP 9 migration guide prescribes, and it is why the change
is a restructure rather than a version bump.

## 2. Target versions

| Component | From | To |
|---|---|---|
| Gradle wrapper | 8.14.5 | 9.7.0 |
| AGP | 8.13.2 | 9.3.1 |
| Compose Multiplatform | 1.11.1 | 1.12.0 |
| `androidCompileSdk` | 36 | 37 |
| `lifecycle` | 2.9.6 | 2.11.0 |

Kotlin stays at 2.4.20; its documented window reaches Gradle 9.7.0 and AGP 9.3.1. `navigation`
stays at 2.9.2 because 2.10 is still beta.

Gradle 9.7 and AGP 9.3.1 land in one commit: AGP 8.13.2 does not run on Gradle 9.6 or later, so
neither half is buildable without the other. `compileSdk` 37 follows in the next commit, because
AGP 8 caps it at 36.

## 3. Module layout

```
core/        KMP library. com.android.kotlin.multiplatform.library. Targets android, jvm, wasmJs.
app/         KMP library. Compose UI, view models, DI wiring. Targets android, jvm, wasmJs.
androidApp/  com.android.application. Depends on :app. Produces the APK.
```

`androidApp` owns everything that only an APK has: the launcher manifest, `MainActivity`,
`KachalochkaApplication`, the signing config, `versionCode` and `versionName`. The KMP library
plugin has no build types, so a signing config has nowhere to live in `app`.

`app/src/androidMain` keeps the code that is Android-specific but not application-specific:
`PlatformModule.android.kt` and `DataStoreThemePreference`. Its manifest shrinks to the INTERNET
permission and declares no application element.

**Namespaces.** `androidApp` takes `monster.greyde.kachalochka`, matching its `applicationId`, so
the identity §6 of the technical spec records is the identity of the shipped APK. The `app`
library moves to `monster.greyde.kachalochka.app`, since two Android modules cannot share one
namespace. Kotlin package names are untouched; only the R-class namespace of the library moves.

`:app:assembleDebug` ceases to exist — a KMP library has no build types. The debug APK comes from
`:androidApp:assembleDebug`, and installing from `:androidApp:installDebug`.

**`sqlMain` in `core`.** The `sql` group in `applyDefaultHierarchyTemplate` uses `withAndroid()`
instead of `withAndroidTarget()`; the old call matches nothing under the KMP library plugin
(KT-80409), which would silently drop the Android half of the intermediate source set §3 of the
technical spec depends on.

`android.enableLegacyVariantApi` is not set. Its whole purpose is to defer this migration.

## 4. versionCode

`versionCode` currently comes from `GITHUB_RUN_NUMBER`, a per-workflow counter: it rises when any
release workflow runs, not when a version does, and a re-run of a failed release produces a higher
code for the same version. It is replaced by a derivation from the released version itself, passed
as `-PversionName=X.Y.Z` by the release workflow:

```
versionCode = major * 10000 + minor * 100 + patch
```

Monotonic for any version sequence that is itself ordered, and reproducible: the same tag always
builds the same code. With no `versionName` property — every local build — the values are a fixed
`versionCode = 1` and `versionName = "0.1.0"`, so a development build cannot be mistaken for a
release. The formula lives in `androidApp/build.gradle.kts`.

## 5. Deprecated APIs removed in the same work

- `KoinApplication(application = { … })` in `app/src/jvmMain`, `app/src/wasmJsMain` and the jvm
  test suite becomes `KoinApplication(config = koinConfiguration { … })`.
- `runComposeUiTest` in `app/src/jvmTest` becomes the v2 API.

Both are covered by the existing `:app:jvmTest` suite, which is the failing-first signal for this
step.

## 6. Documents that state the old shape

- Technical spec §1.1: the "held down by constraints" list describes constraints this work
  removes. The claim that lifecycle 2.11.0 breaks the desktop UI tests is false and goes.
- Technical spec §2: the three-module layout. §6: where signing and the launcher live. §8: the
  release APK path and how `versionCode` is derived.
- `CLAUDE.md`: the repository tree, and `installDebug` pointing at `androidApp`.
- `ci.yml` and `release.yml`: the new module path for both APK builds.

## 7. Verification

`.\gradlew check`, `.\gradlew :androidApp:assembleDebug` and
`.\gradlew :app:wasmJsBrowserDistribution` all green, on a machine with no emulator and no browser.
