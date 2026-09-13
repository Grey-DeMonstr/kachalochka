# Kachalochka — Technical Specification

**Last reviewed:** 2026-09-13

The architectural decisions and invariants new work must respect. It is not a description of the
current code — read the code for that. What is written here is what the code cannot tell you: why
a seam exists, what breaks if it is crossed, and which rules are load-bearing rather than
incidental.

For what the app does, see [functional_spec.md](functional_spec.md).

---

## 1. Platform

One Kotlin codebase produces both deliverables:

| Target | Delivery | Persistence |
|---|---|---|
| Android | APK from GitHub Releases, later Google Play | Local-first: SQLite, synced to Supabase |
| Web | Kotlin/Wasm bundle on GitHub Pages | Online-only: reads and writes Supabase directly |

The functional spec requires offline work only on Android, so the web build carries no local
database. That single decision keeps the web target on the well-trodden part of the toolchain:
Compose for Web on Wasm is Beta and browser-side SQLite for Wasm is not. If offline web is ever
wanted, it is a new repository implementation behind an existing interface (§3), not a rewrite.

Kotlin Multiplatform and Compose Multiplatform are the framework; Supabase is the backend. No
custom server code exists: Postgres, row-level security, storage and realtime cover every
backend requirement in the functional spec.

### 1.1 Technology choices — do not substitute

| Concern | Choice | Why it is fixed |
|---|---|---|
| Language | Kotlin 2.x, JDK 21 | JDK 21 is the floor for current AGP and Gradle |
| UI | Compose Multiplatform | Same UI code on Android and Wasm |
| Navigation | Jetpack Navigation Compose (multiplatform) | Type-safe routes, JetBrains-owned |
| DI | Koin | No codegen, works on Wasm |
| Local DB | SQLDelight | SQL-first, generated type-safe queries, JVM driver for host tests |
| Backend client | supabase-kt (auth, postgrest, storage, realtime) | Supports Android and Wasm |
| HTTP engine | Ktor: OkHttp on Android, JS engine on Wasm | Required by supabase-kt |
| Serialization | kotlinx.serialization | Required by supabase-kt |
| Time | `kotlin.time` from the standard library | |
| Async | kotlinx.coroutines + Flow | |
| Images | Coil 3 | Multiplatform, loads Supabase Storage URLs |
| Charts | Vico | Compose Multiplatform support |
| Lint/format | ktlint via Gradle plugin | Runs inside `gradle check` |
| Tests | kotlin.test, kotlinx-coroutines-test, Turbine | Host JVM only |

Versions live in `gradle/libs.versions.toml`, the single place they are declared. Four of them are
held down by constraints a routine bump walks straight into:

- **Gradle 9.7.0 and AGP 9.3.1** are the top of the window Kotlin 2.4.20 documents. AGP is also a
  floor: 9.0 removed `com.android.library` beside `org.jetbrains.kotlin.multiplatform`, which is
  why §2 has three modules.
- **`navigation` 2.9.2**, while 2.10 is in beta.
- **`compileSdk` 37 is a floor, not a ceiling.** Compose Multiplatform 1.12.0 and `lifecycle`
  2.11.0 both require it, and only AGP 9 accepts it.
- **`kotlinx-browser` is a wasmJs-only dependency**, since `kotlinx.browser.localStorage` lives
  outside the wasmJs standard library and §10 keeps the theme mode there.

Gradle's Kotlin DSL rejects a build script that uses a deprecated Gradle API, so the `by
registering` and `by getting` delegates are out: build scripts name their tasks and source sets
through `register` and `named`.

---

## 2. Modules and layering

```
core/        Kotlin Multiplatform library: domain, data, sync. Targets android, jvm, wasmJs.
app/         Compose Multiplatform library: screens, view models, DI. Targets android, jvm, wasmJs.
androidApp/  Android application. Depends on app. Produces the APK.
```

`core` and `app` are both Kotlin Multiplatform libraries, and `androidApp` exists because an APK
cannot be one: AGP 9 will not apply `com.android.application` to a multiplatform module. It holds
only what an APK has and a library does not — the launcher manifest, `MainActivity`, the
`Application` subclass, the signing config and the version numbers. Android code that is not
application code, such as the DataStore theme preference, stays in `app/src/androidMain`.

`core` and `app` have a `jvm` target for one reason: tests. `core` tests run on the host JVM with
the SQLDelight JVM driver; `app` tests run as Compose desktop UI tests. Neither needs an emulator
or a browser, and the `jvm` target of `app` is never shipped.

The `sql` group in `core` selects its Android half by platform type. `withAndroidTarget()` matches
only the old plugin's target type, so under the multiplatform library plugin it silently matches
nothing and `sqlMain` loses the Android compilation it exists to serve (KT-80409).

Inside `core`:

```
domain/  entities, value objects, repository interfaces, pure functions
data/    SQLDelight schema and DAOs, Supabase gateways, sync engine
```

Three rules:

1. **`domain/` depends on nothing outside the Kotlin standard libraries.** Interfaces are declared
   there, implementations live in `data/`, and `app` wires them through Koin. A `domain/` file
   importing `data/`, Supabase, SQLDelight or Koin fails `:core:jvmTest`.
2. **`core` never imports Compose.** It must stay compilable and testable as a plain library.
3. **Logic that can be a pure function must be one.** Weight totals, per-limb doubling, negative
   machines, suggested next set, period statistics — all are functions in `domain/` with their own
   tests. `app` renders results; it does not compute them.

---

## 3. Repositories: one interface, two implementations

Every repository interface in `domain/` has exactly two implementations:

| Implementation | Source set | Backing |
|---|---|---|
| `Local*Repository` | `sqlMain` (android + jvm) | SQLDelight plus the outbox sync engine |
| `Remote*Repository` | `wasmJsMain` | Supabase PostgREST directly |

`sqlMain` is an intermediate source set, declared through `applyDefaultHierarchyTemplate`, shared
by the Android target and the test-only JVM target: §7 requires the DAOs and the whole sync
algorithm to be covered by host tests, which a source set visible only to the Android target
cannot be. SQLDelight stays out of `commonMain` because the Wasm target has no local database.

The SQLDelight Gradle plugin attaches its generated sources to `commonMain`, so
`core/build.gradle.kts` excludes them there and declares them on `sqlMain` in an `afterEvaluate`
block; the compiler rejects a file claimed by two source sets. That block must stay registered
after the `sqldelight { }` block, because the plugin wires its own end up in an `afterEvaluate` of
its own. It excludes by pattern rather than reassigning `srcDirs`: reassigning resolves the
directory set to plain files and discards the task dependency every generated source dir carries,
which leaves the generator unrun on a cold build.

`app` sees only the interface. This is the seam that lets Android be offline-first and the web be
online-only with identical UI code. It is also why domain types must be serialization-neutral:
the same entity is written to SQLite by one implementation and posted as JSON by the other.

---

## 4. Data model and sync

### 4.1 Identity and change tracking

Every synced row carries:

| Column | Rule |
|---|---|
| `id` | UUID v4, generated on the client at creation. Never reassigned |
| `user_id` | Owner. Null while the Android user is not logged in (§4.3) |
| `updated_at` | UTC instant, set by the writer on every change |
| `deleted` | Soft-delete flag. Rows are never physically deleted by clients |

Ids are value classes in `domain/` — `ProfileId`, `UserId` — and each rejects anything but a
lower-case UUID v4 at construction. Postgres declares the columns `uuid` and folds the text form
to lower case, so a free string would let the two implementations disagree: an id the local
SQLite happily reports as missing is a type error the server raises instead. Validating at the
boundary of `domain/` gives both the same answer, on the same exception, before a query runs.

The null `user_id` is a local state only: a row is stamped with its owner before it can enter the
outbox, so Postgres declares the column `not null`. A nullable server column would admit rows that
match no row-level-security policy — invisible to every client, including whatever would have to
clean them up.

Client-generated ids are what make offline creation possible: a visit and its sets are linked
before the server has ever seen them. Soft deletes are what make sync convergent: a delete is
just another update that travels the same path.

### 4.2 Sync algorithm (Android only)

- **Push.** Every local write appends the row's id and table to an `outbox`. A sync pass upserts
  each outbox entry to Supabase and removes it on success. Failure leaves it for the next pass.
- **Pull.** The sync pass then fetches every row with `updated_at` later than the last pull
  watermark and upserts it locally, skipping rows that have a pending outbox entry.
- **Conflicts** resolve by last-write-wins on `updated_at`. The data is single-user per row and
  edits are rare, so a merge strategy would be cost without benefit.
- **Triggers.** A pass runs on app start, on connectivity gain, and after the user ends a visit.

Sync is a `data/` concern. Nothing in `domain/` or `app` knows whether a row has been pushed.

### 4.3 Anonymous use and first login

Android works without an account: rows are created with a null `user_id` and never pushed. On
first successful login, every row with null `user_id` is stamped with the user's id and enqueued
in the outbox. Logging out keeps the local data; logging in as a different user is refused while
rows belonging to another user exist.

### 4.4 Photos

Photo bytes are not rows. Android stores the file in app-private storage under the photo's id
and records a `photo` row pointing at it; the sync pass uploads the file to the `photos` Storage
bucket at `<user_id>/<photo_id>` before pushing the row. Web uploads directly. Coil loads
displayed photos from the local file on Android and from a signed Storage URL on web.

---

## 5. Backend

### 5.1 Schema ownership

The Postgres schema is source-controlled as SQL migrations under `supabase/migrations/`, applied
with the Supabase CLI. The dashboard is never used to alter schema. The SQLDelight schema in
`core` mirrors the synced tables column for column; a column added to one is added to the other
in the same commit.

### 5.2 Access rules

Row-level security enforces every visibility rule from the functional spec:

- A user reads and writes only rows with their own `user_id`.
- A group member reads visits, exercises and photos of every other member of the same group.
- Group membership itself is readable by members and writable by the group owner.

Because visibility is enforced in Postgres, no client code path can leak data by omission.

### 5.3 Group newsfeed

The feed is a Postgres view over ended visits joined with group memberships, ordered by visit
end time. A view is dynamic by construction: when a user edits an old visit, every member's feed
reflects it on the next read with no fan-out or denormalised feed table. Live updates come from a
Realtime subscription on the `visit` table filtered by the viewer's groups.

### 5.4 Configuration and secrets

The Supabase project URL and anon key are read from `local.properties` on developer machines and
from repository secrets on CI. They are never committed. The anon key is safe to ship inside the
built app because RLS, not the key, is the access boundary.

---

## 6. Android specifics

- `applicationId` and the `androidApp` namespace: `monster.greyde.kachalochka`. The `app` and
  `core` libraries take namespaces below it, since two Android modules cannot share one.
- `minSdk` 29. Scoped storage is mandatory above it, so photo handling has one code path.
  `compileSdk` 37.
- Signing, `versionCode` and `versionName` live in `androidApp`: the multiplatform library plugin
  has no build types, so there is nowhere else for a signing config to go.
- Koin starts once, in an `Application` subclass. A graph built per Activity constructs a second
  DataStore over the same file on configuration change, and DataStore rejects that.
- Camera capture uses the platform take-picture activity contract; no camera library.
- The sync pass is a WorkManager job so it survives the app being backgrounded.

---

## 7. Testing and the quality gate

- **TDD is mandatory.** The failing test is written before the implementation.
- `core` tests run under `:core:jvmTest` with the SQLDelight JVM driver and fakes for the Supabase
  gateways. They cover every pure function, every DAO and the whole sync algorithm.
- `app` tests run under `:app:jvmTest` as Compose desktop UI tests, with Koin overrides supplying
  fake repositories. No test touches the network or a real database.
- `./gradlew check` is the quality gate: ktlint, `:core:jvmTest`, `:app:jvmTest`. It must pass
  before any task is considered complete.

Any UI test that mounts a `NavHost` runs through `runNavigationUiTest` in `app/src/jvmTest`.
Navigation's back-stack entries need a `LifecycleOwner`, which `runComposeUiTest` does not
provide, and `androidx.lifecycle` asserts it is on the main thread, which the test thread is not.
`runNavigationUiTest` runs the test and supplies both, so neither can be omitted.

---

## 8. Build, CI and release

- `master` is the only long-lived branch and every push to it runs CI.
- **`ci.yml`**: JDK 21, `./gradlew check`, `:androidApp:assembleDebug`,
  `:app:wasmJsBrowserDistribution`.
- **`pages.yml`**: on push to `master`, publishes the Wasm distribution to GitHub Pages.
- **`release.yml`**: on a `v*` tag, builds a signed release APK from secrets and creates a GitHub
  Release with the APK attached. `versionName` comes from the tag and `versionCode` is derived
  from it as `major * 10000 + minor * 100 + patch`, so rebuilding a tag reproduces the number it
  shipped; the release notes are that version's section of `changelog.txt`, and a tag whose
  version has no section fails before the build.
- `changelog.txt` in the repo root is the user-facing history, newest version at the top, plain
  ASCII. One release is one commit adding a section, one annotated `vX.Y.Z` tag, and a push —
  `master` first, then the tag.
- Gradle runs with the configuration cache and the wrapper checked in; CI uses the same wrapper.

---

## 9. Data import and export

The interchange format is a single JSON document produced by kotlinx.serialization from the domain
entities, with photos referenced by id and packed alongside the document in a zip. Reading it back
goes through the same repositories as any other write, so imported rows sync like local ones.

---

## 10. Theming

Light and dark are both first-class (see the functional spec). One `MaterialTheme` wrapper in
`app/src/commonMain` owns both `ColorScheme` values and is the only place colours are declared:
screens read `MaterialTheme.colorScheme`, never a literal `Color`. That rule is what makes both
themes correct by construction instead of by review.

The effective theme comes from a `ThemeMode` of `System`, `Light` or `Dark`, resolved against
`isSystemInDarkTheme()`. The mode is a device setting, not user data: it is stored per platform
behind one interface in `app`, backed by DataStore on Android and `localStorage` on web, and it
stays out of the sync model and out of `core`. The interface exposes the mode as a `StateFlow`
whose value is already the stored one when the graph is built, so the first frame is painted in
the chosen scheme. A store that cannot be read or written falls back to `System` instead of
failing the launch.

Vico charts and any Compose `Canvas` drawing take their colours from the same scheme, so the
chart surfaces follow the theme along with everything else.
