# Release 1.0.3 — design

Backlog issues 14–21: group machines in the machine list, linking and merging machines, the slow
friends screen, friends in the calendar, sharing a visit with a nickname, drag-and-drop ordering,
body measures and a body-fat calculator. Each issue is implemented and committed on its own, in
the order of the sections below, and each updates `functional_spec.md` and `technical_spec.md`
in the same commit as the code.

## 1. Friends screen loads slowly (issue 16)

While `GroupsViewModel` has no answer yet, `GroupsScreen` treats the missing list as an empty
one and shows "Групп пока нет", so a slow first read looks like a missing group.

- The screen distinguishes *loading* (null list) from *empty*: loading shows a muted
  "Загрузка…" line, never the empty text.
- `SupabaseFriendsRepository.groups()` issues its two reads (`friend_group`, `group_member`)
  concurrently instead of one after the other.
- The remaining latency is measured on the emulator before the issue is closed: time to the
  client's first request, token resolution in `AccountTokens`, and each request. Whatever
  dominates is fixed at its cause (for instance a token refresh that could be skipped, or a
  client built lazily on the first read); the fix is described in the commit.

Tests: the view-model test asserts the loading state before the fake answers; the screen test
asserts no empty text while loading.

## 2. Drag-and-drop ordering (issue 19)

"Порядок" on the visit screen switches ordering on and turns into "Готово"; "Готово" switches it
off. The arrow buttons and their code are removed.

- In ordering mode every machine header and every set row shows a drag handle (≡,
  `PhosphorIcons.DotsSixVertical` vendored if missing) at its left edge. Only the handle starts a
  drag; rows keep their normal look otherwise.
- Dragging a machine header moves the machine's whole block (header and its sets); dragging a set
  moves it among its own machine's sets. The dragged item follows the finger, drawn above the
  others with a 1 dp outline in `onBackground` and the surface colour, as in the reference
  screenshot; the others shift to show where it will land.
- The drop writes once. `domain/gym/SetOrder.kt` replaces `machineMoved` and `setMoved` (one step
  up or down) with `machineMovedTo(visitSets, machine, index, now)` and
  `setMovedTo(visitSets, set, index, now)`, both returning only the sets whose position changed,
  renumbering first when positions collide, as today.
- The target index is computed from measured item heights by a pure helper in `app`
  (`dropIndex(offsets, heights, from, dragY)`), unit-tested apart from the gesture.

Tests: domain tests for the two functions; a Compose test drags a handle with
`performTouchInput` and asserts the new order; a test asserts no arrow tags exist.

## 3. Nickname and the account profile (issue 18, part 1)

Each signed-in account has one synced `profile` row. It already exists in both schemas and in
the sync pass, and is unused so far.

- `ProfileRepository` gains `forOwner(owner: UserId?): Profile?` — the owner's live profile with
  the newest `updated_at`; every profile write updates the row it returned. A new profile for a
  signed-in owner takes the owner's id as its id, so two devices creating it offline converge on
  one row; an anonymous one takes a random id. `SqlOwnerlessRows.claim` does not cover `profile`
  today and is extended to it (and to `measure` and `measurement`, section 8), with
  `ownerlessIds` / `claimOwnerless` queries per table. After a claim an owner may hold two
  profiles; `forOwner`'s newest-wins rule decides.
- `Profile` gains `friendColors: Map<UserId, Int>`, `sex: Sex?`, `birthYear: Int?` and
  `heightCm: Double?` (sections 5 and 9). The wire and SQLite columns are `friend_colors`
  (text holding a JSON object, `'{}'` when empty), `sex` (`male` / `female`), `birth_year` and
  `height_cm`. SQLDelight `5.sqm` adds them at the end of the table and sets every
  `syncState.lastPullAt` to null, as `4.sqm` did: rows pulled before the upgrade lack the new
  columns, and migration 0008 writes `machine_link` rows older than a 1.0.2 device's watermark.
  `SchemaMigrationTest` covers the reset.
- `display_name` is the nickname. Settings shows a "Ник" field for the active account when one is
  signed in, with the account's Google name as the placeholder; blank means "use the Google
  name". At most 40 characters.
- Friends see the nickname. A `security definer` trigger on `profile` insert or update of
  `display_name` rewrites the owner's `group_member.display_name` rows (clients may only read
  that table). Both it and `my_display_name()` use one rule: the nickname of the owner's newest
  live profile when not blank, else the Google name, read by a separate helper
  `google_display_name(user)` split out of `my_display_name()`.
- `Nickname` in `app` resolves the name to show: the profile's nickname, else the account's
  display name, else empty (Android without an account).

## 4. Sharing a visit (issue 18, part 2)

The visit screen's top bar gets a share icon (`PhosphorIcons.ShareNetwork`) whenever the visit has
sets. It shares plain text through the same platform mechanism as invites: `InviteSharing` is
generalised to `TextSharing.share(text): String?` — Android's share sheet, the web's clipboard with
a "Скопировано" confirmation.

The text is built by a pure function `visitShareText(nickname, day, groups)` in
`app/ui/format`, tested in `commonTest`; `groups` is the visit's machines in visit order, each
with its sets in visit order:

- Line 1: `"<nickname>, <weekday>"`, or just `"<weekday>"` with no nickname. Weekdays: пн, вт, ср,
  чт, пт, сб, вс. Line 2 is empty.
- One line per machine, in visit order: `<name><platform> <weights> <reps>`.
- `<platform>` is `" (+76кг)"` when the machine has a platform weight that is not included in the
  record, as the visit screen shows it, converted like the weights; otherwise empty.
- Weights are converted to kg: kg as is, lb × 0.45359237 rounded to the nearest 0.5; a custom unit
  is written as is with its label after a space (`"3-4 плитка"`). Numbers use the app's decimal
  comma (`formatNumber`).
- With `n` sets, weights `w` and reps `r`:
  - all `w` equal and all `r` equal: `"14кг 3x12"` (`n`x`r`);
  - `w` differ, `r` equal: `"20-20-30-40-40кг 5x10"`;
  - `w` equal, `r` differ: `"41кг 10-15-15-15"`;
  - both differ: `"35-35-30кг 10-10-15"`.
  "Equal" compares the converted, rounded values. When every weight is 0 the weight part is
  left out (`"Подтягивания 3x10"` or `"Подтягивания 10-8-6"`).

## 5. Friends' visits in the calendar (issue 17)

- `FriendsRepository.groupVisits(viewer, from, to): List<FriendVisit>` reads the live visits of
  every group mate around the shown month in one request (`user_id in mates`, and `day` between
  the bounds or, for a visit without a day, `recorded_at` within a day of them), which stays far
  under PostgREST's 1000-row cap; `FriendVisit(friend, visit)`.
- `CalendarViewModel` loads them after its own rows, online, and again on entering the screen,
  on changing the month and after an account switch. A failed read leaves friends out silently; the own calendar never
  waits for it.
- Each friend gets a palette index. `domain/friends/FriendColors.kt` holds
  `assignedColors(existing, friends, paletteSize, random)`: the existing map plus an index for
  every friend without one, chosen at random among the least-used indices. `FriendColors` in
  `app` reads the active profile, assigns, and writes the profile back only when something was
  added. The palette is eight colours declared in `ui/theme` (both schemes), the only place
  colours live.
- `DayUi` carries `friendDots: List<Int>` (palette indices of friends who trained that day, one per
  friend, ordered by name). The cell draws the own dot (`primary`) and then friend dots, at most
  four in total, in one centred row.
- Below the own day card, the chosen day lists one card per friend visit: a colour dot, the
  friend's name, and the machine and set counts once that visit's sets are read. Tapping it
  opens the existing read-only friend visit.
- The group screen shows each member's colour dot (not the viewer's own); tapping it opens a
  palette dialog, and choosing writes the profile.

## 6. Linking and merging machines (issue 15)

### 6.1 Model

A link is its own synced row, replacing `machine.link_id`:

```
machine_link(id, user_id, machine_id, linked_machine_id, updated_at, deleted)
```

`user_id` owns `machine_id` and linked it to `linked_machine_id`, another member's machine. Links
are undirected for reading: machines connected through live links, in either direction and
through any number of hops, are one physical machine (a *cluster*).
`domain/gym/MachineClusters.kt` computes clusters from a list of links (union-find), and every
reader asks it instead of comparing `linkKey`s. `Machine.linkId` and `linkKey` are removed.

- RLS: a user reads their own links and the live links of anyone sharing a group; inserts and
  updates only their own. No foreign keys on either machine column and no ownership check
  against `machine` in the policy: a link may be written before its machine reaches the server.
- Both schemas carry the table (SQLDelight `5.sqm`); the sync pass pushes and pulls it and
  claims it on sign-in. `MachineLinkRepository` has Local and Remote implementations; friends'
  links come from `FriendsRepository.groupLinks(viewer)` online.
- `machine.link_id` stays in both schemas for clients before 1.0.3 and is no longer read or
  written by new clients: `MachineRow` drops the field, so an upsert leaves it untouched, and the
  SQLite column stays in `Machine.sq`, written as null and ignored by `machineOf`.
- Migration `0008` converts 1.0.2 links once. 1.0.2 links by key: a copy stores the friend's
  `coalesce(link_id, id)`, and unlinking either side writes a random key. So live machines are
  grouped by `coalesce(link_id, id)`; for every key shared by machines of two or more users, a
  `machine_link` row is written from each other machine of the group to one representative — the
  machine whose id is the key if it is live, else the one with the oldest `updated_at`. Links an
  older client writes after the migration are not converted.

### 6.2 Linking

The machine form of an own, saved machine gets an outline button "Привязать к…". It opens a
chooser (search field, like the picker) with two sections:

- **"Мои тренажёры"** — the owner's other machines. Choosing one asks "Объединить тренажёры?" and
  merges (6.3).
- **"Тренажёры друзей"** — one row per friends' cluster that does not already contain this machine,
  as in the picker. Shown when signed in and online. Choosing one writes a link from this machine
  to it and returns to the form.

The form shows "Связан с: <names of friends in the cluster>" under the name when the cluster has
friends' machines. The menu's "Отвязать от друзей" keeps its confirmation and breaks every
*direct* link of this machine: friends' links into it first, through the definer function
`break_machine_links(machine)`, then the owner's own links, soft-deleted locally. Either side can
thus break a link. Since friends' links can only change online, unlinking needs the network like
merging: offline it reports "Нет связи с сервером" and writes nothing.

Both definer functions check ownership: `break_machine_links(machine)` soft-deletes live links
whose `linked_machine_id = machine` only when the caller owns `machine` on the server;
`repoint_machine_links(removed, kept)` (6.3) requires the caller to own `removed` and `kept` not
to be owned by anyone else, but does not require `kept` to exist yet, since it may have been
created offline.

Picking a friend's machine in the machine picker (and "Взять себе", section 7) creates the own
copy with the friend's settings and a link from it to the friend's machine.

### 6.3 Merging

Merging removes a duplicate outright; it creates no link. Of the two machines the *older* is kept:
the one whose earliest live set is earlier; a machine without sets counts as newest; a tie keeps
the machine being edited. `mergedMachines(kept, removed, sets, links, now)` in `domain/gym`
returns the rows to write: every own set on `removed` moved to `kept`, every own link from
`removed` moved to `kept`, and `removed` soft-deleted.

When signed in, the definer function `repoint_machine_links(removed, kept)` first rewrites
friends' links that point at `removed`; merging therefore needs the network, and offline it
reports "Нет связи с сервером" and writes nothing. Anonymous merging is local only. Unsaved
edits in the form are discarded by a merge. After a merge the form of the kept machine replaces
the chooser and the old form.

### 6.4 Readers

- **Picker and machine list**: friends' machines are grouped by cluster; a cluster containing an
  own machine shows only the own machine(s); any other cluster shows one friend machine, the
  original-most (fewest outgoing links), then by owner name, then id.
- **Set sheet**: `latestOn(viewer, machineIds)` takes the friends' machine ids of the own
  machine's cluster instead of a link key.
- **Friend visit**: `namesForViewer` names each of their machines by the viewer's machine in the
  same cluster, else by its own name.

## 7. Group machines in the machine list (issue 14)

"Тренажёры" lists the own machines at once and, after the network answers, "Тренажёры друзей": one
row per friends' cluster without an own machine (6.4), with "<friend> · <caption>". Offline or
anonymous, the friends' section is absent. Tapping a friend's row opens "Тренажёр друга", a
read-only view of its settings (name, owner, setup note, weight caption, platform) with the
accent button "Взять себе", which makes the linked own copy (6.2) and replaces the view with that
copy's form.

## 8. Body measures (issue 20)

Private to each account: RLS lets only the owner read or write, and no group policy exists.

### 8.1 Model

```
measure(id, user_id, name, unit, kind, position, updated_at, deleted)
measurement(id, user_id, measure_id, day, value, updated_at, deleted)
```

- `kind` names a predefined measure (`weight`, `waist`, `chest`, `hips`, `biceps`, `thigh`,
  `neck`, `body_fat`), null for the user's own; an unknown name reads as null. Units are free
  text (`кг`, `см`, `%`).
- A measure has at most one value per day. Writing a day's value updates the day's live row for
  that measure if there is one; clearing it soft-deletes the row. Where two devices wrote the same
  day, readers take the newest `updated_at`.
- Both tables exist in SQLDelight (`5.sqm`) and Postgres, sync like the gym tables and are claimed
  on sign-in. `measurement.measure_id` has no foreign key, since a seeded measure may reach the
  server after its first value.
- Repositories: `MeasureRepository` (`upsert`, `all(owner)` live by position, `anyFor(owner)`
  including deleted) and `MeasurementRepository` (`upsert`, `all(owner)` live, newest first),
  Local and Remote.

### 8.2 Predefined measures

Eight kinds are predefined: Вес (кг), Талия, Грудь, Бёдра, Бицепс, Бедро, Шея (см) and Жир (%).
Whenever the screen opens, each kind the owner has no row of — live or deleted — is created, so
a deleted one stays deleted. For a signed-in owner their ids are derived from the owner id and
the kind (`derivedId`, a deterministic v4-shaped UUID from two FNV-1a hashes) and their
`updated_at` is the epoch, so two devices seeding offline write the same rows. A Postgres
trigger on `measure` ignores an update whose `updated_at` is older than the stored one, so a
late seed never overwrites a rename: the one exception to "the outbox wins" (technical spec
§4.2), which the technical spec records. An anonymous owner gets random ids; if such rows are
later claimed by an account that already has predefined rows elsewhere, both appear and the user
deletes one.

### 8.3 Screens

- **Home**: a "Замеры" row.
- **"Замеры"**: one row per measure: name, latest value with unit, the change from the previous
  value (`"−0,4"`, muted, omitted with one value), and `daysAgoLabel` of the latest. "Порядок"
  reorders with the drag handles of section 2 (writing `position`). Bottom: accent "Новый замер",
  outline "Добавить показатель" (name and unit dialog).
- **"Замер"** (form): the day at the top, today by default; tapping it opens the month calendar
  (`MonthGrid`) in a dialog, future days disabled, days with values marked. One decimal field
  per measure with the unit and the previous value as placeholder. Opening a day that has values
  loads them. "Сохранить" writes changed fields and soft-deletes cleared ones; "Удалить замер"
  (only when the day has values) asks and clears the day.
- **Measure** (tapping a row): a line chart of the values with chips 1 мес / 3 мес / 6 мес / Год /
  Всё (default 3 мес), the latest value and the change over the period, then every value newest
  first; tapping one opens that day's form. The top-bar menu renames it, changes the unit, or
  deletes it with its values after confirmation.
- The chart uses Vico, the stack's charting library, if its multiplatform artifact supports all
  three targets; otherwise a small Compose `Canvas` line chart in `app`. Its colours come from
  the theme.

Weekly habit: days are chosen freely; the list and chart assume roughly weekly points (the x-axis
labels months, and a period with fewer than two points shows the value instead of a line).

## 9. Body-fat calculator (issue 21)

The "Жир" field in the measurement form has a "Рассчитать" button opening a sheet.

- Inputs: weight, waist, neck and hips from the form, or the owner's latest value of that kind
  when the field is empty; sex, birth year and height from the profile. When one of these three is
  missing the sheet asks for it first and saves it to the profile.
- Methods, pure functions in `domain/measures/BodyFat.kt` returning null without their inputs,
  tested against published reference values:
  - **US Navy** (cm): men `495 / (1.0324 − 0.19077·log10(waist − neck) + 0.15456·log10(height)) −
    450`; women `495 / (1.29579 − 0.35004·log10(waist + hips − neck) + 0.22100·log10(height)) −
    450`.
  - **YMCA** (lb, in): `(−98.42 [men] or −76.76 [women] + 4.15·waist − 0.082·weight) / weight ·
    100`.
  - **Deurenberg**: `1.20·BMI + 0.23·age − 10.8·[male] − 5.4`, age in whole years at the form's
    day.
- The sheet lists each method with its result (`"18,4 %"`, one decimal) or the missing inputs.
  Tapping a result fills the field; nothing is saved until the form is.

## 10. Plumbing every new synced table needs

For `machine_link`, `measure` and `measurement`: a `.sq` file and its part of `5.sqm`; the wire row
(no Kotlin defaults); `SyncGateway` push and pull, `SupabaseSyncGateway`, `FakeSyncGateway`;
`LocalSyncRows` read and write; `SyncPass` push branch, `PulledRows`, the pending-skip and the
watermark; `SqlOwnerlessRows.claim`; column adapters in `DatabaseFactory`; Local and Remote
repositories and their Koin bindings; `FakeGym` / `FakeFriends` fakes for app tests.

`SyncPass` orders pushes by rank instead of the current boolean sort: `machine`, `visit`,
`profile`, `measure` first, then `machine_link`, `workout_set`, `measurement`.

New routes: `FriendMachineRoute(machineId)`, `LinkChooserRoute(machineId)`, `MeasuresRoute`,
`MeasurementFormRoute(day)`, `MeasureRoute(measureId)`.

## 11. Server migration and release

`supabase/migrations/0008_links_profile_measures.sql` holds everything server-side: `machine_link`
with its RLS and the one-time conversion; `repoint_machine_links` and `break_machine_links`; the
profile columns, `google_display_name` and the display-name trigger; `measure` and `measurement`
with RLS and the stale-update trigger; grants as in §5.2 of the technical spec. It is applied
with `supabase db push` before the release. Then 1.0.3 is cut with the `release` skill.
