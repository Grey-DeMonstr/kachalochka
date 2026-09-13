# Scaffold — design

**Date:** 2026-09-13

A walking skeleton for Kachalochka: every architectural seam named in
[technical_spec.md](../../technical_spec.md) built, wired and proven by a test, with no product
feature implemented. The goal is that the first feature is written entirely in `domain/` and
`app/`, because everything under it already works.

---

## 1. Scope

In scope:

- Gradle build for both modules and all three targets, with the version catalog as the only
  place versions are declared.
- Koin, Navigation, Compose Multiplatform theming, SQLDelight and the supabase-kt client wired
  end to end, each exercised by a host test.
- Supabase configuration read from `local.properties` or the environment, absent by default.
- The base Postgres migration and the local sync bookkeeping tables.
- The three GitHub Actions workflows from §8 of the technical spec.

Out of scope: every use case in the functional spec. Google sign-in is wired as far as a
signed-out auth client and no further; the sign-in flow is its own feature.

---

## 2. Build layout

```
settings.gradle.kts          plugin and dependency repositories, includes :core and :app
gradle/libs.versions.toml    every version
gradle.properties            AndroidX, configuration cache, Kotlin daemon memory
gradlew, gradlew.bat         wrapper checked in; CI uses the same one
core/build.gradle.kts        KMP library: androidTarget, jvm, wasmJs(browser); SQLDelight
app/build.gradle.kts         KMP application: the same targets plus Compose Multiplatform
```

Library versions are chosen at implementation time and confirmed by running the build, not by
recall. A version that does not resolve is a bug found in minutes; a version assumed correct is a
bug found much later.

---

## 3. Source sets

`core` uses an intermediate source set shared by the Android target and the test-only JVM target:

```
commonMain    domain entities, value objects, repository interfaces, pure functions
sqlMain       SQLDelight schema, DAOs, Local*Repository, sync engine      android + jvm
androidMain   Android SQLDelight driver, WorkManager entry point
jvmMain       JVM SQLDelight driver
wasmJsMain    Remote*Repository over Supabase PostgREST
jvmTest       every core test
```

`sqlMain` exists because §7 requires the DAOs and the sync algorithm to be tested on the host
JVM, and a source set only visible to the Android target cannot be. Keeping SQLDelight out of
`commonMain` is equally forced: the Wasm target has no local database.

This amends §3 of the technical spec, which places `Local*Repository` in `androidMain`. Updating
that section is part of this work.

`app` mirrors the shape:

```
commonMain    screens, view models, navigation graph, theme, Koin modules
androidMain   Activity, camera contract, WorkManager sync job
jvmMain       desktop entry point, used only by :app:jvmTest
wasmJsMain    browser entry point
jvmTest       Compose desktop UI tests
```

---

## 4. Supabase configuration

A Gradle task in `core` generates `SupabaseConfig.kt` into a generated `commonMain` source
directory. It reads `SUPABASE_URL` and `SUPABASE_ANON_KEY` from `local.properties`, then from the
environment, and falls back to empty strings.

Empty values produce a client that is constructed but never reached, so the build and
`gradlew check` succeed on a machine with no Supabase project. CI supplies the same two names
from repository secrets, so there is one code path and no committed credential.

---

## 5. What the skeleton contains

**Theme.** One `KachalochkaTheme` in `app/src/commonMain` declares both `ColorScheme` values and
is the only place a colour literal appears. `ThemeMode` is `System`, `Light` or `Dark`, resolved
against `isSystemInDarkTheme()`, and persisted behind a single interface with a DataStore actual
on Android and a `localStorage` actual on web.

**Navigation and DI.** A two-destination navigation graph and a view model resolved from Koin.
Two destinations rather than one, so that navigation is exercised rather than merely configured.

**Placeholder screen.** The app name, a working theme toggle, and one value that reached the UI
from `core` through Koin.

**Persistence.** SQLDelight tables for `outbox` and `sync_state`. Both are sync bookkeeping from
§4.2, so nothing here is discarded when features arrive.

**Backend schema.** `supabase/migrations/0001_init.sql` creates `profile` with the §4.1 identity
columns and its row-level security policies. It is written and source-controlled, not pushed.

**Tests.** `:core:jvmTest` round-trips an outbox entry through the SQLDelight JVM driver.
`:app:jvmTest` asserts that toggling the theme changes the resolved colour scheme. Both are
written before the code they cover.

**CI.** `ci.yml` runs `check`, `assembleDebug` and `wasmJsBrowserDistribution` on JDK 21;
`pages.yml` publishes the Wasm distribution on every push to `master`; `release.yml` builds a
signed APK and creates a release on a `v*` tag.

---

## 6. Done means

- `.\gradlew check` passes: ktlint, `:core:jvmTest`, `:app:jvmTest`.
- `:app:assembleDebug` produces an APK.
- `:app:wasmJsBrowserDistribution` produces a bundle.

The last two are verified locally, not inferred from `check`. Target-specific assembly is where a
multiplatform scaffold fails, and `check` does not touch it.
