# Kachalochka

Kotlin Multiplatform app to record gym results, share them with friends and view statistics.
Two targets from one codebase: **Android** (local-first, synced) and **Web** (Kotlin/Wasm,
online-only). Backend is Supabase; there is no custom server code.

## Environment

**Development is Windows-native.** Use `PowerShell` for all shell commands — not `Bash`.

The `Bash` tool runs inside WSL, where no JDK or Android SDK is installed and the repository sits
on a slow NTFS mount. `gradlew`, `adb` and the emulator all live on Windows, so call them through
`PowerShell`. CI runs the same Gradle tasks on Ubuntu.

# Repository

All development happens directly on `master` — no feature branches, no PRs.
`origin` is `git@github.com:Grey-DeMonstr/kachalochka.git` and `master` is pushed to it: GitHub
Actions verifies every push, publishes the web build to GitHub Pages, and pushing a `vX.Y.Z` tag
cuts a release with a signed APK. See `docs/technical_spec.md` §8.

## Issue backlog

`ISSUES.local.md` in the repo root is the user's git-ignored backlog: one `## N. Title — status`
heading per issue (`open` or `done`), the user's description below it. When the user asks to list
issues, show the open ones; when they report a new one, append it with the next number, worded
from the user's point of view. Mark an issue `done` once its fix is committed.

## Commits and releases

Commit titles are read by whoever scans `git log`, so make them **product-oriented**: a reader
should generally understand what changed. No `feat:`/`fix:` prefixes, no issue keys. The body
explains why, when the title cannot.

`changelog.txt` in the repo root is the user-facing history, newest version at the top. It is
written in a user's language, not from commit subjects, and `release.yml` publishes a version's
section as its release notes. It is only ever touched while cutting a release — use the `release`
skill, never bump a version or write an entry by hand.

## Commands

```powershell
.\gradlew check                          # canonical quality gate: ktlint + host tests
.\gradlew ktlintFormat                   # format all Kotlin files
.\gradlew :core:jvmTest :app:jvmTest     # host tests, no device needed
.\gradlew :androidApp:assembleDebug      # debug APK
.\gradlew :androidApp:installDebug       # install on the connected device / emulator
.\gradlew :app:wasmJsBrowserDevelopmentRun   # web app in the browser
.\gradlew :app:wasmJsBrowserDistribution     # production web bundle
supabase db push                         # apply supabase/migrations to the linked project
```

## Architecture

```
core/                 Kotlin Multiplatform library (android, jvm, wasmJs)
  src/commonMain/
    domain/           entities, value objects, repository interfaces, pure functions
    data/             SQLDelight schema + DAOs, Supabase gateways, sync engine
  src/sqlMain/        Local*Repository: SQLite + outbox sync (android and the test JVM)
  src/wasmJsMain/     Remote*Repository: Supabase PostgREST directly
  src/jvmTest/        all core tests (SQLDelight JVM driver, fake Supabase gateways)
app/                  Compose Multiplatform library (android, jvm, wasmJs)
  src/commonMain/     screens, view models, Koin modules, navigation graph
  src/androidMain/    Android platform bindings, camera contract, WorkManager sync job
  src/wasmJsMain/     browser entry point
  src/jvmTest/        Compose desktop UI tests with fake repositories
androidApp/           Android application: manifest, MainActivity, signing, versioning
supabase/migrations/  Postgres schema, RLS policies, newsfeed view
gradle/libs.versions.toml   the only place library versions are declared
```

## Tech Stack (decided — do not substitute)

| Concern | Choice |
|---------|--------|
| Language | Kotlin 2.x on JDK 21 |
| UI | Compose Multiplatform |
| Navigation | Jetpack Navigation Compose (multiplatform) |
| DI | Koin — no codegen |
| Local DB | SQLDelight (Android only; web has no local DB) |
| Backend | Supabase via `supabase-kt`: auth, postgrest, storage, realtime |
| HTTP | Ktor client — OkHttp engine on Android, JS engine on Wasm |
| Serialization | kotlinx.serialization |
| Time | `kotlin.time` from the standard library |
| Images | Coil 3 |
| Charts | Vico |
| Lint / format | ktlint Gradle plugin |
| Tests | kotlin.test, kotlinx-coroutines-test, Turbine |

## Key Constraints

- **TDD is mandatory.** Write the failing test first, then implement.
- **Host tests only.** `.\gradlew check` runs everything; no emulator, device or browser is needed
  to pass it.
- **`core` never imports Compose; `domain/` imports nothing outside the Kotlin standard
  libraries.** Both must stay testable as plain libraries.
- **`.\gradlew check` must pass** before any task is considered complete.
- **Every synced table exists twice**: in SQLDelight (`core`) and in `supabase/migrations/`. A
  column is added to both in the same commit.
- **Supabase URL and anon key are never committed.** They come from `local.properties` locally
  and from repository secrets on CI.
- **Android minSdk >= 29.** `applicationId` / `namespace`: `monster.greyde.kachalochka`.

## Project spec

Two documents, both describing the app as built:

- `docs/functional_spec.md` — what the app does. No code.
- `docs/technical_spec.md` — how it is built and the decisions new work must respect.

Feature designs produced while brainstorming go to `docs/superpowers/specs/`. All noticeable
features are added to the two specs before implementation to keep them consistent.

# Generic code conventions

These apply to **every file in the repository**, whatever the language, as well commit messages and
pull request descriptions.

- Wrap at **99 columns**: comment text, like code, and Markdown documents and pull request
  descriptions too. clang-format shortens a long comment line but never widens a short one, so a
  comment wrapped at 80 stays wrong forever.
- Keep comments **as short and clear as possible**. Avoid long texts. If a comment needs a
  paragraph, the code usually needs a better name instead.
- **Never write about what was not done or what does not exist** — no absences ("deliberately not
  listed here", "left at its default"), no changes ("as it used to", ticket keys, commit hashes),
  no rejected alternatives. See *What deserves a comment* section below.

## What deserves a comment

- **Comment why, never what.** If the comment can be derived by reading the line below it, delete
    it. A declaration whose name already says what it is needs no comment at all - silence is the
    correct amount of documentation for an obvious thing.
- **Do not document what the code does not do.** No "X is deliberately not listed here", "there is
    no matching Y", "Z is left at its default", "no DEPENDS on W because...". An absence cannot be
    misread, because there is nothing there to read; explaining it only creates a comment that will
    rot. The rare exception is a default whose value something else depends on - then state the
    dependency in one line, not the reasoning behind mentioning it.
- **Comment the code, not the change.** No "as it used to", "now that", "previously", "this used to
    live in". No issue keys, commit hashes, or implementation-plan step numbers - that history goes
    in the commit message, where it stays accurate.
- **Length is a signal.** More than ~6 lines of comment above one declaration means the rationale
    belongs in a design doc under `docs/`, or the code needs a clearer name. Do not repeat the same
    rationale in two places; put it where the reader will be standing when it matters.
