# Kachalochka — Technical Specification

**Last reviewed:** 2026-09-28

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
| Sign-in | Credential Manager (Android), OAuth redirect (web) | Both yield a Supabase session |
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

Friends' data is the exception: both platforms read it online, so `FriendsRepository` has one
implementation in `commonMain`, and nothing it returns is written to SQLite. The web runs it on
the UI client. Android runs it on a client that, like the sync client (§4.2), asks
`AccountTokens` for the active account's token on every request, because its UI client holds a
session only after a sign-in or a switch in the same process. Concurrent refreshes of one
account's token share one request. The active account's groups are read ahead into a
process-wide `GroupsCache` whenever it becomes active, so the friends screen opens with them.

The calendar reads group mates' visits for the shown month with one `groupVisits` request, far
under PostgREST's row cap. It reads them after its own rows, again on entering the screen, after
a sync, after an account switch and on a month change, and never stores them. A read in flight
is cancelled by the next one, and a result that lands for another account or month than the one
shown is dropped. A failed read leaves friends out; the own calendar never waits for it.

Text leaves the app through `TextSharing`, bound per platform in `platformModule()`: Android's
`ShareSheetTextSharing` opens the system share sheet and returns no notice; the web's
`ClipboardTextSharing` writes the clipboard and returns the notice the screen shows. Invites use
the same platform call. A shared visit's text comes from the pure `visitShareText` in
`app/ui/format`. `VisitViewModel` reads the nickname ahead of the tap, because a browser accepts
a clipboard write only shortly after the user's gesture.

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

Reads take their owner as an argument. One Android database holds the rows of every account
signed in on the device (§4.3), so a repository that resolved the owner from ambient state would
scope twice on the web, where row-level security already filters, and would turn a test that
forgot to set the active account into an empty list instead of a failure.

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

Each account keeps one `profile` row. A signed-in owner's new profile takes the owner's own id as
its `id` (`Profile.new`), so two devices creating it offline converge on one row; an anonymous one
takes a random id. Readers take the owner's live profile with the newest `updated_at`, the
greatest `id` breaking a tie (`ProfileRepository.forOwner`, and `member_display_name` on the
server), and every profile write updates the row it returned, so an owner who ends up with two
after a claim (§4.3) still reads one, the same one on every client. `friend_colors` is a JSON
object in a text column, `'{}'` when empty; an entry that cannot be read is dropped rather than
failing a pull.

### 4.2 Sync algorithm (Android only)

- **Push.** Every local write appends the row's id and table to an `outbox`, which names only the
  table and the row, not its owner. A pass reads each row to learn who owns it: an entry for
  another account's row waits for that account's own turn, and an entry whose row is gone or
  unowned is dropped. A pass pushes entries by table rank: `machine`, `visit`, `profile`,
  `measure`, `machine_link`, `workout_set`, then `measurement`, because the server checks a set's
  visit and machine, which the local SQLite does not. The server checks nothing a link or a
  measure's value names, but each follows the rows it names, so a reader never meets it before
  them. An entry is removed after a successful push only if nothing re-enqueued it in the
  meantime.
- **Pull.** The sync pass fetches every row of every table newer than the account's pull
  watermark, keyset-paged on `(updated_at, id)` using the values the server returned for the last
  row of the previous page, and stops once a page comes back empty. It writes nothing, and leaves
  the watermark alone, unless every table's pull succeeds; rows with a pending outbox entry are
  skipped. The watermark then advances to the newest `updated_at` pulled.
- **Conflicts.** A row waiting in the device's outbox wins over the server's copy: the pull skips
  it and its push overwrites the server's. Otherwise the server's copy wins on pull. Every row
  belongs to one person and edits are rare, so a merge strategy would be cost without benefit.
  The one exception is a `measure` older than the server's copy (§4.6).
- **Triggers.** A pass runs whenever the app comes to the foreground (a `ProcessLifecycleOwner`
  `ON_START` observer, which also fires at launch), when the app goes to the background, after an
  account is added, after a visit is moved, replaced or removed on the calendar, and after any
  write to a visit or measurement of a day other than today, and after visit normalization wrote
  something. It is a WorkManager job — unique work `"sync"` — with a network constraint, so a
  pass already queued waits for connectivity rather than failing outright. A pass reports whether
  every push and pull succeeded, and one that did not is retried with exponential backoff. A new
  request replaces (`REPLACE`) whatever is queued or running, so it never waits behind a pass
  sitting out its backoff; cancelling a running pass is safe, because its outbox entries stay and
  every push is an upsert.
- **Tokens.** The account live on the UI client lends its own access token to the pass while it
  has more than a minute left; nearer its expiry the pass has the UI client refresh its own
  session with `refreshCurrentSession`, because the rotated refresh token that client holds would
  be spent by a refresh from anywhere else. Every other account refreshes through
  `refreshSession`, and the session that comes back is written to the store before use. A refresh
  the server refuses with a 4xx other than 429 leaves that account unsynced for the pass; a 429
  or a network failure fails the pass instead.
- **Every account.** A pass covers every account signed in on the device, not only the active
  one. `syncState` holds one row per `user_id`, created by the first local schema migration
  (`1.sqm`), so each account has its own pull watermark. Pushing only the active account would
  leave a guest's sets enqueued until somebody happened to switch back to them.

Sync runs on a second `SupabaseClient` that installs no `Auth`; its `accessToken` resolver asks
for the token of the account the pass is currently on. The UI's client and its active session are
never touched. Importing each account's session in turn on the one client would race every write
the UI makes meanwhile.

Sync is a `data/` concern. Nothing in `domain/` or `app` knows whether a row has been pushed.
The screens that read their rows once — home, the visit, the calendar, the machine picker and the
machine list — reload when `SyncTrigger.completed` emits, which the worker does after every pass,
so rows a pass pulled show up on the screen already open. On the web, where every write has
already reached the server, `ServerSyncTrigger` emits on every request instead.

### 4.3 Anonymous use and several accounts

Android works without an account: rows are created with a null `user_id` and never pushed. On the
first successful sign-in, every row with null `user_id`, the anonymous profile included, is
stamped with that user's id and enqueued in the outbox. Accounts added afterwards claim nothing
and start empty.

Several accounts are signed in at once and one of them is active; the active one owns whatever is
recorded now. Their rows share one local database, which is why reads are scoped by owner (§3)
and why a pull watermark is per account (§4.2). Signing out drops the session and keeps the rows,
so signing back in finds them again.

Friends' calendar colours belong to the account, not the device: they live in its profile's
`friend_colors` (§4.1), keyed by friend, so each account sees its own and they sync to its other
devices. `FriendColorStore.colorsFor` only reads: a friend without a stored colour gets one from
the pure `assignedColors` over the stored map (empty without a profile) and the friends sorted by
id, seeded by an FNV-1a hash of the owner's id. The same owner, stored map and friends so give the
same colours on every screen and platform, and a drawn colour needs no write: a device that has
not pulled the account's profile yet would push its row over the server's under outbox-wins
(§4.2), wiping the nickname and colours chosen elsewhere. Only a colour the user picks is saved,
creating the profile when there is none. A new friend may shift the others' drawn colours, never
a stored one.

The avatar sits in every screen's top bar, so the active account can change under any screen. No
screen-scoped view model carries a row across that change: each observes the store's active id and
re-resolves what it holds for the account that became active, and a write that records something
new re-checks that this account owns the row it reaches for, mirroring it (§4.5) rather than
writing across the boundary. Correcting a row already recorded keeps that row's own owner and
visit, because the correction belongs to what is being corrected. A form keeps the edits the user
typed — only the row underneath them is replaced.

Sessions are held by an account store in `core/data/identity`, not in `domain/`, which may not
import kotlinx.coroutines and so cannot expose the `StateFlow` the UI observes. It is persisted
per platform behind one interface, as the theme mode is (§10): DataStore on Android,
`localStorage` on web, in memory for tests. A switch calls `importSession` on the UI client, so
one session is live at a time even though several are stored, and supabase-kt's own session
storage is turned off — it holds a single session and would contend for the same slot. So is its
hook that reloads that storage whenever the app returns to the foreground: finding it empty, it
would end the live session. As a second guard, an end the library reports right after it starts
reloading signs nobody out.

supabase-kt refreshes the live session on its own; each refresh is written back to the store
through `LiveSession`, which records the account it put live only once `importSession` for it has
actually succeeded. When supabase-kt instead clears a session it could not refresh, `LiveSession`
signs that account out of the store — the store never names an account the server has stopped
accepting. A clear the app makes itself, such as an ordinary sign-out, does not trigger this. If
activating the next account to take the vacated slot itself fails, the store is left with nobody
active and the live session cleared; the account that failed to activate stays listed, so
switching to it again is a retry rather than adding it back.

`CurrentUser` is one `commonMain` implementation reading that store's active id, on both targets.

### 4.4 Photos

Photo bytes are not rows. Android stores the file in app-private storage under the photo's id
and records a `photo` row pointing at it; the sync pass uploads the file to the `photos` Storage
bucket at `<user_id>/<photo_id>` before pushing the row. Web uploads directly. Coil loads
displayed photos from the local file on Android and from a signed Storage URL on web.

### 4.5 Gym data

Four synced tables: `machine`, `visit`, `workout_set` and `machine_link`. `workout_set` is not
called `set` — a keyword in both SQLDelight's dialect and Postgres. Weights are `Double`; every
step and every typed weight is rounded to three decimals so a running total never drifts. The
weight-counting mode and the unit are enums, mapped to the wire names `total` / `per_side` and
`kg` / `lb` / `custom` by one shared mapping in `core/data/gym`, used by both implementations. A
name the mapping does not know reads as `total` or `kg`: clients before 1.0.2 still write
`counterweight`, and one unreadable row must not stop a pull. A custom unit's name is
`unit_label`, empty for kg and lb.

A `machine_link` row says its owner's machine `machine_id` is the same physical machine as another
member's `linked_machine_id`. Neither column is a foreign key and no policy checks either against
`machine`: a link may reach the server before its own machine does, and the linked machine is
someone else's. Links are undirected for reading: machines joined by live links, in either
direction and through any number of hops, form a cluster that counts as one physical machine.
`MachineClusters` in `domain/gym` builds the clusters from a list of links by union-find, and
every reader asks it: the picker and the machine list offer one friend's machine per cluster
without an own machine (`friendMachineRows`, the machine with the fewest outgoing links, then by
owner name and id), the set sheet asks `FriendsRepository.latestOn` for the friends' machines of
the open machine's cluster, and a friend's visit names each machine after the viewer's own in its
cluster (`namesForViewer`). The clusters combine the account's own links, read locally, with its
group mates' live links, read online through `FriendsRepository.groupLinks`. Picking a friend's
machine, or "Взять себе" on one opened from the machine list, writes the own copy (`linkedCopy`)
and the link from it to the friend's machine. Friends' rows read for one account are never shown
or copied once another is active: each screen remembers whom it read them for.

The machine form's "Привязать к…" chooser (`LinkChooserViewModel`) links an own machine to a
friend's by writing one own link, and merges two own machines. A merge removes a duplicate and
writes no link: `olderMachine` keeps the machine whose earliest live set is earlier, one without
sets counting as newest and a tie keeping the edited one, and `mergedMachines` returns the rows to
write — the removed machine's sets and own links moved to the kept one (a link the kept one
already has, or one into it, is soft-deleted instead) and the removed machine soft-deleted.
Signed in, friends' links into the removed machine are moved first through
`FriendsRepository.repointLinks`, called only when `groupLinks` shows one; if the server does not
answer nothing is written. The rows are then written sets first and the machine last, and a sync
is requested. "Отвязать от друзей" breaks every direct link of the machine the same way round:
`FriendsRepository.breakLinks` first, then the own links touching it soft-deleted locally. The
form reads the links again whenever it is shown, and the set sheet reads friends' results again on
`refresh()` and after a sync pass, because the chooser changes links on another screen. A merge
started from a visit hands the kept machine to the visit's sheet, and the sheet closes on an own
machine that has been deleted rather than record a set on it.

1.0.2 linked machines by a shared key, `machine.link_id`, which stays in both schemas for its
clients. It is never written or read: `MachineRow` has no such field, so an upsert leaves the
server column as it is, and the SQLite column is left null by the upsert and ignored by the
mapper. Migration `0009` converted those keys once: live machines are grouped by
`coalesce(link_id, id)`, and in every group spanning two or more users, each machine of a user
other than the representative's links to the representative — the machine whose id is the key,
else the oldest by `updated_at`. Keys a 1.0.2 client writes later are not converted.

A visit is one account's calendar day: `visit.day`, with no start, end or duration, and the row
is created with the day's first set. `day` is nullable and unconstrained, because clients before
1.0.2 push visits without it and two devices may create the same day offline; `recorded_at` and
`ended_at` stay, written equal, so a client before 1.0.2 reads every visit as ended on its day.
Before a visit's `day` is set, readers place it on the day of its `recorded_at` instead
(`Visit.dayAt`), and where more than one visit falls on a day, show `keptVisit`'s pick: the newest
by `(recorded_at, updated_at, id)` (`visitRecency`) of those with a live set, or of all when none
has one (`VisitRepository.shownOn`).

`normalizedVisits` in `domain/gym` is the pure function that reconciles this: it fills a missing
`day` from `recorded_at` and, per day, keeps `keptVisit` of the visits — the newest by
`visitRecency` of those with a live set, of all of them when none has one — and soft-deletes the
rest with their sets; a second run over already-normalized rows writes nothing. `keptVisit` is
shared with `shownOn`, so a day's shown visit is always the one normalization would keep. Clients
before 1.0.2 wrote a visit on opening it, so an empty visit may follow the day's workout.
`VisitNormalizer` re-reads each visit before rewriting it and skips one whose `updated_at` changed
since it read the owner's visits; the next
run judges it again.

On Android normalization runs at start only for the anonymous owner; a signed-in account is
normalized only inside the sync worker, once that account's own pass comes back clean, and the
worker runs one more pass inline — never a new `request()` — when normalization wrote something,
while switching the active account requests a pass instead of normalizing directly. On the web it
runs against the server after the session restore and after every switch. A failure normalizing
one owner — at start, on a switch or in the worker — is logged (Android) or sent to the console
(web) without stopping the next owner's turn. In the worker it leaves the pass clean, since every
pass normalizes again; the worker logs a failed pass as well.

Moving a visit to another day moves its sets, and removing a visit soft-deletes its sets: every
reader of sets filters on the set's own `deleted` and `recorded_at` and never joins `visit`. Such
a rewrite writes the sets first and the visit last, and places each set by its clock time after
the visit's, so a retry after a half-finished write on the web writes the same rows. Replacing a
day's visit on the calendar removes every live visit on that day (`allOn`), so normalization
never weighs another against the moved one. A set added to a visit of another day is stamped one
second after the visit's last set, which keeps a late correction on the visit's day and in order.
Within a visit, sets sort by `(position, recorded_at, id)`; `position` defaults to 0, so rows
written before it keep their recording order. A reorder (`machineMovedTo`, `setMovedTo`)
renumbers the visit's sets 1..n in the new order and writes only those whose position changed, so
sets sharing a position still land in the dropped order.

Repositories stay suspend-only, because `domain/` may not depend on kotlinx.coroutines (§2) and
so has no `Flow` to expose. A view model that writes through a repository reloads afterward
instead of observing it.

`CurrentUser` supplies the owner stamped on a new row (§4.1): the active account, or null on
Android while nobody has signed in (§4.3). On the web a write with no owner is rejected by
row-level security (§5.2), which is why the web build requires an account before any route is
reachable.

### 4.6 Body measures

Two synced tables, private to their owner: `measure` (name, free-text unit, `kind`, `position`)
and `measurement` (`measure_id`, `day`, `value`). `kind` names a predefined measure by the wire
names `weight`, `waist`, `chest`, `hips`, `biceps`, `thigh`, `neck` and `body_fat`, mapped in
`domain/measures` because derived ids hash them; it is null for the user's own measure, and a
name this version does not know reads as null. `measurement.measure_id` is not a foreign key,
since a seeded measure may reach the server after its first value. `measurement.day` is a Postgres
`date`, the same ISO text as `visit.day`.

A measure has one value per day. Two devices may still write one day offline, so
`MeasurementRepository.all` reads every row of the owner, deleted ones included, keeps the one
with the newest `(updated_at, id)` of each measure and day (`newestPerDay`) and leaves the day out
when that row is deleted, so a cleared day stays cleared; newest day first.
`MeasureRepository.all` orders live measures by `measureOrder` — position, then name — in Kotlin
on both platforms.

The eight predefined measures are seeded by `missingDefaults` for every kind the owner has no
row of, live or deleted (`MeasureRepository.kinds`), so a deleted one stays deleted. A signed-in
owner's seeds take `derivedId(owner, kind)` — FNV-1a 64 of `"<owner>:<kind>"` under two offset
bases, shaped as a v4 UUID — and `updated_at` at the epoch, so two devices seeding offline write
the same rows. An anonymous owner's seeds take random ids; claimed by an account that already has
predefined rows elsewhere, both sets show and the user deletes one.

A seed is the one exception to outbox-wins (§4.2), on both sides. The trigger
`measure_keeps_newer` ignores an update of `measure` dated at the epoch when the stored row is
newer, so a late seed never undoes a rename made on another device; every other update applies.
On the device, a pulled `measure` replaces a pending local row dated at the epoch
(`MEASURE_SEEDED_AT`) and drops its outbox entry, so the rename lands even when the seed's push
failed in the same pass.

"Замеры" seeds on every load, one seeding at a time, since two concurrent seeds of an anonymous
owner would each add a full set. The "Замер" form writes only the fields whose value changed: the
day's newest row updated in place, or a new row; an emptied field soft-deletes that row, which
`newestPerDay` then reads as a cleared day.

The measure screen's periods (`MeasurePeriod`, `inPeriod`) count calendar months back from today,
starting on today's day of the month or the shorter month's last day. Its Vico chart puts each
value at its epoch day, so an irregular week keeps its true width, and fits the y range to the
values instead of Vico's default from zero, which would flatten a body weight. Deleting a measure
also soft-deletes the values `MeasurementRepository.all` returns for it; older rows of those days
stay hidden behind them through `newestPerDay`.

The body-fat formulas are pure functions in `domain/measures/BodyFat.kt` (`bodyFat`,
`missingInputs`); a result outside 2–70 % is treated as none. The form finds its inputs by
`MeasureKind`, never by name: a field's typed value, else the value its placeholder shows (the
latest before the form's day). Sex, birth year and height live in the profile; the user's save
re-reads `forOwner` and updates that row, or creates one with `Profile.new` when there is none,
as a picked friend colour does (§4.3), so the nickname and colours survive.

---

## 5. Backend

### 5.1 Schema ownership

The Postgres schema is source-controlled as SQL migrations under `supabase/migrations/`, applied
with the Supabase CLI. The dashboard is never used to alter schema. The SQLDelight schema in
`core` mirrors the synced tables column for column; a column added to one is added to the other
in the same commit.

The local SQLite database is recreated rather than migrated until the first release: a change
`core`'s schema makes unreadable is taken by clearing the app's data, and the migration written
is the Postgres one. From the first `vX.Y.Z` tag on, such a change ships a SQLDelight `.sqm`
beside it and moves `Schema.version`, because by then the rows belong to somebody.

A column a `.sqm` adds with `ALTER TABLE … ADD COLUMN` lands at the end of the table, so the
`.sq` declares it last too: the generated `SELECT *` mappers read columns by position. A wire
row declares no Kotlin default values, because supabase-kt encodes without defaults and an
upsert leaves a column it was not sent unchanged. Old clients keep working against a newer
schema only as far as their decoders allow: unknown columns are ignored, but a value they
cannot map, such as the unit `custom`, fails their pull.

### 5.2 Access rules

Row-level security enforces every visibility rule from the functional spec:

- A user writes only rows with their own `user_id`, and a set only into their own visit and on
  their own machine.
- A user reads their own rows and the live `machine`, `visit`, `workout_set` and `machine_link`
  rows of everyone who shares a live group with them, through the security-definer function
  `shares_group_with`. `profile`, `measure` and `measurement` stay readable by their owner alone:
  each has one owner-only policy and no group policy.
- A friend's link into one's own machine is changed only through two security-definer
  functions, called by `FriendsRepository.breakLinks` and `repointLinks`.
  `break_machine_links(machine)` soft-deletes the live links pointing at `machine`
  when the caller owns it on the server. `repoint_machine_links(removed, kept)` moves friends'
  live links pointing at `removed` to `kept` when the caller owns `removed` and nobody else owns
  `kept`; `kept` need not exist yet, since it may have been created offline.
- A group's current members are readable by its members. Membership changes only through the
  security-definer functions `create_group`, `join_group` and `leave_group`; the owner renames
  and soft-deletes the group directly, and a deleted group stays deleted.
- A member's `group_member.display_name` follows one rule, `member_display_name`: the nickname
  of their newest live profile when not blank, else the Google name (`google_display_name`).
  `my_display_name` applies it when a group is created or joined, and a security-definer trigger
  on `profile` insert and update rewrites the owner's member rows, since clients may only read
  that table.

Because visibility is enforced in Postgres, no client code path can leak data by omission.

Foreign-key checks bypass row-level security, so a row may reference another user's id. That is
harmless: every read is scoped by row-level security.

Policies only filter rows a role may already touch: the Data API reaches a table through the
privileges granted to its roles, and the project grants none by default. Every synced table
grants `select`, `insert` and `update` to `authenticated` in the migration that creates it —
never `delete`, since clients soft-delete, and nothing to `anon`, which no policy matches.

### 5.3 Configuration and secrets

The Supabase project URL and anon key are read from `local.properties` on developer machines and
from repository secrets on CI. They are never committed. The anon key is safe to ship inside the
built app because RLS, not the key, is the access boundary. The Google OAuth client id that
Credential Manager needs travels the same way and is safe to ship for the same reason.

A build without any of them is normal: nothing resolves the Supabase client until a sign-in or a
sync asks for one, so a fresh clone still launches and runs anonymously on Android. Sign-in is
unavailable rather than merely hidden — the screens that offer it read `SignInAvailable`, and an
attempt made anyway fails and is reported instead of resolving a client that cannot exist.

Web sign-in asks Supabase to return to the page's own address rather than its origin, because
GitHub Pages serves the app under a path. That address must match the project's redirect
allowlist, alongside the local development server's.

`WEB_APP_URL`, the web app's address, travels the same way, as a repository variable rather than
a secret on CI. Android builds invite links from it and shares only the code without it; the web
builds them from its own address.

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
- Google sign-in goes through Credential Manager, whose sheet needs an `Activity` rather than the
  `Application`, so the implementation takes a context supplier the Activity installs.
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
- The gate depends on those suites directly, not on the `allTests` report, so no part of it
  schedules Node, npm or a Wasm test compilation. ktlint still covers the wasmJs sources. The
  root build script makes that cut, and holds the one ktlint filter both library modules use.

Any UI test that mounts a `NavHost` runs through `runNavigationUiTest` in `app/src/jvmTest`. The
Compose test host already provides a resumed `LifecycleOwner`; what it does not provide is a main
dispatcher, and `androidx.lifecycle` resolves "the main thread" from `Dispatchers.Main` when it
moves back-stack entries. `runNavigationUiTest` sets an unconfined one for the duration of the
test, which is all such a test needs. The cost is the guard: lifecycle can no longer reject a
main-thread violation in the code under test.

App UI tests that host a single screen instead use `runScreenTest`: a single-destination
`NavHost`, so the screen under test still gets a `ViewModelStoreOwner`. It wires in fake
repositories, a fixed clock and a ticker that only advances when the test tells it to.

---

## 8. Build, CI and release

- `master` is the only long-lived branch and every push to it runs CI.
- **`ci.yml`** is the only workflow on a push or a pull request, and it has two jobs. `check`
  runs JDK 21 and one Gradle invocation — `check :androidApp:assembleDebug
  :app:wasmJsBrowserDistribution` — then uploads the web distribution as the Pages artifact.
  `deploy` needs `check` and publishes that same artifact to GitHub Pages on a push to `master`,
  so nothing reaches Pages that the gate has not passed.
- **`release.yml`**: on a `v*` tag, runs `check`, then builds a signed release APK from secrets
  and creates a GitHub Release with it attached as `kachalochka-X.Y.Z.apk`. No tag ships without
  the gate passing.
  `versionName` comes from the tag and `versionCode` is derived
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

Friends' calendar colours are the one palette outside `ColorScheme`: eight hues per scheme,
declared in the same file, kept clear of `primary`, and handed out by index through
`friendColor(index)`, which reads the palette `KachalochkaTheme` provides for the active scheme.

The colour schemes themselves are the Nocturne design system's ramps: dark is drawn by the
design, light is derived from the same tonal ramps. Icons are Phosphor Regular, vendored as
`ImageVector`s under `app/.../ui/icons` with the MIT licence kept beside them — the multiplatform
Phosphor library ships every weight of every icon, about 27 MB per platform artifact.

---

## 11. Local time

`kotlin.time` has no time zones. `app` reads the UTC offset from the platform —
`java.util.TimeZone` on Android and the JVM, `Date.getTimezoneOffset` on Wasm — and passes it
into the pure day and clock-time functions in `domain/`.

Calendar dates are `CalendarDay` values in `domain/`, with epoch-day arithmetic, because
`kotlin.time` has no calendar and the stack adds no date library. The calendar screen, a past
day's first set and moving a visit all go through them.
