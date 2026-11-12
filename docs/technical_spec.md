# Kachalochka — technical specification

This document tells how the app is built and which rules new work must keep. It is for a
developer who adds or changes a feature. It does not repeat the code: it tells why a seam
exists, and what breaks when you cross it.

- For what the app does, see [functional_spec.md](functional_spec.md).
- For the domain terms, see [GLOSSARY.md](../GLOSSARY.md).
- For the commands, see [CLAUDE.md](../CLAUDE.md).

## 1. Platform

One Kotlin codebase makes two apps:

| Target | Delivery | Data |
|---|---|---|
| Android | APK from GitHub Releases and Google Play | SQLite on the device, synced with Supabase |
| Web | Kotlin/Wasm bundle on GitHub Pages | Supabase only, read and written directly |

- Only Android must work offline, so the web build has no local database. This keeps the web on
  the stable part of the toolchain: browser SQLite for Wasm is not ready. Offline web would be a
  new repository implementation behind the same interfaces (see [3](#3-repositories)).
- Supabase is the backend: Postgres, row-level security (RLS), Storage and Auth. There is no
  server code of our own.

### 1.1 Technology

Do not replace these choices.

| Concern | Choice | Reason |
|---|---|---|
| Language | Kotlin 2.x, JDK 21 | JDK 21 is the minimum of the current AGP and Gradle |
| UI | Compose Multiplatform | The same UI code on Android and Wasm |
| Navigation | Jetpack Navigation Compose (multiplatform) | Type-safe routes |
| DI | Koin | No code generation; works on Wasm |
| Local DB | SQLDelight | SQL first, type-safe queries, a JVM driver for host tests |
| Backend client | supabase-kt (auth, postgrest, storage, realtime) | Supports Android and Wasm |
| Sign-in | Credential Manager (Android), OAuth redirect (web) | Both give a Supabase session |
| HTTP | Ktor: OkHttp on Android, JS engine on Wasm | Necessary for supabase-kt |
| Serialization | kotlinx.serialization | Necessary for supabase-kt |
| Time | `kotlin.time` | |
| Async | kotlinx.coroutines and Flow | |
| Images | Coil 3 | Multiplatform; a custom fetcher loads Storage photos |
| Charts | Vico | Compose Multiplatform support |
| Lint and format | ktlint Gradle plugin | Runs in `check` |
| Tests | kotlin.test, kotlinx-coroutines-test, Turbine | Host JVM only |

### 1.2 Versions

All versions are in `gradle/libs.versions.toml`. Some of them have limits that a routine upgrade
breaks:

- **Gradle 9.7.0 and AGP 9.3.1** are the newest versions that Kotlin 2.4.20 supports. AGP 9 is
  also the minimum: it does not accept `com.android.library` next to the Kotlin multiplatform
  plugin, which is why there are three modules (see [2](#2-modules-and-layers)).
- **`navigation` 2.9.2**, while 2.10 is in beta.
- **`compileSdk` 37 is a minimum.** Compose Multiplatform 1.12.0 and `lifecycle` 2.11.0 need it,
  and only AGP 9 accepts it.
- **`kotlinx-browser`** is a wasmJs-only dependency, because `kotlinx.browser.localStorage` is not
  in the wasmJs standard library.

The Gradle Kotlin DSL refuses a deprecated Gradle API. Thus build scripts use `register` and
`named`, not the `by registering` and `by getting` delegates.

## 2. Modules and layers

```text
core/        Kotlin Multiplatform library: domain, data, sync. Targets android, jvm, wasmJs.
app/         Compose Multiplatform library: screens, view models, DI. Targets android, jvm, wasmJs.
androidApp/  Android application. Depends on app and core. Makes the APK.
```

- **`androidApp`** exists because AGP 9 does not apply `com.android.application` to a
  multiplatform module. It holds only what an APK needs and a library does not: the launcher
  manifest and icon, `MainActivity`, the `Application` subclass, the photo `FileProvider`, signing
  and version numbers. Other Android code, such as `DataStoreDeviceStore`, is in
  `app/src/androidMain`.
- **The `jvm` target** of `core` and `app` is for tests only. `core` tests use the SQLDelight JVM
  driver; `app` tests are view model tests and Compose desktop UI tests. The `jvm` build of `app`
  never ships.
- **The `sql` source set group** of `core` selects its Android half by platform type.
  `withAndroidTarget()` matches nothing under the multiplatform library plugin (KT-80409), and
  `sqlMain` then loses its Android compilation.
- **The icon** is adaptive: the round badge on the colour of its outer ring, so each launcher mask
  continues the ring. The web uses the same art as `icon.png`. The source art is
  `design/logo.png`; `design/play/` holds the Play listing graphics and texts, which are uploaded
  by hand.

Inside `core`:

```text
domain/  entities, value objects, repository interfaces, pure functions
data/    SQLDelight schema and DAOs, Supabase gateways, sync engine
```

Three rules:

1. **`domain/` uses only the Kotlin standard libraries.** It declares the interfaces; `data/`
   implements them; `app` connects them with Koin. A `domain/` file that imports `data/`,
   Supabase, SQLDelight or Koin fails `:core:jvmTest`.
2. **`core` never imports Compose.** It must compile and test as a plain library.
3. **Logic that can be a pure function is one.** Unit and side conversion, the platform shift,
   the suggested next set, gains and statistics are functions in `domain/` with their own tests.
   `app` shows the results; it does not calculate them.

`DomainPurityTest` enforces rule 1.

**Where the parts connect.**

- Koin modules: `CoreModule.kt`, `SqlModule.kt` (Android and JVM), `CorePlatformModule.*.kt`,
  `AppModule.kt` and `PlatformModule.*.kt`.
- Start-up: `KachalochkaApplication.kt` on Android and `Main.kt` on the web start the
  process-wide jobs: the live session, the group cache, the link upkeep, the visit normalization
  and the rest sound. The web also keeps invite codes and follows the family.
- `FailureLog` records the failures of background work, such as sync and normalization.

## 3. Repositories

Each repository interface in `domain/` has two implementations, except `FriendsRepository` and
`FamilyRepository`. Both platforms read them from the server, so `SupabaseFriendsRepository` and
`SupabaseFamilyRepository` are in `commonMain`. Both platforms wrap the first (see
[4.12](#412-friends-data)).

| Implementation | Source set | Data |
|---|---|---|
| `Local*Repository` | `sqlMain` (android and jvm) | SQLDelight and the outbox sync |
| `Remote*Repository` | `wasmJsMain` | Supabase PostgREST directly |

- `app` sees only the interface. This lets Android work offline and the web online with the same
  UI code. Thus domain types do not depend on a storage format: one implementation writes an
  entity to SQLite, the other sends it as JSON.
- `sqlMain` is shared by the Android target and the test JVM target, so host tests cover the DAOs
  and the full sync. SQLDelight is not in `commonMain`, because Wasm has no local database.
- **Reads take the owner as an argument.** One Android database holds the rows of all accounts on
  the device. A repository that found the owner by itself would filter twice on the web, where RLS
  already filters, and a test that forgot the active account would get an empty list, not a
  failure.
- **Repositories are suspend-only.** `domain/` cannot use kotlinx.coroutines, so it has no `Flow`.
  A view model that writes reads again after the write.
- **`CurrentUser`** gives the owner of a new row: the active account, or null on Android while
  nobody is signed in. On the web, RLS refuses a row without an owner, so the web needs an account
  before any screen.

**The SQLDelight Gradle plugin** adds its generated sources to `commonMain`.
`core/build.gradle.kts` removes them there and adds them to `sqlMain` in an `afterEvaluate` block,
because the compiler refuses a file in two source sets.

- The block must come after the `sqldelight { }` block, because the plugin uses an
  `afterEvaluate` of its own.
- The block excludes by pattern. If it sets `srcDirs` again, the task dependency of the generated
  directory is lost, and a clean build does not run the generator.

## 4. Data and sync

### 4.1 Identity and changes

Each synced row has these columns:

| Column | Rule |
|---|---|
| `id` | A UUID v4 made by the client. `tag` and `machine_tag` use a UUID v5 of their content (see [4.6](#46-machines-and-tags)). Never changes |
| `user_id` | The owner. Null on Android while nobody is signed in |
| `updated_at` | UTC time, set by the writer on each change |
| `deleted` | Soft delete. Clients never delete a row |

- **Client ids** let a device make a visit and its sets offline, linked before the server sees
  them.
- **Soft deletes** make sync converge: a delete is an update and goes the same way.
- **Id types** (`ProfileId`, `UserId` and the others) accept only a lower-case UUID v4. Postgres
  stores `uuid` and changes text to lower case, so without this check SQLite and the server could
  give different answers for one id.
- **`user_id` is not null on the server.** A device sets the owner before a row goes into the
  outbox. A row without an owner would match no RLS policy, so no client could see or remove it.

**Profile.** Each account has one `profile` row. A signed-in owner's new profile takes the
owner's id as its `id` (`Profile.new`), so two devices that make it offline make the same row.
Readers take the live profile with the newest `updated_at`, then the largest `id`
(`ProfileRepository.forOwner`, and `member_display_name` on the server). Thus an owner with two
profiles after a claim still reads the same one on each client. `friend_colors` is a JSON object
in a text column; an entry that cannot be read is dropped and does not stop a pull.

### 4.2 Sync (Android)

Sync is a `data/` concern. `domain/` and `app` do not know if a row was pushed.

**Push.** Each local write adds the table and the row id to the `outbox`.

- A pass reads each row to find its owner. An entry of another account waits for that account's
  turn. An entry whose row is gone or has no owner is dropped.
- A pass pushes by table, in this order: `machine`, `tag`, `machine_tag`, `visit`,
  `workout_plan`, `profile`, `measure`, `machine_link`, `photo`, `workout_set`, `measurement`.
  The server checks that a set's visit and machine, and a tag link's machine and tag, exist. The
  other rows follow the rows that they name, so a reader never sees them first.
- A pass removes an entry after its push only when nothing added it again during the push.

**Pull.** A pass reads each table's rows newer than the account's pull watermark, in pages keyed
on `(updated_at, id)`, until a page is empty.

- It writes nothing and keeps the watermark unless all tables pull without an error.
- It skips rows that have an outbox entry.
- Then the watermark moves to the newest `updated_at` pulled.

**Conflicts.** A row in the outbox wins over the server's copy: the pull skips it, and its push
overwrites the server's row. Else the server's copy wins. Each row has one owner and edits are
rare, so a merge strategy is not worth its cost. The only exception is a seeded `measure` (see
[4.10](#410-body-measures)).

**When a pass runs.** Sync is the WorkManager unique work `"sync"`, with a network constraint. A
pass runs:

- when the app comes to the foreground (also at launch) and when it goes to the background.
- after an account is added or becomes active.
- after a write that other devices must see soon: a visit or a measurement of a day other than
  today, a visit moved, replaced or deleted in the calendar or the feed, the profile (a friend
  colour included), a measure, a link, a copy, an unlink, a merge or a delete of a machine, a
  change of the family.
- after the start-up normalization of the anonymous owner wrote something.

A new request replaces (`REPLACE`) the queued or running pass, so it never waits behind a backoff.
A stopped pass is safe: its outbox entries stay, and each push is an upsert. A pass that does not
push and pull everything is retried with exponential backoff.

**After the pull** (`runAndNormalize`), the pass normalizes the visits and recalculates the prior
bests of each owner that pushed and pulled without an error (trainees excluded; see
[4.8](#48-visits-and-sets)). When that wrote something, the same job runs one more pass to push
it. It does not request a new pass, because the request would replace and cancel the running job.

**All accounts.** A pass syncs each account on the device: the signed-in accounts, then the
managed accounts (see [4.4](#44-managed-accounts)). `syncState` has one row for each `user_id`, so
each account has its own watermark.

| Account | Tables it pushes and pulls |
|---|---|
| Signed in | All |
| Child | `machine`, `tag`, `machine_tag`, `visit`, `workout_set`, `machine_link`, `photo`, `workout_plan` |
| Trainee | Pushes `machine`, `tag`, `machine_tag`, `workout_plan`. Pulls the tables of a child, except `machine_link` |

A child's `profile`, `measure` and `measurement` entries stay in the outbox, because the server
refuses them. They go out when the child signs in on that device.

**Tokens.** Sync uses its own `SupabaseClient` without `Auth` (see
[4.3](#43-accounts)). Its `accessToken` asks for the token of the account that the pass syncs now.
The UI client and its session are never touched; a change of the session on one client would race
with the UI's writes.

- The active account gives its own access token while it has more than one minute left. Nearer
  the expiry, the pass asks the UI client to refresh its own session (`refreshCurrentSession`),
  because a refresh from elsewhere would spend the refresh token that the UI client holds.
- Other accounts refresh through `refreshSession`. The pass writes the new session to the account
  store before it uses it.
- A refresh that the server refuses with a 4xx (not 429) skips that account in this pass. A 429 or
  a network failure fails the pass.

**Screens.** The screens that read their rows once (home, the visit, the calendar and the feed,
the machines, the plans, the statistics, the measures and the accounts) read again when
`SyncTrigger.completed` emits after each pass. On the web, each write already went to the server,
and `ServerSyncTrigger` emits after each request.

### 4.3 Accounts

**Anonymous use.** On Android, rows made without an account have a null `user_id` and are never
pushed. The first sign-in stamps each such row, the profile included, with the account's id and
adds it to the outbox. The profile keeps its `updated_at`, so the account's own newer profile still
wins. Tags are claimed in a different way (see [4.6](#46-machines-and-tags)). Accounts added later
claim nothing.

**Several accounts.** One account is active and owns what is recorded now. All accounts share one
local database, so reads take the owner (see [3](#3-repositories)) and each account has its own
watermark. A sign-out removes the session and keeps the rows.

**Sessions.** The account store in `core/data/identity` holds the sessions. It is not in
`domain/`, because the UI observes a `StateFlow`. It writes its JSON text through `AccountStorage`,
which is not a `DeviceStore` (see [9.2](#92-device-settings)): `DataStoreAccountStorage` (the file
`accounts.preferences_pb`) on Android, `LocalStorageAccountStorage` on the web and
`InMemoryAccountStorage` on the JVM. Its `read` blocks, so the first frame has the accounts.

- A switch calls `importSession` on the UI client, so one session is live at a time.
- supabase-kt's own session storage is off: it holds one session and would fight for the same
  slot. Its reload of that storage when the app comes to the foreground is also off: it would find
  the storage empty and end the live session. As a second guard, a session end that the library
  reports just after it starts to reload signs nobody out.
- supabase-kt refreshes the live session. `LiveSession` writes each refresh to the store, and
  records the live account only after `importSession` succeeds.
- When supabase-kt clears a session that it could not refresh, `LiveSession` signs that account out
  of the store and makes the next account active. A clear that the app makes itself, such as a
  sign-out, does not.
- When that next account cannot become active, nobody is active and the live session is clear. The
  account stays in the list, so a new switch is a retry.
- `CurrentUser` reads the active id of the store, on both platforms.

**Supabase clients.** `SupabaseClientFactory.kt` makes five clients. Only the UI client has
`Auth`; the others take a token for each request.

| Client | Used for |
|---|---|
| `supabaseClient` | The UI: sign-in, the live session, web reads and writes |
| `syncSupabaseClient` | The sync pass, as the account that it syncs |
| `activeAccountSupabaseClient` | Friends' and family requests and photo downloads on Android, as the active account |
| `ownerSupabaseClient` | Account deletion on Android, as the account that is deleted |
| `actingSupabaseClient` | Family reads (`AccountFamilyReads`, `ActingAccount`), as each Google account in turn |

`AccountTokens` gives the tokens. Parallel refreshes of one account's token share one request
(`SingleFlight`).

**Watermark resets.** An account that was managed and then signs in on the device loses its
watermark (`Accounts.addAccount`), and so do a guardian's managed accounts when the guardian
leaves the device (`PersistedAccountStore.remove`). Thus the next pass pulls all their rows.

**A switch under any screen.** The avatar is in each top bar, so the active account can change on
any screen. No view model keeps a row across a switch: each observes the active id and reads again
for the new account.

- A write of something new checks again that the account owns the row that it uses. Else it uses
  the account's stand-in (see [4.7](#47-links-and-clusters)).
- A correction of a recorded row keeps that row's owner and visit.
- A form keeps the text that the user typed; only the row under it changes.
- Screens that save by themselves, such as the plan form, put their write in `HeldWrites`.
  `Accounts` runs each held write before a sign-in, a switch or a sign-out, while the old account
  is still live (see [4.9](#49-plans)).

**Names.** `Account.shownName` (the nickname, else the Google name) is the name on each screen.
Only the placeholder of "Nickname" shows the Google name. The web can read only the active
account's profile, so the account store keeps each Google account's nickname. `AccountAvatars`
updates it from each profile that it reads; a failed read, a token refresh and a new sign-in keep
the stored nickname.

**Friend colours** belong to the account, in its profile's `friend_colors`, keyed by friend.

- `FriendColorStore.colorsFor` only reads. A friend without a stored colour gets one from the pure
  `assignedColors`: the stored map and the friends sorted by id, seeded by an FNV-1a hash of
  the owner id. Each screen and platform thus draws the same colours without a write.
- Only a colour that the user picks is saved. A drawn colour must not be written: a device that
  did not yet pull the profile would push its row over the server's and erase the nickname and
  colours chosen elsewhere.
- A new friend can change the drawn colours of others, never a stored one.

### 4.4 Managed accounts

A managed account is a child or a trainee of a Google account on the device. It has no session:
its entry in the account store names the guardian (the parent or the trainer) whose session acts
for it.

- A switch to it makes the guardian's session live first. The UI client, `AccountTokens` and each
  client that resolves the active token act as the guardian. RLS lets the guardian write the
  managed account's rows, and each row recorded carries the managed account's id.
- `CurrentUser.writesPrivateRows` is false while a managed account is active: its profile and
  measures belong to it alone.
- A managed entry never replaces a Google account signed in on the device. It leaves the device
  with its guardian, keeps its rows but loses its watermark, so a later sign-in of the child on
  that device pulls everything.
- When two guardians of one child are on the device, the first one acts.
- A person who is both a child and a trainee of accounts on the device shows once, as a child. A
  person who changes between the two is removed and added again, because the two sync different
  tables.

**Trainee.** `AccountKind.Managed.trainee` marks a trainee. While a trainee is active:

- `AccountsUi.traineeActive` (and `traineeActive()` in a composable) hides what the trainer must
  not change.
- The view models that write a visit (`VisitViewModel`, `CalendarViewModel`,
  `VisitFeedViewModel`, `PlansViewModel`, `MachinePlacementViewModel`) refuse to write it.
  `MachineCatalogue.standIn` refuses to copy another account's machine.
  `MachineFormViewModel.readOnly` keeps an existing machine as it is.
- `VisitNormalization` skips the trainee's visits, and prior bests are not recalculated on the
  trainer's device: such writes could never go out.
- `NavController.leaveTraineeHidden` closes the machine form and the link choosers.
- `TraineeFriends` gives a trainee no friends: under the trainer's session, a group read would
  answer with the trainer's friends.

**Following the family.** `FamilyFollower` keeps the managed entries the same as on the server.

- It reads `my_family` (children and guardians) and `my_coaching` (trainees and trainers) as each
  Google account on the device, one after the other on one client.
- It lists each managed account once, under the first guardian signed in on the device, and
  updates their names and avatars.
- It removes a managed account that no family names any more, with its rows, because the device
  can never sync them again. `OwnedRowsPurge` removes an owner's rows: `SqlOwnedRowsPurge` on
  Android, `NoOwnedRowsPurge` on the web.
- A family that cannot be read is not empty: while one is unread, no managed account leaves, and
  a managed account of an unread guardian keeps that guardian. A read without a token fails; it
  does not go out anonymous.
- A managed account that leaves is purged while it is still listed, and leaves the store after
  that. A failed purge thus repeats on the next follow.
- Each follow makes the guardian of the active managed account the live session when it is not
  already.

| Platform | When it follows |
|---|---|
| Android | At the start of each sync pass, and after each family read of "People" |
| Web | Once on each page load (a sign-in redirect is a page load), and after each family read of "People" |

A follow and a sync pass share one lock (`followThen`): a follow during a pass could purge a
managed account whose rows the pass then pulls again.

**Codes from the web.** A parent's link carries `?parent=CODE` and a trainer's link
`?trainer=CODE`. `PendingLink` keeps the code until an account signs in, and the account confirms
before it links.

### 4.5 Photos

A photo belongs to a machine: the synced `photo` table (`machine_id`, `taken_at`), ordered by
`(taken_at, id)`. `machine_id` is not a foreign key.

- The bytes are a JPEG in the private Storage bucket `photos`, at `<user_id>/<photo_id>`
  (`Photo.storagePath`). The owner and the owner's guardians write, read and delete the owner's
  folder; trainers and friends read it.
- Both platforms make a new photo upright, at most 1600 px on its long edge, JPEG quality 85,
  before they keep it: Android with `BitmapFactory` and the EXIF orientation, the web with a
  canvas.
- **Android** keeps the bytes in `PhotoFiles` (`filesDir/photos/<id>.jpg`). `LocalPhotoRepository`
  writes the file before the row. The sync pass uploads the file before it pushes a live row, and
  removes the Storage object before it pushes a deleted row. A pulled deleted row removes the file.
- **The web** uploads and deletes in `RemotePhotoRepository`.

**Display.** Coil loads each photo through a `Photo` fetcher (`PhotoFetcher`) that asks
`PhotoImages`: the device file when there is one, else an authenticated download from Storage as
the active account. Google pictures load as plain URLs. On Android each
downloaded photo, a friend's too, is kept as a file, so it shows again without a network. One
`ImageLoader` (`PhotoLoaders`) serves all screens.

**Camera and sharing.** `PhotoCapture` is bound for each platform. Android registers the
`TakePicture` and `PickVisualMedia` launchers; the camera writes to the cache through the
`FileProvider` of `androidApp`. The web opens a file input, with `capture` for the camera.
`PhotoSharing` shares the bytes of a photo: Android through the cache and the `FileProvider`, the
web with `navigator.share` where the browser can share a file, else a download.

**Avatar.** An avatar that the owner chose is a `photo` row whose `machine_id` is the owner's
profile id, named by `profile.avatar_photo`. It is stored, synced and deleted as a machine's photo
is. Profiles are private, so friends read avatars from `group_member`: the trigger
`member_takes_avatar` writes `avatar_photo` and `picture_url` (the Google picture) on each member
row of the owner. The account's own Google picture is the `avatar_url` or `picture` claim of the
session. `Avatar` in `domain/identity` holds the photo and the picture; with neither, the initial
shows.

**Cover photo.** `coverPhoto` picks the photo that stands for a machine in the lists:
`machine.cover_photo` while that photo is live in the machine's cluster, else the machine's first
photo, else the first photo of its cluster. A friend's machine uses its owner's choice. Offline,
a friend's chosen photo is not available, so the own first photo shows, and the form keeps the
choice on save unless it removed that photo.

### 4.6 Machines and tags

The gym data is in the synced tables `machine`, `visit`, `workout_set`, `machine_link`, `photo`,
`tag`, `machine_tag` and `workout_plan`.

- `workout_set` is not called `set`, and `workout_plan` not `plan`: those are SQL keywords.
- Weights are `Double`. Each step and each typed weight is rounded to three decimals, so a running
  total does not drift.
- The weight mode and the unit are enums. One mapping in `core/data/gym` writes them as `total` /
  `per_side` / `counterweight` and `kg` / `lb` / `custom`. An unknown name reads as `total` or
  `kg`, so one bad row does not stop a pull. An own unit's name is in `unit_label`.

**Tags** are rows: `tag` (`name`) has one row for each owner and name, and `machine_tag`
(`machine_id`, `tag_id`) joins machines and tags.

- A tag's name is stored as its `tagKey`: trimmed, inner spaces made one, lower case.
- Ids come from the content, so the same tag added on two devices offline is one row. A tag's id
  is the UUID v5 of `"<owner>/<name>"` (empty owner while anonymous); a `machine_tag` id is the
  UUID v5 of `"<machine id>/<name>"` (`TagWire.kt`). `uuidV5` in `domain/identity` gives the same
  bytes as Postgres' `uuid_generate_v5`.
- A friend can read the owner's ids, so the server checks each write against its content
  (migration `0027`): a tag's id must be `tag_content_id` of its owner and name, and a link must
  join a machine and a tag of its own owner.
- Readers count a link only between a machine and a tag of the link's owner (`withTagRows` and
  `MachineTag.sq`), so a friend's row is never the owner's tag.
- `Machine.tags` is the domain view. Both repositories read it from the live links and their tags.
  `writeTagsOf` (`TagRowStore.kt`) writes it on both platforms: it makes the machine's tags live,
  writes and soft-deletes links, and soft-deletes each tag that no live machine of the owner
  holds. `SqlTagRows` and `PostgrestTagRows` only map these calls to rows. On Android the flow runs
  in the machine's transaction.
- A group read gets the friends' tags from the `group_gym` answer, which has only live tags.
- A claim (see [4.3](#43-accounts)) takes only the anonymous links of live machines and the tags
  that they hold. It forgets the other anonymous tag rows: written as the owner's, they could
  delete a tag that the server holds live.
- The pure functions of `domain/gym` hold the tag rules: `sortedTags` (the display order),
  `hasAllTags` (the filter), `toggledTag` and `suggestedTags` in `Tags.kt`, and `tagSections` in
  `TagSection.kt`. Screens write a tag in capitals only when they draw it (`TagText`,
  `tagTitle`).

**Old tag column.** `machine.tags`, a JSON array of names, stays in both schemas for clients before
1.7.1. Clients now never write it. Migration `0027` on the server, and `19.sqm` with the Kotlin
step `tagConversion` on the device, moved its values into the tag tables once. An old client
shows the tags as they were at that time.

**Old link column.** `machine.link_id`, the link key of 1.0.2, stays in both schemas for those
clients. It is never read or written. Migration `0009` converted its keys to `machine_link` rows
once.

**Records and sort.** `machinePeaks` in `domain/gym` gives for each machine its heaviest and
lightest weight with the most reps at each, the time of its last set and its number of visits.
`MachinePeaks.best` is the record. Android calculates it locally (`WorkoutSetRepository.peaks`);
the web and `FriendsRepository.groupPeaks` ask the server function `machine_peaks`, which makes
the same calculation under RLS and returns one row for each machine. `machineOrder` sorts by
`profile.machine_sort` (`recent` / `name` / `frequent`).

**Choices in the profile.** `machine_sort` and `group_by_tag` are `ProfileChoice`s: a choice is
written to the profile at once. A child's profile cannot be written, so `UnsavedChoices` keeps
the child's choice in memory, before the profile.

**Units.** `UnitConversion` in `domain/gym` converts recorded weights between kg and lb:
`convertedWeight` rounds pounds to 0.1 kg and kilograms to a multiple of a pound step, which
`guessedLbStep` finds from the sets. `SideConversion.sideFactor` halves or doubles weights for a
change to or from per side. `platformShift(from, to)` is the change that keeps each total the same
under the new platform weight; `shiftedSets` applies it, with no weight below zero. The machine
form, on save, applies the unit, then the sides, then the platform.

**Search.** `nameMatches` in `domain/gym` filters each machine search: the smallest Levenshtein
distance from the typed text to a part of the name (Sellers' algorithm), against a limit that
increases with the typed length. `matchedPart` gives the part to highlight.

### 4.7 Links and clusters

A `machine_link` row tells that its owner's `machine_id` is the same physical machine as a
friend's `linked_machine_id`. Neither column is a foreign key, and no policy checks them: a link
can reach the server before its machine does, and the linked machine belongs to someone else.

- Links have no direction for readers. Machines joined by live links, through any number of
  steps, are a cluster: one physical machine. `MachineClusters` in `domain/gym` makes the clusters
  by union-find, and each reader uses it.
- The clusters join the account's own links with its friends' live links. The friends' links
  come from the server with the group read (`FriendsRepository.groupGym`).
- **Direct links.** Each device keeps its owner's links direct, so a cluster stays when a machine
  in the middle, or its owner, goes. `missingDirectLinks` gives the links to write: from each own
  live machine to each visible friend's machine of its cluster that no live link joins to it. The
  links are the owner's rows; no friend's row changes. A device sees only its friends' links.
- `linkSuggestions` gives each own machine the other own machines with the same name (trimmed,
  any case) or in its cluster: the merge suggestions.

**`MachineCatalogue`** (`app/ui/machine`) reads machines with their photos and links for each
screen that lists them. It is the one writer of `machine_link` rows.

| Call | What it does |
|---|---|
| `own` | The account's live machines, photos and links |
| `group` | The friends' machines, tags, links and photos in one request, `FriendsRepository.groupGym` (server function `group_gym`) |
| `lastGroup` | The last group read of the account, else the kept one (see [4.12](#412-friends-data)), with the own links as they are now |
| `clusters` | The clusters of the own links and `FriendsRepository.groupLinks`, for a friend's calendar |
| `take` | Writes an own copy of a friend's machine (`linkedCopy`) and its link |
| `link` | Writes one own link to a friend's machine |
| `friendStandIn` | The own machine of a friend's machine's cluster, else `take` |
| `ownCopy`, `deviceStandIn` | The account's machine for a machine of another account on the device (see [4.8](#48-visits-and-sets)) |
| `standIn` | The account's machine for any shown machine |
| `standIns` | The account's machines for the machines of a friend's card: one group read, the order kept, a machine the group lacks left out |
| `merge`, `unlink`, `delete` | See below |

- All link writes run under one mutex, and read the own links again first, so two writes never
  write the same link. Server calls run before the mutex.
- A write requests a sync when it wrote something, also when it stopped halfway.
- After each successful group read, and after `take` and `link`, the catalogue writes
  `missingDirectLinks` for the account of that read. A failed upkeep goes to `FailureLog` and
  fails nothing.
- `LinkUpkeep` makes a group read for each account that becomes active, so friends' new links
  reach the account without a screen that reads the group.
- Group reads for one account share the read in flight. The catalogue keeps the last read of each
  account in memory.

**Screens.** `MachineReads` gives each screen one stream for each account: first the own read
joined to the last group read, then the new read. A failed read emits the last good one, marked
offline. `ShownMachines` joins an own read and a group read into what a screen shows: clusters,
cover photos, the friends' machines on offer and the suggestions. It drops a group read made for
another account, so one account's friends never show for another. `MachineCards` makes the card
that each list draws, in the preferred unit. `listing` (`MachineListing.kt`) applies a
`MachineQuery` (text, tags, machines in the visit, the suggestion chip).

**Merge.** `MachineCatalogue.merge` merges two own machines for the account that the chooser
showed.

1. With an account, it first moves friends' links from the removed machine to the kept one on the
   server (`FriendsRepository.repointLinks`), only when `groupLinks` shows such a link. When the
   server does not answer, nothing is written.
2. It writes `mergedMachines` and `mergedLinks`: the sets and photos moved, the own links moved
   (or deleted when the kept machine has them already), and the removed machine soft-deleted. With
   `convert`, the moved sets take the unit of the kept machine (`convertedSets`). With `adjust`,
   they take `platformShift(removed, kept)`.
3. It writes sets first and the machine last, under `NonCancellable`, so a closed screen does not
   stop it halfway, and requests a sync.

`suggestedToKeep` preselects the machine whose latest live set is later.

**Unlink.** `MachineCatalogue.unlink` calls `FriendsRepository.breakLinks` on the server, then
soft-deletes the own links of the machine. An unlink drops the account's last group read. A merge
drops it only when it moved a friend's link.

**Delete.** `MachineCatalogue.delete` soft-deletes the machine's photos, its own links and the
machine, then requests a sync. It writes nothing for a machine with live sets. Friends' links to a
deleted machine stay, because the group read leaves out deleted machines; so a delete needs no
network.

### 4.8 Visits and sets

**A visit is one day.** `visit.day` is the calendar day; there is no start, end or duration.

- `day` is nullable and has no unique constraint: clients before 1.0.2 push visits without it, and
  two devices can make the same day offline. `recorded_at` and `ended_at` stay, written equal, for
  those clients.
- A visit without `day` is on the day of its `recorded_at` (`Visit.dayAt`).
- When a day has more than one visit, readers show `keptVisit`: the newest by
  `(recorded_at, updated_at, id)` of those with a live set, or of all when none has a set.
  `keptVisit` in `domain/gym` holds this rule. Readers use it through `shownVisit`
  (`VisitRepository.shownOn`); the feed, which holds the sets already, calls it directly.

**Normalization.** `normalizedVisits` in `domain/gym` sets a missing `day` and, for each day,
keeps `keptVisit` and soft-deletes the others with their sets. A second run writes nothing.
`VisitNormalizer` reads each visit again before it writes it, and skips one that changed.

| Platform | When it runs |
|---|---|
| Android, anonymous owner | At each start |
| Android, signed-in account | In the sync worker, after a clean pass of that account. When it writes, the worker runs one more pass in the same job |
| Web | After the session restore and after each switch |

A failure for one owner is logged and does not stop the next owner. No platform normalizes the
visits of a trainee (see [4.4](#44-managed-accounts)).

**Sets.**

- Readers of sets filter on the set's own `deleted` and `recorded_at`, and never join `visit`. So
  a move or a delete of a visit rewrites its sets: sets first, the visit last.
- Within a visit, sets sort by `(position, recorded_at, id)`. A reorder (`machineMovedTo`,
  `setMovedTo`) numbers the sets 1..n and writes only the sets whose position changed.
- A set added to a visit of another day is stamped one second after the last set of that visit,
  so it stays on that day and in order.
- The set form cuts `comment` to 200 characters while the user types, and trims it on save.

**`SetRecorder`** (`app/ui/visit`) is the one way to record, correct, remove and reorder a set,
and to plan a machine on a day. It finds or makes the day's visit, gives a new set its position
and time, starts the rest timer for a set of today and requests a sync for another day.

- A set goes on an own machine of the active account. When the shown machine belongs to another
  account on the device (such as a child's), `MachineCatalogue.ownCopy` finds the stand-in from
  both accounts' local links: the machine of the same cluster, else one with the same name, else
  a new `linkedCopy` linked to the cluster. `deviceStandIn` finds the same machine without a write,
  so after a switch the page shows the history that the next set continues.
- A picked machine goes through `ownCopy`, then `SetRecorder.plan` adds it to `visit.planned`
  before its page opens.

**Machine page.** The visit view model keeps the state of the open machine page and its set form.
`MachinePager` shows the page in a `HorizontalPager` over `swipeOrder`, the rows of the list. A
next page shows only its header (`MachinePreview`), with the gear as its page shows it, until the
pager stops on it and calls `openMachine`. The swipe is off while the form is open or the sets are
ordered, so the steppers, the sheet and the drag handles keep their gestures.

**Set form people.** `reload` reads which recording accounts (not trainees) have a visit on the
day (`VisitRepository.allOn`). On a new set of today the row has the active account and these
accounts, or all recording accounts while `ShowEveryone.checked` is set. Any other set shows only
the active account. The "Show everyone" box appears only on a new set of today, and only when more
than one account can record. `ShowEveryone` is a Koin `single` that holds one boolean in memory
and saves it nowhere. The form shows the comment field while `SetCommentPreference.shown` or the
set's comment text is set. `SetCommentPreference` is a device setting (see
[9.2](#92-device-settings)).

**Pyramids.** `workout_set.pyramid` (migration `0031`, `22.sqm`) is true on the set that starts a
pyramid. Nothing stores its
end: `pyramidGroups` cuts the sets at each flagged set, so a move or a flag change regroups them
without other writes. `WorkoutSetRow.pyramid` is `@EncodeDefault`, because the wire leaves out
default values and a cleared flag must reach the server.

**Prior bests and gains.** A visit card shows the gain of a day on each machine. To read it from
the day's rows alone, for a friend as for the account, each `workout_set` row stores the prior
best of its machine: `prior_best_weight`, `prior_best_reps` and `prior_best_sets`.

- `DayBest` is the best set of a day and the number of sets equal to it. Results compare by weight
  (inverted on a counterweight machine), then reps, then sets (`dayBestOrder`).
- `0, 0, 0` (`DayBest.NONE`) is the first day ("New"). Three nulls mean that no client has
  calculated the row; the card then shows no gain.
- `withPriorBests` in `domain/gym` is the rule. It returns only the rows whose stored value is
  wrong, so a second run returns nothing.
- `GymWrites` (`core/data/gym`) is the one writer of sets. After each write it applies the rule to
  the machines that it touched, through `WorkoutSetRepository.storePriorBest`, which updates only
  those three columns and `updated_at`. A failed recalculation is reported; the next write on the
  machine repeats it.
- `refreshAll(owner)` recalculates all machines: the sync pass runs it after normalization for
  each clean owner that is not a trainee, and Android runs it at start for the anonymous owner.
- Two offline devices each calculate from part of the data; the recalculation after the pull makes
  them agree. A recalculated row goes out whole, so a parallel edit of the same set on another
  device is lost, as with two edits.

### 4.9 Plans

`workout_plan` holds an account's plans: `name`, `machine_ids` (a JSON array in plan order),
`created_at` and `pinned`. It is private to its owner and the owner's guardians and trainers.

- `planOrder` lists pinned plans first, then by `created_at`.
- `machine_ids` is read leniently: an entry that is not an id is dropped. Readers skip ids of
  machines that are not live, so a merged machine leaves the plans.
- A started plan's machines go to `visit.planned`, an array of the same kind. `startedPlanned`
  adds the live machines that the visit does not have yet. The visit screen shows
  `plannedWithoutSets` as planned rows.

**Plan form.** `PlanFormViewModel` saves the plan by itself.

- Each change of the machines or the pin, a pause in the name (`NAME_PAUSE`), the loss of focus
  (`commitName`) and `leave` put a write on one `Channel`, which runs them in order.
- A write takes the form's state when it runs and skips a plan equal to the stored one. Thus an
  empty new plan is written at its first real change. A new plan that opens with machines, from
  a machine or a visit, is written when the form opens.
- A plan without a name and without live machines is soft-deleted; a later write restores it with
  the same id.
- `leave` sets `done` only after its write ran, because the view model's scope ends when the
  screen closes.
- The form keeps its write in `HeldWrites` while it is open. `Accounts` runs it before each change
  of the active account (a switch, a sign-out, an added account). On the web the new session is
  live after the switch, so a later write of the old plan would fail or go to the wrong account.

**Placement.** `Placement` (`app/ui/plans`) writes each "to a plan" and "to today" action. Each
call takes the account that the screen offered the action to, and checks that it is still active
before it writes and before it opens today. A start of a plan writes today's visit first, then
soft-deletes the plan unless it is pinned, so a failed write keeps the plan.

- `Placement.machinesOn` gives the machines of the shown visit of a day. "Repeat today", the
  machine picker and a new plan from a visit read them there.
- `MachinePlacementViewModel` serves the "⋮" menu that the machine form, the machine page and
  "Friend's machine" share. It first asks `MachineCatalogue.standIn` for the account's machine:
  an own machine stands for itself, another account's machine on the device goes through
  `deviceStandIn`, else a new linked copy, and a friend's machine through `friendStandIn`. While
  a trainee is active, `standIn` answers `Gone` for any machine that is not the trainee's, because
  a trainer must not write links for a trainee.
- `VisitPlacementViewModel` serves "Add to plan…" of the visit screen and of the visit cards in
  the calendar and the feed. For an own visit it reads the visit again and adds its
  `visitMachines` that the plan does not have. For a friend's card it asks `standIns` for the
  account's machines of `VisitCardUi.friendMachines`, then adds them, opens a new plan with them
  (`PlanRoute.machineIds`) or plans them in today's visit (`repeatToday`). The friends of a
  trainee are never shown, so a trainer never reaches it. Its notice is for a visit key: the
  day for an own visit, the card tag for a friend's card.

### 4.10 Body measures

Two synced tables, private to their owner:

- `measure`: name, free-text unit, `kind` and `position`. `kind` names a ready-made measure
  (`weight`, `waist`, `chest`, `hips`, `biceps`, `thigh`, `neck`, `body_fat`, `cycle_day`); it is
  null for the user's own measure. An unknown name reads as null.
- `measurement`: `measure_id`, `day` (a Postgres `date`) and `value`. `measure_id` is not a
  foreign key, because a seeded measure can reach the server after its first value.

**One value a day.** Two devices can write the same day offline. `MeasurementRepository.all` reads
all rows of the owner, deleted ones included, and keeps the newest `(updated_at, id)` for each
measure and day (`newestPerDay`). When that row is deleted, the day has no value. The forms write
only the changed fields; an empty field soft-deletes its row.

**Ready-made measures.** Each load of "Measurements" runs `measureUpkeep` on the owner's ready-made
rows, live or deleted, when `CurrentUser.writesPrivateRows` is true. It runs one at a time.

- `missingDefaults` seeds each kind that the owner has no row of. Thus a deleted kind stays
  deleted, unless `isLocked(kind, sex)`: a kind that a body fat formula reads for the sex comes
  back.
- `CycleDay` is seeded only for the sex `Female`; `isShownFor` hides it for other sexes, and its
  rows stay.
- A live `BodyFat` row is deleted with its values; that kind is never seeded.
- A ready-made row shows the app's name and unit for its kind in the current language
  (`shownName()`, `shownUnit()` in `ui/measures/MeasureHints.kt`), whatever the row stores.
  A seed stores its kind's wire name and an empty unit; older rows can hold Russian names.
  Versions before 1.0.4 show the stored name.
- A signed-in owner's seeds take `derivedId(owner, kind)`, an FNV-1a hash shaped as a UUID v4, and
  `updated_at` at the epoch. Thus two devices that seed offline write the same rows.
- An anonymous owner's seeds take random ids. After a claim, a kind can have two live rows;
  `measureDuplicates` keeps the derived one (else the smallest id) and the upkeep moves the
  values of the others to it. When both have a value on one day, the value entered later stays.
- `storedKindOf` takes a missing or unknown kind from a matching `derivedId`, so an old version
  that wrote no kind does not lose it.

**Seeds and conflicts.** A seed is the exception to "the outbox wins" (see
[4.2](#42-sync-android)), on both sides. The trigger `measure_keeps_newer` ignores an update of
`measure` dated at the epoch when the stored row is newer, so a late seed never undoes a rename
from another device. On the device, a pulled `measure` replaces a pending local row dated at the
epoch (`MEASURE_SEEDED_AT`) and drops its outbox entry.

**Charts.** The periods (`MeasurePeriod`, `periodStart`) count calendar months back from today.
The Vico chart puts each value at its epoch day, so an irregular week keeps its true width, and
fits the y range to the values, so a body weight does not look flat. `measureStep` gives the −/+
step: the largest of 1 and 0.5 that each value is a multiple of (within 1e-6), else 0.1. The
cycle day steps by 1 and goes round the cycle length instead.

**Body fat.** The formulas are pure functions in `domain/measures/BodyFat.kt` (`bodyFat`,
`missingInputs`, `methodsReading`). A result outside 2–70 % is no result. The inputs are found by
`MeasureKind`, never by name. `bodyFatHistory` makes the chart: a point on each day with a value
of an input, from the first day all inputs have one; a missing value is interpolated linearly by
epoch day. After the last value of an input, that value stays.

**Cycle.** The cycle arithmetic is pure, in `domain/measures/Cycle.kt`.

- `cycleAnchors` makes each recorded day of the cycle a start: the day minus the value plus one.
- `Cycle.dayOn` counts a day from a start on or before it, modulo the cycle length. Of such
  starts, the one recorded last applies. Before the first start, it counts back from it.
- `Cycle.bands` gives the phases of a day range: menstruation on days 1 to 5, ovulation on days
  length − 14 (1 at the earliest) to length − 12. In a cycle of 19 days or less they overlap.
- `showsCycle` allows the phases on charts of up to `CYCLE_CHART_MONTHS` (3).
- `cycleOf(profile, measures, values)` gives the cycle for the other charts: null unless the sex
  is `Female` and a live `CycleDay` has a value. A friend's machine never gets the phases.
- `PhasesDecoration`, a Vico `Decoration`, draws the bands under the line with the theme colours.
  The cycle day's own chart is a Compose `Canvas` (`CycleChart`), without Vico.

**Profile.** `ProfileViewModel` saves a choice at once and typed text after `SAVE_DELAY` (1 s).
Each save reads `forOwner` again under one lock and updates that row, or makes one with
`Profile.new`, so a save never overwrites the colours or another field.

| Field | Column | Type |
|---|---|---|
| `birthDate` | `profile.birth_date` | Postgres `date`, TEXT in SQLite |
| `weightUnit` | `profile.weight_unit` | `kg` / `lb` / `mixed`; unknown reads as `Kg` |
| `groupByTag` | `profile.group_by_tag` | false by default |
| `cycleLength` | `profile.cycle_length` | 14 to 60 (`CYCLE_LENGTHS`, a check in Postgres), or null for 28 |

### 4.11 Statistics

Each load of the statistics screen reads all live sets of the account in one call
(`WorkoutSetRepository.all`). `domain/gym/Statistics.kt` does the arithmetic:

- `StatsPeriod.start` gives the first day of a period.
- `machineProgress` gives the two sets that a card compares; `bestSet` and `worstSet`
  (`SetStrength.kt`) order sets by `setStrength`.
- `bestPerDay` gives the points of the chart.
- `statsOrder` sorts the cards by a `StatsSort`. `StatsSort.Growth` compares the change between
  the two sets: the weight in kilograms, then the reps, then the sets equal to the best.

A machine without a set before the period always shows as improved. Its change runs from the
worst set of the period to the best; when the two are equal, the card shows "New machine".

**Web paging.** PostgREST returns at most `max_rows` rows (1000 by default) for each request. The
web thus reads sets in pages of 1000, ordered by `(recorded_at, id)`, and a shorter page ends the
read. The page size must not be more than `max_rows`.

**One machine.** `machineStatsUi` (`ui/stats`) makes the best set, the chart points and the
history of a machine, and `MachineStats` draws them. "Friend's machine" uses the same two for a
friend's machine, with `FriendsRepository.setsOn`.

### 4.12 Friends' data

Both platforms read friends' data from the server. Thus `SupabaseFriendsRepository` is in
`commonMain`, and no synced table holds its answers. Each platform binds it as
`TraineeFriends(KeptFriends(…))`: `TraineeFriends` gives a trainee no friends (see
[4.4](#44-managed-accounts)), and `KeptFriends` keeps the answers (see below).

- The web runs it on the UI client. Android runs it on `activeAccountSupabaseClient`, which asks
  `AccountTokens` for the active token for each request, because Android's UI client has a session
  only after a sign-in or a switch in the same process.
- Friends are the members of the groups that hold the viewer. While a child is active, the reads
  use the guardian's session; thus the child's friends are the people that the guardian and the
  child share a group with, the guardian included.
- The active account's groups are read into the process-wide `GroupsCache` when it becomes active,
  so the friends screen opens with them.

**Calendar.** One `groupVisits` call reads the friends' visits of the month (it reads the group
members, then their visits). The calendar reads
them after its own rows, when the screen opens, after a sync, after a switch and on a month change.
A new read cancels the read in flight; a result for another account or month is dropped. A failed
read leaves the friends out; the own calendar never waits for it.

**Feed.** `VisitFeedViewModel` reads the own rows locally and the friends' visits with one
`groupVisits` call for each 21-day window.

- It reads back window by window until a window has a visit of a shown person. The list asks for
  more when it is three items from its end.
- It ends at the oldest visit of the account and of `oldestGroupVisit`.
- The people (the account and its `mates`) are read with their colours after the first page of
  each start: from the kept answer, then from the server.
- A sync or a return to the screen reads the own rows again and keeps the windows. A switch
  starts the feed again.

**Friends' records on the machine page.** `GroupReads` (`app/ui/visit`) serves the visit view
model.

- `follow` takes the active account and the open own machine. It reads the group again (through
  `MachineReads.group`) when the account changes or `markStale` was called.
- When a group read lands, it reads `FriendsRepository.groupPeaks`: first the kept answer, then,
  when the group read was fresh, the server's answer.
- `friendRecords` gives one `FriendRecord` for each friend's machine of the open machine's
  cluster that has sets: the record in the friend's own unit. `friendLine` shifts it by the
  `platformShift` between the two machines.
- A read for an account other than the followed one is dropped. A failed read keeps what an
  earlier read showed.

**Gains.** A visit card compares the day's result (`dayBest`) with the prior best that the day's
sets store (see [4.8](#48-visits-and-sets)), so a card needs only the day's rows.

**Kept answers (Android).** `KeptFriends` wraps the repository and keeps its answers in the local
`friend_answer` table: one JSON text for each account and request. The table does not sync.

- It keeps the answers of `visits`, `sets`, `machines`, `mates`, `groupGym`, `oldestGroupVisit`,
  `groupLinks`, `groupPeaks` and `groupVisits`. Each other request goes to the server.
- A read inside `keptOnly` gives the kept answer, or fails when there is none. Other reads ask the
  server and keep the answer.
- The feed, a friend's calendar and the machine page (`GroupReads`) read inside `keptOnly` first,
  show the result, then read from the server. A failed server read keeps what shows.
  `MachineCatalogue.lastGroup` uses the kept `groupGym` when it has no read in memory.
- The kept visits of a viewer are one list: each window replaces the visits it covers, and visits
  older than 31 days go out. Each time an answer is kept, the answers kept more than 31 days
  before go out. A sign-out removes the answers of accounts that are no longer on the device.
- The web uses `KeptFriends` over `NoFriendAnswers`, which keeps nothing.

## 5. Backend

### 5.1 Schema

- The Postgres schema is in SQL migrations under `supabase/migrations/`, applied with the Supabase
  CLI (`supabase db push`). Never change the schema in the dashboard.
- The SQLDelight schema in `core` has the same columns as each synced table. Add a column to both
  in the same commit.
- A change to the local schema ships a SQLDelight migration (`N.sqm`); SQLDelight derives
  `Schema.version` from them. A migration that adds a column which the server fills resets
  `lastPullAt`, so each device pulls all rows again with the new values. `SchemaMigrationTest`
  tests the upgrades.
- A migration that needs Kotlin code runs it through the driver's callback
  (`CorePlatformModule.android.kt`), as `19.sqm` runs `tagConversion`.
- `ALTER TABLE … ADD COLUMN` puts a column at the end, so the `.sq` file declares it last too: the
  generated mappers read columns by position.
- A wire row that a client upserts has no Kotlin default values: supabase-kt encodes without
  defaults, and an upsert keeps a column that it does not send. `WorkoutSetRow` uses this: its
  `prior_best_*` columns default to null, so a null value is not sent. Its `pyramid` is
  `@EncodeDefault` (see [4.8](#48-visits-and-sets)).
- Readers map an unknown enum value to a default, so one row does not stop a pull. Old clients
  ignore unknown columns, but a value that they cannot map (such as the unit `custom` for a client
  before it) fails their pull.

### 5.2 Access rules

RLS applies each visibility rule of the functional spec. Because Postgres applies them, no client
code can leak data by an omission.

**Writes.**

- A user writes only rows with their own `user_id`, or with the id of a child they guard
  (`guards`). A trainer also writes some rows of a trainee (`coaches`; see **Trainer writes**).
- A set goes only into a visit and on a machine of the set's owner. A photo belongs only to a
  machine or profile of its owner. A `tag` and a `machine_tag` must have the ids of their content
  (see [4.6](#46-machines-and-tags)).
- The `before update` triggers, which call the function `user_id_stays`, keep `user_id` from
  changing on the gym tables and `workout_plan`. `profile`, `measure` and `measurement` have
  owner-only policies.
- Each synced table grants `select`, `insert` and `update` to `authenticated`. It never grants
  `delete` (clients soft-delete) and grants nothing to `anon`. Policies only filter rows that a
  role can already touch.

**Reads.**

- **The owner** reads all own rows.
- **A friend** reads the live `machine`, `visit`, `workout_set`, `machine_link`, `photo`,
  `tag` and `machine_tag` rows, and the owner's `photos` folder (`shares_group_with`).
- **A guardian** reads all `machine`, `visit`, `workout_set`, `machine_link`, `photo`, `tag`,
  `machine_tag` and `workout_plan` rows of the child, deleted ones included, and the child's
  `photos` folder.
- **A trainer** reads the same rows of the trainee as a guardian, except `machine_link`, and the
  trainee's `photos` folder.

`profile`, `measure` and `measurement` are for their owner only. Foreign-key checks ignore RLS, so
a row can name another user's id; this is safe, because RLS limits each read.

**Server functions.** Security-definer functions make the changes that a policy cannot allow:

- `break_machine_links(target)` soft-deletes friends' live links to a machine of the caller (or
  of a child they guard).
- `repoint_machine_links(removed, kept)` moves friends' live links from `removed` to `kept`.
  `kept` need not exist yet: it can be offline.
- `delete_my_account` deletes the caller from `auth.users`; each owned table cascades.
- `create_group`, `join_group` and `leave_group` change membership. The owner renames and
  soft-deletes the group directly.
- `offer_guardianship`, `accept_guardian(code)` and `end_guardianship(child, guardian)` make,
  accept and end a guardian link. A code is valid for 24 hours; a new one replaces the old one.
- `offer_coaching`, `accept_trainer(code)` and `end_coaching(trainee, trainer)` do the same for a
  coaching link.
- `my_family` and `my_coaching` list the caller's children and guardians, or trainees and
  trainers, with names and avatars.
- `guards(other)`, `coaches(other)` and `shares_group_with(other)` tell if the caller guards
  `other`, coaches `other` or shares a group with `other`. `is_group_member(target)` tells if the
  caller is a member of the group `target`.

- `accept_guardian` answers an unknown or expired code with `PT404`, and the caller's own code with
  `P0001`.
- `my_family` lists only children and guardians: released builds read each row of it that is not a
  child as a guardian.
- **Account deletion.** Storage objects do not cascade, so `SupabaseAccountServer` removes the
  caller's photos through the Storage API after `delete_my_account`, with the same token. Only
  after the server answers does `AccountDeletion` remove the rows from the device
  (`OwnedRowsPurge`) and sign the account out. When no other Google account is signed in on the
  device, the rows of the account's managed accounts also leave the device. `AccountDeletion`
  refuses a managed account, because its token is the guardian's.

**Trainer writes.** A trainer writes the trainee's `workout_plan` rows and inserts their
`machine`, `tag` and `machine_tag` rows. An update policy cannot tell a machine that the trainer
added from the trainee's own. Thus the triggers that call `trainer_keeps_row` refuse a trainer's
update that changes the content of a row or deletes it. A repeated upsert and a restore of a
deleted row pass.

**Names in groups.** `group_member.display_name` follows `member_display_name`: the nickname of the
newest live profile, else the Google name. `my_display_name` sets it when a user makes or joins a
group, and a trigger on `profile` updates the owner's member rows, because clients can only read
that table.

### 5.3 Configuration and secrets

- The core Gradle task `generateSupabaseConfig` writes `SupabaseConfig.kt` with `URL`, `ANON_KEY`,
  `GOOGLE_WEB_CLIENT_ID` and `WEB_APP_URL`. It reads `local.properties` first, then environment
  variables; CI sets them from repository secrets (`WEB_APP_URL` from a variable). Never commit
  these values. They are safe inside the built app, because RLS, not the key, controls access.
- Android makes invite links from `WEB_APP_URL`, and shares only the code without it. The web uses
  its own address.
- A build without these values is normal: nothing makes the Supabase client until a sign-in or a
  sync needs it. A new checkout thus starts and works anonymously on Android. The screens read
  `SignInAvailable` and do not offer sign-in: Android needs the URL, the key and the Google client
  id; the web needs the URL and the key.
- The web sign-in returns to the page's own address, not its origin, because GitHub Pages serves
  the app under a path. That address, and the local development server's, must be in the
  project's redirect allow list.

## 6. Android

- `applicationId` and the `androidApp` namespace are `monster.greyde.kachalochka`. `app` and
  `core` use namespaces below it, because two Android modules cannot share one.
- `minSdk` 29, so scoped storage is always on and photos have one code path. `compileSdk` 37.
- Signing, `versionCode` and `versionName` are in `androidApp`: the multiplatform library plugin
  has no build types.
- Koin starts once, in the `Application` subclass. A graph for each Activity would make a second
  DataStore on the same file after a configuration change, and DataStore refuses that.
- The camera uses the system take-picture contract, without a camera library. Do not declare the
  camera permission in the manifest: the system camera then needs it.
- Google sign-in uses Credential Manager. Its sheet needs an `Activity`, so the implementation
  takes a context supplier that the Activity installs.
- Release builds are minified and shrunk by R8, and the app bundle has the mapping file for Play.
  Class names change under R8, so code must never compare them. Wrap a platform error that common
  code reacts to in a type that `core` owns, as `SignInCancelledException` wraps the cancellation
  of Credential Manager.

**Rest sound.** `RestSoundService` is a `shortService` foreground service in the `app` manifest,
with `FOREGROUND_SERVICE`. A rest is much shorter than the few minutes that such a service gets.

- It shows the rest as a countdown notification on its own low-importance channel, so the channel
  makes no sound.
- At the end, it plays the chosen notification sound as media with transient ducking focus. Media
  goes to the headphones at the user's volume, and silent mode does not mute it. When focus is
  refused (for example in a call), the rest is silent. A sound that cannot play falls back to the
  default sound.
- A partial wake lock (`WAKE_LOCK`) keeps the delay running with the screen locked. The service
  stops after the sound.
- When the system refuses the foreground start (the app is in the background),
  `ServiceRestAlarm` logs the failure and that rest stays silent.
- Settings picks the sound with `ACTION_RINGTONE_PICKER` (notification sounds, no silence), and
  asks for `POST_NOTIFICATIONS` on Android 13 and later.

**Developer panel.** `DeveloperPanelScreen` (`ui/network`) is a `PrimaryTabRow` over the `PanelTab`
enum ("Info", "Proxy settings", "Options"); a new tab is a new entry. The selected tab is
`rememberSaveable`.
The home screen links to it only where a `DeviceNetwork` is bound, and only Android binds one: a
browser cannot choose a proxy or look up a host.

- The proxy is `SupabaseCredentials.proxy`, a Ktor `ProxyConfig`, with an optional
  `SupabaseCredentials.proxyLogin`. Each Supabase client, Coil's client and the checks' client call
  `HttpClientEngineConfig.goThrough(proxy, login)`, an `expect` function of `core`. Android binds
  credentials that add the stored `ProxySetting`, so a change applies at the next start.
- Requests are HTTPS, so the proxy sees only the CONNECT request and a header cannot reach it. The
  `actual` for OkHttp (`sqlMain`) sets a `proxyAuthenticator` that answers the 407 with
  `Proxy-Authorization: Basic`. A blank login sets none. The `wasmJs` `actual` does nothing.
- The proxy address is not resolved in advance: OkHttp resolves it off the main thread, and
  Android refuses a lookup on the main thread.
- `WorkManagerSyncTrigger` and `LastFailure` keep the last sync and the last background failure in
  memory only.

## 7. Testing

- **TDD is mandatory.** Write the failing test before the code.
- `:core:jvmTest` runs the `core` tests with the SQLDelight JVM driver and fake Supabase
  gateways. They cover each pure function, each DAO and the full sync.
- `:app:jvmTest` runs the `app` tests: view model tests and Compose desktop UI tests, with fake
  repositories from Koin overrides. No test uses the network or a real database.
- `.\gradlew check` is the quality gate: ktlint, `:core:jvmTest`, `:app:jvmTest` and the Android
  lint of `androidApp`. It must pass
  before a task is complete. It depends on those suites directly, not on `allTests`, so it starts
  no Node, npm or Wasm test compilation. ktlint still checks the wasmJs sources. The root build
  script holds this setup and the ktlint filter of each module.
- Some tests enforce rules of this document: `DomainPurityTest` (see [2](#2-modules-and-layers)),
  `SchemaMigrationTest` (the `.sqm` upgrades) and `LegalPagesTest` (the privacy, terms and entry
  pages; `:app:jvmTest` takes the web resources as an input).
- `check` does not compile the wasmJs main sources. After a change in `commonMain`, also run
  `:app:wasmJsBrowserDistribution`, as CI does.

**Test helpers** in `app/src/jvmTest`:

- `runNavigationUiTest` is for each test that shows a `NavHost`. It sets an unconfined
  `Dispatchers.Main`, which `androidx.lifecycle` needs to move back-stack entries. The cost:
  lifecycle cannot find a main-thread error in the code under test. It also refuses saved state
  that an Android `Bundle` cannot hold, as Android does. Thus the keys of a lazy list or a pager
  must be strings or other `Bundle` types, not value classes such as `MachineId`.
- `runScreenTest` shows one screen in a single-destination `NavHost`, so the screen gets a
  `ViewModelStoreOwner`. It supplies fake repositories, a fixed clock and a ticker that moves only
  when the test tells it to. `runScreenTestInRussian` does the same in Russian.
- The tests speak English (see [9.3](#93-strings-and-languages)). A test keeps Cyrillic only when
  it checks Cyrillic-specific behaviour, such as letter case, search or Russian plurals. Else it
  compares with `RuStrings` members inside `inRussian { }` or `runScreenTestInRussian`.

## 8. Build, CI and release

- `master` is the only long-lived branch. Each push to it runs CI.
- **`ci.yml`** runs on each push to `master` and on each pull request. Its `check` job runs
  JDK 21 and one Gradle call,
  `check :androidApp:assembleDebug :app:wasmJsBrowserDistribution`, then uploads the web bundle.
  Its `deploy` job needs `check` and publishes that bundle to GitHub Pages on a push to `master`.
  Thus nothing reaches Pages that did not pass the gate.
- **`release.yml`** runs on a `v*` tag. It runs `check`, builds a signed release APK and app bundle
  from secrets, makes a GitHub Release with `kachalochka-X.Y.Z.apk`, and uploads the bundle to the
  Google Play internal testing track. It also keeps the bundle as a workflow artifact, so you can
  upload it by hand when the Play upload fails. Promotion past internal testing is done by hand in
  the Play Console.
- **Secrets.** Both workflows pass `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `GOOGLE_WEB_CLIENT_ID`
  (secrets) and `WEB_APP_URL` (a variable). `release.yml` also uses `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` and `PLAY_SERVICE_ACCOUNT_JSON`.
- **Signing.** Play App Signing uses the same key as the GitHub APK, so a Play install and a
  sideloaded one update each other, and Google sign-in needs one Android OAuth fingerprint. The
  same keystore is the upload key.
- **Version.** `versionName` comes from the tag (`-PversionName`); `versionCode` is
  `major * 10000 + minor * 100 + patch`, so a rebuild of a tag gives the same number. A build
  without it is version 0.1.0, code 1.
- **Changelog.** `changelog.txt` is the user-facing history, newest version first. The
  release notes are the tag's section of it; a tag without a section fails before the build. A
  release is one commit that adds a section, one annotated `vX.Y.Z` tag and a push: `master`
  first, then the tag. Use the `release` skill.
- **Shown version.** `AppVersion.NAME` is generated at build time: the `versionName` of a release
  build, else the newest heading in `changelog.txt`. The web deploys from `master` without a tag,
  so the changelog is the source that both platforms can read.
- Gradle runs with the configuration cache. CI uses the checked-in wrapper.
- **Commit hook.** `.githooks/pre-commit` refuses a commit that adds Cyrillic outside
  `RuStrings.kt`, `GLOSSARY.md` and the tests of Cyrillic-specific behaviour that it lists. It
  checks only added lines, so older text does not block an edit next to it. Enable it once in each
  clone: `git config core.hooksPath .githooks`.
- **Privacy and terms.** `privacy.html` and `terms.html` are static pages in the web resources.
  The OAuth consent screen and the Play listing link to them. The Play account deletion link is
  `privacy.html#deletion`, so keep that anchor. The entry page links to both in plain HTML under
  `#app`, because the app draws on a canvas. `App` tells `PageFooter` which screen shows; the web
  hides the footer except on the home and sign-in screens.
- The license is the GNU GPL v3, in `LICENSE`.

## 9. UI

### 9.1 Theme

- One `MaterialTheme` wrapper in `app/src/commonMain` holds the light and the dark `ColorScheme`.
  It is the only place that declares colours: screens read `MaterialTheme.colorScheme`, never a
  `Color` literal. This makes both themes correct by design.
- The colour schemes come from the ramps of the Nocturne design system: the design draws the dark
  theme, and the light theme uses the same ramps.
- Friend colours are the only palette outside `ColorScheme`: eight colours for each scheme, in the
  same file, different from `primary`, given by `friendColor(index)`. The green of a gain is
  `gainColor()`.
- Vico charts and `Canvas` drawings take their colours from the scheme.
- `App` tells `SystemBars` if the scheme is dark. On Android the status and navigation bars are
  transparent, with icons that match the scheme; without this, Android draws a scrim of the light
  window theme.
- Icons are Phosphor Regular (`DropFill` is Phosphor Fill), copied as `ImageVector`s into
  `app/.../ui/icons` with their MIT licence. The Phosphor library has every weight of every icon,
  about 27 MB for each platform.

### 9.2 Device settings

Device settings do not sync and are not in `core`. They sit on one seam, `DeviceStore`
(`app/.../ui/settings`): `read(key)` answers at once from memory, and `write(key, value)` suspends.
A null value removes the key. A `DeviceKey` has a name and one of four value types: text, number,
flag or text set. A store that cannot be read gives null, which becomes the default.

| Adapter | Where | Storage |
|---|---|---|
| `DataStoreDeviceStore` | `androidMain` | The `DataStore<Preferences>` in `theme.preferences_pb`; one key type for each value type |
| `LocalStorageDeviceStore` | `wasmJsMain` | `localStorage` strings; a text set is joined by spaces; a browser that refuses site storage gives null and ignores writes |
| `InMemoryDeviceStore` | `commonMain` | A map, for the `jvm` target and the tests |

`DataStoreDeviceStore` reads the file once, when Koin builds it, and then answers from that
snapshot. The Android module binds it with `createdAtStart`, so the first frame already has the
stored values. Every client is built with the stored proxy, so that read must finish before any
client exists.

Each setting is a class in common code over a `DeviceStore`. It extends `DeviceSetting`, which
holds a `MutableStateFlow` that starts from the stored value. `set` writes the store first and then
changes the flow. The class has the keys, the default and the decoding. A stored value that cannot
be decoded gives the default. `appModule` binds the settings. Each platform module binds only
`DeviceStore`.

| Setting | Class and value | Keys |
|---|---|---|
| Theme | `ThemePreference`: `ThemeMode`: `System`, `Light`, `Dark` | `theme_mode` |
| Language | `LanguagePreference`: `AppLanguage` | `language` |
| Transition | `TransitionPreference`: milliseconds | `transition_millis` |
| Rest sound | `RestSoundPreference`: on or off, and the Android sound URI (null for the default) | `rest_sound`, `rest_sound_uri` |
| Visits mode | `VisitsModePreference`: calendar or feed | `visits_mode` |
| Feed person | `FeedPeoplePreference`: at most one person for each viewer | `feed_selected_person` |
| Set comment | `SetCommentPreference`: whether the set form has a comment field | `set_comment_shown` |
| Proxy | `ProxyPreference`: the address, whether it is used, the login and the password | `proxy_address`, `proxy_used`, `proxy_login`, `proxy_password` |
| Statistics picture rows | `StatsPictureRows`: the most rows on one picture, 15 by default | `stats_picture_rows` |

The keys and their types are the stored format. A change of a key name or of its type loses the
stored value.

- A transition length stored before the presets shows as the nearest preset.
- `NavHost` fades over a box painted with the scheme's `background`. The Android window theme is
  light, and a fade over the bare window would show it through both screens.

### 9.3 Strings and languages

- Each text of the app is a member of the `Strings` interface in `app/.../ui/strings`, with
  `RuStrings` and `EnStrings`. Counts are functions with each language's plural rule. Compose
  resource files are not used, because view models and the shared text make most strings outside
  composition.
- `AppStrings` holds the current `Strings` as a process-wide `StateFlow`. Formatters and view
  models read `AppStrings.current`; composables read `strings()`.
- The language changes only in Settings. Text that a view model made is made again when its
  screen opens again: most screens read again on entry, and the others call `speak()`. No view
  model subscribes to `AppStrings`. The measurement form keeps its title until it opens again, so
  that it keeps the typed values.
- `AppLanguage.System` is Russian when the device language tag starts with `ru`, else English
  (`SystemLanguage`). `App` sets `AppStrings` while it composes, so the first frame has the right
  language.
- Russian text is only in `RuStrings.kt` and in the tests of Cyrillic-specific behaviour (see
  [7](#7-testing)); code and comments are in English.
  `RuStrings` and `EnStrings` each name their own language (`languageName`) for the language
  list.
- `AppStrings` starts with `defaultStrings` until `App` sets the chosen language: Russian on
  Android and the web, English on the JVM. The JVM target runs only the host tests, and it also
  binds the system language to English, so the English assertions pass on each machine.
- Ready-made measures show the name of their kind. User data (machine names, own units, tags,
  comments, measure names) is never translated.

### 9.4 Local time

`kotlin.time` has no time zones. `app` reads the UTC offset from the platform
(`java.util.TimeZone` on Android and the JVM, `Date.getTimezoneOffset` on Wasm) and gives it to
the pure day and clock functions of `domain/`. Calendar dates are `CalendarDay` values with
epoch-day arithmetic, because the stack has no date library.

### 9.5 Weight units

Each gym weight on a screen goes through `app/ui/format/WeightUnits.kt`:

- `shownUnit` picks the unit from the machine's unit and the profile's `PreferredWeightUnit`.
- `shownWeight` converts to the nearest half unit; `shownStep` to one decimal.
- The formatters that write weights (`setsSummary`, `setsSummaryParts`, `setValue`,
  `weightCaption`, `platformSuffix`, `setChange`, `dayBestGain`, `sharedName`, `visitShareText`)
  take the preferred unit.
- Only the set form's weight is in the machine's own unit, with `recordingConversion` under it.

View models read the preferred unit with `ProfileRepository.preferredUnit` each time they read
again.

### 9.6 Sharing

**Text.** `TextSharing` is bound for each platform in `platformModule()`. Android's
`ShareSheetTextSharing` opens the share sheet. The web's `ClipboardTextSharing` writes the
clipboard and returns the message that the screen shows. `InviteSharing` does the same for
invites.

- `visitShareText` (`app/ui/format`) makes the text of a visit. Its `setsSummary` writes one
  machine's weights and reps, and each screen that sums up a machine's sets calls it, so they read
  as the shared text does. `sharedName` writes a machine's name, with a platform weight that the
  sets leave out; the text of a visit, each visit card and each statistics card use it.
- A browser accepts a clipboard write only just after the user's gesture, so the text's parts are
  read before the tap: `ownCard` and `friendCard` put a `SharedVisit` and the date into the
  `VisitCardUi`, and `SharedVisit.text` writes it at the tap. A friend's card has them once
  `FriendDayDetails` is read; its `friendMachines` give the card its menu. The statistics text
  (`statsExportText`) uses the cards that are already on screen.
- `SharePopup` (`app/ui/share`) is the popup of both shares. `StatisticsViewModel` offers it from
  `export()` and shares the text or the picture; `statsHeader` writes the first line of both.
- `VisitShareViewModel` is the one place that shares a visit. `VisitShareDialog` shows the popup,
  and `VisitShareNotice` shows the message under the card with the tag that the share offered.

**Picture.** `CardSharing.available` is true only on Android; when it is false, `SharePopup` hides
"As image". `OffScreenPicture` (`app/ui/share`) composes its content off screen with a fixed width
and `LocalDensity`, so the picture is 1080 px wide on each phone, and records it into a
`GraphicsLayer`. When the content has settled (the avatar has its final look),
it gives the picture as strips of at most 2048 px in height, from top to bottom: a second layer
draws one part of the first and `toImageBitmap()` reads it. A bitmap from a layer is in GPU
memory, and a GPU cannot hold a picture that is higher than its limit, which each device sets.
`CardSharing.share` takes a list of pictures, each a list of strips. Android joins the strips of
each picture into one bitmap in normal memory and encodes it as PNG. It shares them through the
cache and the `FileProvider`: `ACTION_SEND` for one picture, `ACTION_SEND_MULTIPLE` with a
`ClipData` of all the URIs for more. `CardPicture` draws `SharedVisitCard` in it, for
`VisitShareDialog` and for `StatisticsScreen`.

`StatisticsViewModel.shareImage` splits the overview with `pictureChunks(sections, limit)`
(`StatsPictures.kt`; `limit` is `StatsPictureRows`) and makes one `StatsPictureUi` for each chunk:
a `VisitCardUi` whose rows are the chunk's cards, and the first and last day of the period as the
caption. The state's `picture` is the first picture that is not drawn. `pictureDrawn` keeps its
strips; after the last picture, the view model calls `CardSharing.share` once. The screen wraps
`CardPicture` in `key(card.tag)`, so each picture has its own `OffScreenPicture`.

### 9.7 Rest timer sound

`RestSoundScheduler` (one for the process) observes `RestTimer.startedAt` and the preference.
While the sound is on, it gives each rest that did not end to the platform's `RestAlarm`, which
replaces the previous one. When the sound goes off, it cancels the alarm.

- Android's `ServiceRestAlarm` gives the rest to `RestSoundService` (see [6](#6-android)).
  `RestSoundChooser` picks the sound in Settings.
- The web's `WebAudioRestAlarm` sets a timeout that plays an 880 Hz beep of 200 ms through the Web
  Audio API, with one audio context for each page. While the sound is on, each tap wakes the
  context, because Safari starts audio only in a gesture handler. While it is off, the page opens
  no audio context. `FixedRestSound` offers no choice.
