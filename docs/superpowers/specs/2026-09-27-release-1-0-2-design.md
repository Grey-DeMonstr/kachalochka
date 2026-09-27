# Release 1.0.2 — design

**Date:** 2026-09-27

Nine issues from the backlog, in three parts that ship together as 1.0.2:

- **Part A — machines.** Issue 5: the weight step is any decimal. Issue 6: a set's weight is
  typed, with a comma or a point. Issue 7: "Своя единица" becomes a real unit with its own name.
  Issue 8: "Противовес" goes. Issue 11: a list of the account's machines, reached from home.
- **Part B — visits.** Issue 10: one visit per day, with no start or end. Issue 9: machines and
  sets within a visit keep an order the user can change.
- **Part C — groups and linked machines.** Issue 12: friends form groups and read each other's
  visits. Issue 13: a machine can be linked to a friend's copy of the same physical machine.

No design frame draws any of this, so every new element is built from the Nocturne components
already in `ui/components` and follows the existing screens' copy style.

---

## 1. Scope

Built:

- A decimal weight-step field on the machine form, with 1 / 2,5 / 5 / 10 as quick picks.
- A typed weight in the set sheet; − and + keep stepping by the machine's step.
- A third unit, "Своя единица", named by the user; weights on that machine carry the name.
- Two weight-counting modes, "Всего" and "На сторону"; counterweight machines become "Всего".
- A "Тренажёры" home row: the account's machines, each opening the machine form, and "Новый
  тренажёр".
- One visit per calendar day. No "Начать визит" or "Завершить визит"; the home card is
  "Сегодня". The calendar adds a visit on any past day or today and moves a visit onto a day,
  replacing that day's visit after a confirmation.
- A one-time normalization turning existing visits into day visits.
- An order of machines and of sets inside a visit, changed with arrows in a "Порядок" mode.
- Groups: create, invite by link or code, join, leave, delete. A group-mate's calendar and
  visits, read-only.
- Linked machines: a friend's machine cloned into one's own list, friends' results on it in the
  set sheet, and a way to break the link.

Not built: deleting a machine, unit conversion, drag-and-drop ordering, a group newsfeed, Android
App Links for invites, editing a friend's data, offline access to friends' data.

---

## 2. Decisions

Each decision, then why.

### 2.1 Part A

- **`counterweight` is read as `total` wherever it appears, and so is any weight mode this
  version does not know.** Clients from before 1.0.2 still write `counterweight`, and a reader
  that throws on a name fails the whole sync pass it is part of.
- **The Postgres check on `weight_mode` keeps accepting `counterweight`.** A 1.0.1 client whose
  push the server refuses keeps that row in its outbox and fails every pass from then on.
- **The server migration converts counterweight rows and stamps `updated_at = now()`; the local
  `2.sqm` converts without stamping or enqueuing.** Stamping on the server lets every device pull
  the change once; stamping locally would have every device push the same change back.
- **A unit this version does not know reads as `kg`.** Same reason as the modes: one odd row
  must not stop a pull.
- **`unit_label` is `''` unless the unit is `custom`; the form saves `''` for kg and lb.** One
  representation per unit; the label means something only for a custom unit.
- **A custom unit needs a label: trimmed, at most 12 characters, shown exactly as typed.** Free
  text cannot be declined reliably, and 12 characters still fit the stepper caption "плитка всего
  · ±1" on a phone.
- **A custom unit with a blank label, which only a row written elsewhere can have, shows as
  "ед."** The label is never validated by the server.
- **The weight step is a decimal field, `> 0`, rounded to three decimals; 1 / 2,5 / 5 / 10 fill
  it in one tap and show selected when the field holds their value.** The stepper already rounds
  to three decimals; a step finer than that would be lost at the first press.
- **The set sheet's weight number itself becomes the text field.** One control keeps the sheet
  as tall as it is; the design's 52 sp number is large enough to tap.
- **A typed weight is not snapped to the step, and is rounded to three decimals.** Issue 6 is
  about weights the step cannot reach, such as a 22,5 on a machine stepping by 5.
- **A weight that is empty, not a number or negative disables "Сохранить подход"; − and + step
  from the last valid weight and replace the text.** The sheet never saves a value the user
  cannot see.
- **The machine list is the repository's `all(owner)`, by name, with the weight caption under
  each name.** An account has a few dozen machines; the picker already covers search.
- **No delete in the list.** Sets reference their machine, and soft-deleting a machine would
  need a rule for its history that the backlog does not ask for.
- **The machine form opened from the list returns to the list and hides "После сохранения
  тренажёр появится в этом визите."** It was not opened from a visit.
- **"Тренажёры" is the second home row, enabled with or without an account, with Phosphor's
  `Barbell` icon.** Machines exist on Android before any sign-in.

### 2.2 Part B

- **A visit is a row per account and calendar day; the screen is addressed by the day
  (`VisitRoute(day)`), not by a visit id.** With one visit per day the day names it, and a day
  with no visit yet still has a screen to record into.
- **A visit row is created with its first set; a day without sets has no visit.** An empty visit
  carries nothing and would only have to be normalized away.
- **`visit.day` is nullable on both sides, with no unique constraint.** Old clients push visits
  without it, and two devices creating the same day offline must both reach the server before
  normalization can resolve them.
- **The server keeps `visit.recorded_at` (`not null`) and `visit.ended_at`; 1.0.2 keeps writing
  both, `ended_at` equal to `recorded_at`.** A 1.0.1 client decodes `recorded_at` as non-null and
  treats a null `ended_at` as its running visit, so both stay meaningful to it until it upgrades.
  `recorded_at` is the moment the visit row was created for today, and local noon for another
  day, as in 1.0.1.
- **Normalization keeps, of the visits sharing a day, the one with the newest `(recorded_at,
  updated_at, id)` and soft-deletes the others with their sets.** The approved design merges
  nothing; the newest is what the user saw last.
- **Normalization runs once per account at start, and again after every sync pass and account
  switch; it is idempotent, so "once" needs no stored flag.** A second run finds nothing to write.
- **Moving a visit onto a day that has one asks "Заменить визит?"; cancelling keeps move mode.**
  The user can tap another day without starting over.
- **Moving still restamps the sets' `recorded_at` onto the new day.** Suggestions and the picker
  order sets by their own `recorded_at` and never join `visit` (tech spec §4.5).
- **`workout_set.position` is visit-wide, `not null default 0`; sets sort by `(position,
  recorded_at, id)`, machines by their first set.** Old rows and 1.0.1 pushes carry `0` and keep
  their recording order; a new set takes the visit's highest position plus one.
- **Ordering uses up/down arrows in a "Порядок" mode.** Arrows work the same on touch and
  mouse, and a test taps them by tag.
- **A pass is requested when the app goes to the background, besides the existing triggers.**
  Ending a visit was the "done" signal; leaving the app is the new one and batches a session.
- **The rest timer restarts on a saved set only in today's visit; person chips appear only
  there.** Nobody rests between sets typed in for last week.

### 2.3 Part C

- **Friends' rows are read online on both platforms through one `commonMain` implementation on
  the UI client, and never reach SQLite.** The functional spec shares data, it does not sync it;
  the pull stays `owned(owner)`, so widened policies cannot leak rows into the local database.
- **Row-level security, not the client, decides who reads whom: a security-definer
  `shares_group_with(other)` widens only the `select` policies of `machine`, `visit`,
  `workout_set` and `profile`.** Insert and update policies stay owner-only, so friends read and
  never write.
- **Membership changes only through security-definer functions: `create_group`, `join_group`
  (takes the code), `leave_group`.** A plain insert policy would let anyone add themselves to any
  group whose id they learned.
- **`group_member` carries `display_name`, filled from the joining user's `full_name` metadata by
  those functions.** No client writes `profile` rows, and `auth.users` is readable only with
  definer rights.
- **An invite code is 8 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, drawn from
  `extensions.gen_random_bytes`.** No 0/O or 1/I to mistype; 32⁸ codes are too many to guess.
- **Every member sees the code; only the owner renames or deletes the group, and the owner
  cannot leave it.** Friends invite friends; a group without its owner would have nobody left to
  delete it.
- **The invite link is the web app's own address with `?join=CODE`. The page stores the code in
  `localStorage` before sign-in and removes it from the address.** Sign-in returns to the page
  address without its query (tech spec §5.4), so the code has to survive the Google round trip
  elsewhere.
- **Android builds the link from a `WEB_APP_URL` build setting and shares the code alone when it
  is unset; Android has "Вступить по коду".** Android has no page address of its own, and a fresh
  clone must still build.
- **A link key is `coalesce(link_id, id)`; picking a friend's machine clones it with `link_id`
  set to the friend machine's key.** The first machine of a physical machine needs no write when
  a second joins it.
- **Breaking a link sets one's own `link_id` to a fresh uuid, from a ⋮ menu in the machine form,
  after a confirmation, and writes at once.** It detaches only that machine; the others keep
  their shared key. The confirmation is the deliberate step, so the form's save is not a second
  one.
- **The ⋮ menu shows whenever an account is signed in.** A machine can be a link root without
  its owner knowing, and breaking an unlinked machine's link is harmless.

---

## 3. Part A — machines

### 3.1 Machine form

Top to bottom, changes only:

- **"Как считается вес"**: two choices, "Всего" (`mode-total`) and "На сторону"
  (`mode-per-side`).
- **Unit**: "кг" (`unit-kg`), "lb" (`unit-lb`), "Своя единица" (`unit-custom`, enabled, weight
  2). With "Своя единица" chosen, a field follows: label "Название единицы", tag `unit-label`,
  single line, at most 12 characters. The platform weight field's unit shows the typed name, or
  "ед." while it is blank.
- **"Шаг веса"**: the label, then a decimal field (tag `weight-step`, decimal keyboard) with the
  unit after it, like the platform weight; under them the chips "1", "2,5", "5", "10"
  (`step-1`, `step-2.5`, `step-5`, `step-10`). A chip writes its value into the field.
- The hint "После сохранения тренажёр появится в этом визите." (`machine-visit-hint`) shows only
  when the form was opened from a visit.

"Сохранить тренажёр" is enabled when the name is not blank, the platform weight is empty or a
number `≥ 0`, the step is a number `> 0` after rounding, and a custom unit has a non-blank
label. Numbers accept a comma or a point.

### 3.2 Set sheet

The weight stepper's number is a text field (`weight-value`, decimal keyboard). Typing "22,5" or
"22.5" sets the weight; the caption under it is unchanged ("плитка всего · ±1"). "Сохранить
подход" (`save-set`) is disabled while the text is empty, not a number, or negative. − and +
step from the current valid weight by the machine's step and show the result formatted, with a
comma. Choosing another machine, editing a set, saving and collapsing out of an edit reseed the
field as today.

### 3.3 Units in text

A custom unit's name replaces "кг"/"lb" everywhere a weight is written: the stepper caption,
the platform suffix "(+2 плитка)", set rows "7 плитка × 10", group summaries "3 × 7 плитка", the
picker's "Было 7 плитка × 10 · вчера" and the home card's last set. No declension.

### 3.4 Machine list

- Home: a row "Тренажёры" (`section-machines`, `Barbell` icon) after "Визиты", always enabled.
- Screen title "Тренажёры". One row per machine of the active account (`machine-list-row-<id>`):
  thumbnail, the name, the weight caption ("кг на сторону · ±5") under it.
- No machines: "Тренажёров пока нет" (`machine-list-empty`).
- An accent button "Новый тренажёр" (`new-machine`, Plus icon) at the bottom.
- Tapping a row opens the machine form on it; the button opens an empty form. Saving returns to
  the list, which reloads. Back returns home.
- Like every screen, the list follows the active account and reloads after a sync pass.

### 3.5 Domain and data

- `WeightMode { Total, PerSide }`; `WeightUnit { Kg, Lb, Custom }`.
- `Machine` gains `unitLabel: String` after `unit`; `Machine.new` sets `""`.
- `roundWeight(weight)` in `Stepping.kt`, used by `stepWeight`, the typed weight and the step.
- Wire names: `total`, `per_side`; `kg`, `lb`, `custom`. `weightModeOf` falls back to `Total`,
  `weightUnitOf` to `Kg`.
- `MachineRow` gains `@SerialName("unit_label") val unitLabel: String` with **no default
  value**: supabase-kt encodes with `encodeDefaults = false`, and a column missing from an upsert
  keeps its old value on the server.

Local schema (`Machine.sq`), the column appended **last**, after `deleted`, because `ALTER TABLE
… ADD COLUMN` appends and the generated `SELECT *` mappers read columns by position:

```sql
    deleted           INTEGER AS Boolean NOT NULL DEFAULT 0,
    unit_label        TEXT    NOT NULL DEFAULT ''
```

`2.sqm` (schema version 3):

```sql
ALTER TABLE machine ADD COLUMN unit_label TEXT NOT NULL DEFAULT '';
UPDATE machine SET weight_mode = 'total' WHERE weight_mode = 'counterweight';
```

`supabase/migrations/0005_machine_units.sql`:

```sql
alter table public.machine add column unit_label text not null default '';

alter table public.machine drop constraint machine_unit_check;
alter table public.machine add constraint machine_unit_check
    check (unit in ('kg', 'lb', 'custom'));

-- Clients before 1.0.2 may still write counterweight; the app reads it as total.
update public.machine
set weight_mode = 'total', updated_at = now()
where weight_mode = 'counterweight';
```

The table-level grants of `0003_grants.sql` cover the new column.

### 3.6 Sync and upgrade

- The pull and push carry `unit_label` through `MachineRow`, shared by the web repositories and
  the sync gateway; nothing else in the sync pass changes.
- A 1.0.1 client ignores `unit_label` (supabase-kt decodes with `ignoreUnknownKeys`), but its
  `weightUnitOf("custom")` throws: once any device of an account saves a custom unit, that
  account's 1.0.1 devices stop pulling. The release notes ask to update every device.
- **Order of rollout: every migration of this release is applied with `supabase db push` before
  `master` is pushed, because the web deploys on every push to `master`; `master` goes first,
  the `vX.Y.Z` tag last.** A 1.0.2 client against a server without `unit_label` fails to decode
  every machine.

---

## 4. Part B — visits

### 4.1 Home

The visit card is always shown, titled "СЕГОДНЯ" (11 sp, 0.09 em, `colors.secondary`):

- with sets today: "2 тренажёра · 5 подходов", the last set, and "Продолжить" (ArrowRight);
- without: "Подходов пока нет" and "Записать подход" (Plus).

The button (`open-today`) opens `VisitRoute(today)`. `start-visit` and `continue-visit` go.

### 4.2 Visit screen

- Title "Сегодня" for today; "Визит · 12 ноября" for any other day, with the year when it is not
  the current one.
- The header row shows the set count and, on the right, "Порядок" (`reorder-toggle`), which
  reads "Готово" while ordering. "Завершить визит" goes.
- The first saved set of a day creates its visit (§4.6). The person chips and the rest timer
  behave as for the running visit of 1.0.1 only when the day is today.
- Switching account in the chips records into the other account's visit of the same day.

### 4.3 Ordering

- Machines are listed in the order of their first set; sets within a machine by `(position,
  recorded_at, id)`.
- "Порядок" collapses the sheet, expands every group and shows arrows: on each machine header
  (`machine-up-<id>`, `machine-down-<id>`) and each set row (`set-up-<id>`, `set-down-<id>`),
  drawn with `CaretRight` rotated. The first item's up and the last item's down are disabled.
  Tapping a set does not open it while ordering.
- Moving a machine renumbers every set of the visit 1…n: machines in the new order, sets inside
  each in their current order. Moving a set swaps its position with its neighbour on the same
  machine; if any two of the visit's sets share a position — old rows, or a reorder written
  halfway — the visit is renumbered first.
- A new set takes the visit's highest position plus one.
- Every reorder writes only the sets whose position changed, requests a sync pass when the
  day is not today, and keeps "Порядок" on.

### 4.4 Calendar

- A day holds at most one visit card: its counts and machines, "Перенести" and "Удалить".
- A day without a visit, today included, shows "Нет визита" and "Добавить визит", which opens
  that day's visit screen.
- Moving: "Перенести", then tap a day. An empty day receives the visit. A day with a visit opens
  the dialog "Заменить визит?" with "На 12 ноября уже есть визит: 3 подхода. Он и его подходы
  пропадут из истории и статистики." and "Заменить" (`confirm-replace`) / "Отмена"
  (`cancel-replace`). Replacing soft-deletes that visit and its sets, then moves. Cancel closes
  the dialog and stays in move mode. Tapping the visit's own day leaves move mode.
- Removing is unchanged.

### 4.5 Normalization

`normalizedVisits(visits, sets, utcOffset, now): List<VisitRows>` in `domain/gym`, pure:

1. A live visit without `day` gets `CalendarDay.of(recordedAt, utcOffset)`.
2. Live visits are grouped by day; each group keeps its newest by `(recordedAt, updatedAt, id)`
   and every other visit becomes `removedVisit(visit, itsSets, now)`.
3. Only rows that changed are returned; a second run returns nothing.

`VisitNormalizer` in `core/data/gym` (`commonMain`) reads the owner's visits and their sets
through the repositories, applies the result sets first, visit last, and reports whether it
wrote. It runs:

- **Android**: at start, only for the anonymous owner; a signed-in account is normalized only
  inside `SyncWorker`, once that account's own pass comes back clean, and the worker runs one
  more pass inline — never a new `request()` — when normalization wrote something. Switching the
  active account requests a pass instead of normalizing directly.
- **Web**: after the session restore and after every account switch, against the server.

A failure normalizing one owner at start or on a switch is logged (Android) or sent to the
console (web) and does not stop the next owner's turn. Until an account's visits are normalized,
readers show a visit with no `day` on the day of its recorded instant (`Visit.dayAt`), newest by
`visitRecency` when several fall on it (`VisitRepository.shownOn`).

### 4.6 Domain and data

- `Visit(id, userId, day: CalendarDay?, recordedAt, updatedAt, deleted)`: `endedAt` leaves the
  domain. `day` is null only on rows normalization has not reached; readers skip them.
- `CalendarDay` gains `iso` ("2023-11-14") and `parse(iso)`; `isoDate` in `Formats.kt` uses it.
- `WorkoutSet` gains `position: Int` after `reps`.
- `VisitRepository`: `onDay(owner, day): Visit?`; `active` goes.
- `recordingInstant(visit, visitSets, today, now)`: `now` in today's visit; otherwise one second
  after the visit's last set, or after its `recordedAt`.
- `VisitRow` gains `day: String?` and keeps `recorded_at` and `ended_at`, writing `ended_at =
  recorded_at`. `WorkoutSetRow` gains `position: Int`. Neither has a default value (§3.5).
- `VisitRoute(day: String)` and `MachinePickerRoute(day: String, selectedMachineId)` replace the
  visit-id routes; the calendar opens a day, not a visit id.

Local, appended last in `Visit.sq` and `WorkoutSet.sq`:

```sql
    day         TEXT AS CalendarDay
    position    INTEGER NOT NULL DEFAULT 0
```

`3.sqm` (schema version 4):

```sql
ALTER TABLE visit ADD COLUMN day TEXT;
ALTER TABLE workout_set ADD COLUMN position INTEGER NOT NULL DEFAULT 0;
CREATE INDEX visit_day_idx ON visit (user_id, day);
```

`supabase/migrations/0006_visit_day.sql`:

```sql
alter table public.visit add column day date;
create index visit_user_id_day_idx on public.visit (user_id, day);

alter table public.workout_set add column position integer not null default 0;
```

### 4.7 Sync

- A pass is also requested on `ON_STOP` of the process lifecycle; "after the user ends a visit"
  goes. Writes to a day other than today and every calendar write keep requesting one. On the
  web, where a pass is only the screens re-reading what a write already sent, requesting one
  reloads them.
- A pulled visit without `day` is written as pulled; the normalization after the pass fills it
  and the push that follows carries it to the server.
- 1.0.1 sees every 1.0.2 visit as ended, on its `recorded_at` day. A 1.0.1 device still writes
  running visits; 1.0.2 normalizes them like any other.

---

## 5. Part C — groups and linked machines

### 5.1 Friends

Home's "Друзья" (`section-friends`) opens once an account is signed in; it stays locked without
one, as today.

- **Groups screen** ("Друзья"): one row per live group the account belongs to, with its name and
  "3 участника"; "Создать группу" (`create-group`) and "Вступить по коду" (`join-by-code`).
  No groups: "Групп пока нет".
- **Creating**: a dialog "Новая группа", a name field (1–40 characters), "Создать" / "Отмена".
  It opens the new group.
- **Joining by code**: a dialog "Вступить в группу", a code field (8 characters, upper-cased as
  typed), "Вступить" / "Отмена". An unknown code: "Приглашение не найдено".
- **Group screen** (the group's name): members by name, the owner marked "владелец"; "Пригласить"
  (`invite`); the owner gets "Удалить группу", everyone else "Выйти из группы", each after a
  dialog: "Удалить группу?" — "Участники перестанут видеть визиты друг друга." / "Выйти из
  группы?" — "Вы перестанете видеть визиты участников, а они — ваши."
- **Inviting**: Android opens the share sheet with "Вступай в группу «<name>» в Качалочке:
  <link>" and "Код: <code>" (the link line only with `WEB_APP_URL` set); the web copies the link
  and shows "Ссылка скопирована".
- **A member** opens a read-only calendar titled with their name, in the same month grid, with no
  add, move or remove; a day opens their visit read-only: groups and sets, no sheet, no
  "Порядок", no "Новый тренажёр".
- Every friends screen reads online. On failure: "Нет связи с сервером" and "Повторить".

### 5.2 The invite link

- The link is `inviteLink(pageAddress, code) = signInReturnAddress(pageAddress) + "?join=" +
  code`: the page's own address, which GitHub Pages serves under the repository path.
- `Main.kt`, before the session restore: `joinCodeOf(window.location.href)` reads the `join`
  parameter (8 characters of the code alphabet, upper-cased) into `JoinCodeStore`
  (`localStorage`, key `kachalochka.joinCode`), then `history.replaceState` puts
  `withoutJoinCode(href)` back, which removes only `join`: supabase-kt's own `code` parameter,
  if any, must still be there when the Auth plugin starts.
- With no account the sign-in screen shows; Google returns to the plain page address, and the
  code waits in storage.
- Once an account is active, `App` hands a stored code to `join_group`; success opens the group
  and clears the code, "unknown code" shows "Приглашение не найдено" and clears it, a network
  failure keeps it for the next start.
- The sign-in redirect allowlist is untouched: the return address never carries `join`.
- Android: `inviteLink(WEB_APP_URL, code)`. `WEB_APP_URL` comes from `local.properties` or the
  environment like the Supabase settings, and on CI from a repository variable in `ci.yml` and
  `release.yml`.

### 5.3 Linked machines

- `Machine` gains `linkId: MachineId?`; `Machine.linkKey = linkId ?: id`.
- **Picker**: under the own machines, a section "Тренажёры друзей": group-mates' live machines
  whose key matches none of the own keys, each with "у Миши" (`friend-machine-<id>`). Picking
  one saves `linkedCopy(friend, owner, now)` — the friend's settings, a new id, `linkId =
  friend.linkKey` — and returns it to the visit like any pick. Offline, the section is absent.
- **Set sheet**: under the previous-visit line, "Друзья:" and, for up to three friends with sets
  on a machine sharing the key, "Миша · вчера · 80×8, 85×6" from their latest visit on it
  (`sheet-friends`). Absent offline or when there are none.
- **A friend's visit**: their sets on a machine whose key matches one of the viewer's machines
  are grouped under the viewer's machine name; others under the friend's name for it.
- **Breaking**: the machine form's top bar gets a ⋮ (`machine-menu`, Phosphor
  `DotsThreeVertical`) with "Отвязать от друзей" (`unlink-machine`); the dialog "Отвязать
  тренажёр?" — "Результаты друзей на этом тренажёре перестанут показываться у вас." — "Отвязать"
  / "Отмена". It writes `linkId = MachineId.random()` at once.

### 5.4 Domain and data

- `domain/friends`: `GroupId`, `FriendGroup(id, name, ownerId, inviteCode, memberCount)`,
  `GroupMember(userId, displayName, isOwner)`, `FriendResult(member, sets)`, and
  `FriendsRepository`: `groups()`, `create(name)`, `join(code)`, `leave(group)`,
  `delete(group)`, `members(group)`, `visits(member)`, `sets(visit)`, `machines(member)`,
  `groupMachines()`, `latestOn(linkKey)`.
- One implementation, `SupabaseFriendsRepository` in `core/src/commonMain/.../data/friends`, on
  the UI `SupabaseClient`, bound in `coreModule` for both platforms. It is the exception to "two
  implementations per repository" (tech spec §3), because both platforms read friends online.
- `MachineRow` gains `@SerialName("link_id") val linkId: String?`, no default.
- Local: `link_id TEXT` appended last in `Machine.sq`; `4.sqm` (schema version 5):

```sql
ALTER TABLE machine ADD COLUMN link_id TEXT;
```

`supabase/migrations/0007_groups.sql`:

```sql
alter table public.machine add column link_id uuid;
create index machine_link_id_idx on public.machine (link_id);

create function public.new_invite_code() returns text
language sql volatile set search_path = '' as $$
    select string_agg(
        substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + get_byte(bytes, i) % 32, 1), ''
    )
    from extensions.gen_random_bytes(8) as bytes, generate_series(0, 7) as i;
$$;

create table public.friend_group (
    id          uuid        primary key default gen_random_uuid(),
    name        text        not null check (length(trim(name)) between 1 and 40),
    owner_id    uuid        not null references auth.users (id) on delete cascade,
    invite_code text        not null unique default public.new_invite_code(),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted     boolean     not null default false
);

create table public.group_member (
    group_id     uuid        not null references public.friend_group (id) on delete cascade,
    user_id      uuid        not null references auth.users (id) on delete cascade,
    display_name text        not null,
    joined_at    timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted      boolean     not null default false,
    primary key (group_id, user_id)
);
create index group_member_user_id_idx on public.group_member (user_id);

create function public.is_group_member(target uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1
        from public.group_member m
        join public.friend_group g on g.id = m.group_id
        where m.group_id = target and m.user_id = (select auth.uid())
          and not m.deleted and not g.deleted
    );
$$;

create function public.shares_group_with(other uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1
        from public.group_member mine
        join public.group_member theirs on theirs.group_id = mine.group_id
        join public.friend_group g on g.id = mine.group_id
        where mine.user_id = (select auth.uid()) and theirs.user_id = other
          and not mine.deleted and not theirs.deleted and not g.deleted
    );
$$;

create function public.my_display_name() returns text
language sql stable security definer set search_path = '' as $$
    select coalesce(nullif(trim(raw_user_meta_data ->> 'full_name'), ''), 'Участник')
    from auth.users where id = (select auth.uid());
$$;

create function public.create_group(group_name text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    created uuid;
begin
    insert into public.friend_group (name, owner_id)
    values (trim(group_name), (select auth.uid()))
    returning id into created;
    insert into public.group_member (group_id, user_id, display_name)
    values (created, (select auth.uid()), public.my_display_name());
    return created;
end;
$$;

create function public.join_group(code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    target uuid;
begin
    select id into target from public.friend_group
    where invite_code = upper(trim(code)) and not deleted;
    if target is null then
        raise exception 'unknown invite code' using errcode = 'P0002';
    end if;
    insert into public.group_member (group_id, user_id, display_name)
    values (target, (select auth.uid()), public.my_display_name())
    on conflict (group_id, user_id) do update
        set deleted = false, updated_at = now(), display_name = excluded.display_name;
    return target;
end;
$$;

create function public.leave_group(target uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if exists (select 1 from public.friend_group
               where id = target and owner_id = (select auth.uid())) then
        raise exception 'the owner deletes the group instead' using errcode = 'P0001';
    end if;
    update public.group_member set deleted = true, updated_at = now()
    where group_id = target and user_id = (select auth.uid());
end;
$$;

alter table public.friend_group enable row level security;
alter table public.group_member enable row level security;

create policy friend_group_select_member on public.friend_group
    for select using (owner_id = (select auth.uid()) or public.is_group_member(id));
create policy friend_group_update_owner on public.friend_group
    for update using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()));
create policy group_member_select_member on public.group_member
    for select using (public.is_group_member(group_id));

grant select on public.friend_group to authenticated;
grant update (name, deleted, updated_at) on public.friend_group to authenticated;
grant select on public.group_member to authenticated;

drop policy machine_select_own on public.machine;
create policy machine_select_own_or_group on public.machine
    for select using ((select auth.uid()) = user_id or public.shares_group_with(user_id));
drop policy visit_select_own on public.visit;
create policy visit_select_own_or_group on public.visit
    for select using ((select auth.uid()) = user_id or public.shares_group_with(user_id));
drop policy workout_set_select_own on public.workout_set;
create policy workout_set_select_own_or_group on public.workout_set
    for select using ((select auth.uid()) = user_id or public.shares_group_with(user_id));
drop policy profile_select_own on public.profile;
create policy profile_select_own_or_group on public.profile
    for select using ((select auth.uid()) = user_id or public.shares_group_with(user_id));

revoke execute on function
    public.new_invite_code(), public.is_group_member(uuid), public.shares_group_with(uuid),
    public.my_display_name(), public.create_group(text), public.join_group(text),
    public.leave_group(uuid)
    from public, anon;
grant execute on function
    public.is_group_member(uuid), public.shares_group_with(uuid), public.create_group(text),
    public.join_group(text), public.leave_group(uuid)
    to authenticated;
```

`new_invite_code` runs as the column default inside `create_group`, a definer function, so
`authenticated` needs no grant on it.

### 5.5 Sync

- `link_id` travels in `MachineRow` like any column. A clone is an own machine: it is stored
  locally on Android, enqueued and pushed.
- The pull keeps its `owned(owner)` filter. Nothing a friend owns enters SQLite or the outbox.
- A friend's rows reach the screen only through `FriendsRepository`, filtered on `user_id =
  member` and `deleted = false`.

---

## 6. Tests

- **Pure functions** — units and modes on the wire, `roundWeight`, `normalizedVisits`, ordering,
  `linkedCopy`, `inviteLink` / `joinCodeOf` / `withoutJoinCode`: `core` and `app` host tests.
- **Local migrations** — one test per `.sqm` in `SchemaMigrationTest`: create the current schema,
  rebuild the changed tables as the previous version declared them, insert old rows, migrate one
  step, and read the columns back with raw SQL. Raw SQL keeps each test valid after later parts
  append columns. No test asserts `Schema.version`, which every later migration would break.
- **Wire compatibility** — `SupabaseSyncGatewayTest` on the Ktor mock engine: a pulled row with an
  unknown column still decodes; a pushed row carries every new column even at its default value.
- **Repositories** — `Local*RepositoryTest` on in-memory SQLite; the wasm sources compile with
  `:core:compileKotlinWasmJs`, which the gate does not run.
- **Screens and view models** — `FakeGym`, `runScreenTest`, `AppTest`; Part C adds a
  `FakeFriends` repository bound in `fakeGymModule`.
- **SQL of Part C** — not host-testable. The plan ends with a manual check against the linked
  project after `supabase db push`: two accounts, create, join by link and by code, read each
  other's calendar, fail to update the other's machine, leave, delete.

---

## 7. Spec text to apply

Each part's plan applies its own subsection in its last task. Set **Last reviewed** in
`docs/technical_spec.md` to the date of that task.

### 7.1 Part A

#### `docs/functional_spec.md`

In the home-screen paragraph, replace "Below the card are rows for visits, plans, statistics and
friends;" with:

> Below the card are rows for visits, machines, plans, statistics and friends;

In the set-sheet paragraph, after "Saving records the set.", insert:

> The weight can also be typed, with a comma or a point; − and + step it by the machine's weight
> step.

Replace the machine-form paragraph with:

> The machine form collects a name, a setup note, how the weight is counted (total or per side),
> the platform weight and whether it is added to the recorded weight, the unit and the weight
> step. The unit is kg, lb or an own unit such as "плитка", whose name is then written after
> every weight on that machine, with no conversion. The weight step is any positive number, with
> 1, 2,5, 5 and 10 one tap away.
>
> The "Тренажёры" row lists the active account's machines by name, each with its unit, how its
> weight is counted and its step. Tapping one opens it in the machine form; "Новый тренажёр"
> adds one. Saving returns to the list.

In "Photos, comments on a set, a custom unit and counting left and right separately are shown on
these screens but not yet available.", delete "a custom unit, ".

#### `docs/technical_spec.md`

§4.5, replace the sentences from "Weights are `Double`; …" to "… used by both implementations."
with:

> Weights are `Double`; every step and every typed weight is rounded to three decimals so a
> running total never drifts. The weight-counting mode and the unit are enums, mapped to the
> wire names `total` / `per_side` and `kg` / `lb` / `custom` by one shared mapping in
> `core/data/gym`, used by both implementations. A name the mapping does not know reads as
> `total` or `kg`: clients before 1.0.2 still write `counterweight`, and one unreadable row must
> not stop a pull. A custom unit's name is `unit_label`, empty for kg and lb.

§5.1, append:

> A column a `.sqm` adds with `ALTER TABLE … ADD COLUMN` lands at the end of the table, so the
> `.sq` declares it last too: the generated `SELECT *` mappers read columns by position. A wire
> row declares no Kotlin default values, because supabase-kt encodes without defaults and an
> upsert leaves a column it was not sent unchanged. Old clients keep working against a newer
> schema only as far as their decoders allow: unknown columns are ignored, but a value they
> cannot map, such as the unit `custom`, fails their pull.

### 7.2 Part B

#### `docs/functional_spec.md`

Replace the home-screen paragraph's first three sentences ("The home screen shows a visit
card. … which starts one.") with:

> The home screen shows today's card: how many machines and sets were recorded today and the
> last set, or "Подходов пока нет", and a button opening today's visit. A visit is one calendar
> day; it has no start or end and comes into being with its first set.

Replace the visit-screen paragraph with:

> The visit screen lists the day's sets grouped by machine, machines in the order of their first
> set, and ends with "Новый тренажёр", which opens the machine picker. Tapping a machine's row
> expands it to show its sets; tapping a set opens it for editing or deletion. "Порядок" shows
> arrows that move a machine or a set up or down. Today's visit is titled "Сегодня", any other
> day's with its date.

In the calendar paragraph, replace everything from "Below the month are the chosen day's
visits" to the end with:

> Below the month is the chosen day's visit, with its machines and set count. Tapping it opens
> it on the visit screen, where its sets are added, edited, deleted and ordered as today's are.
> A day without a visit offers "Добавить визит". "Перенести" moves a visit, with its sets, to the
> day tapped next; if that day already has a visit, the app asks whether to replace it, and
> replacing removes it with its sets. "Удалить" asks for confirmation, then removes the visit
> and its sets from the history and the statistics.

#### `docs/technical_spec.md`

§4.2, **Triggers**: replace "when the user ends a visit" with "when the app goes to the
background", and "after any write to an ended visit" with "after any write to a visit of a day
other than today, and after visit normalization wrote something".

§4.5, replace the two paragraphs starting "A visit is active while it has no end;" and "A visit
added for a past day is ended when it is created," with:

> A visit is one account's calendar day: `visit.day`, with no start, end or duration, and the
> row is created with the day's first set. `day` is nullable and unconstrained, because clients
> before 1.0.2 push visits without it and two devices may create the same day offline. A
> normalization in `core/data/gym` fills a missing `day` from `recorded_at` at the device's
> offset and, where a day holds several visits, keeps the newest by `(recorded_at, updated_at,
> id)` and soft-deletes the others with their sets. It is idempotent and runs at start, after
> every sync pass and after an account switch. `recorded_at` and `ended_at` stay, written equal,
> so a client before 1.0.2 reads every visit as ended on its day.
>
> Moving a visit to another day moves its sets, and removing a visit soft-deletes its sets:
> every reader of sets filters on the set's own `deleted` and `recorded_at` and never joins
> `visit`. Such a rewrite writes the sets first and the visit last, and places each set by its
> clock time after the visit's, so a retry after a half-finished write on the web writes the
> same rows. A set added to a visit of another day is stamped one second after the visit's last
> set, which keeps a late correction on the visit's day and in order. Within a visit, sets sort
> by `(position, recorded_at, id)`; `position` defaults to 0, so rows written before it keep
> their recording order.

### 7.3 Part C

#### `docs/functional_spec.md`

Home-screen paragraph: replace "plans, statistics and friends are shown but not yet available."
with "plans and statistics are shown but not yet available."

Replace the section "## Group sharing" with:

> ## Group sharing
>
> Signed-in users form groups. The creator owns the group and invites others with a link to the
> web app or with its eight-character code; the Android app joins by code. Every member sees the
> other members' calendars and visits, read-only, and nothing is shared outside a group. A member
> can leave; the owner deletes the group instead. Friends' data is read online and never stored
> on the device.
>
> A machine can be taken from a friend's list: the copy keeps the friend's settings and stays
> linked to the friend's machine as one physical machine. The set sheet then shows friends'
> latest results on it, and a friend's visit shows their sets on it under the user's own machine
> name. A link can be broken from the machine form.
>
> A newsfeed of ended visits is not built yet.

#### `docs/technical_spec.md`

§3, append:

> Friends' data is the exception: both platforms read it online, so `FriendsRepository` has one
> implementation in `commonMain`, on the UI client, and nothing it returns is written to SQLite.

§5.2, replace the three bullets with:

> - A user writes only rows with their own `user_id`.
> - A user reads their own rows and the `machine`, `visit`, `workout_set` and `profile` rows of
>   everyone who shares a live group with them, through the security-definer function
>   `shares_group_with`.
> - Group membership is readable by the group's members and changes only through the
>   security-definer functions `create_group`, `join_group` and `leave_group`; the owner renames
>   and soft-deletes the group directly.

§5.4, append:

> `WEB_APP_URL`, the web app's address, travels the same way. Android builds invite links from
> it and shares only the code without it; the web builds them from its own address.

---

## 8. Order of work

Part A — one plan:

1. Machine data: own unit label, counterweight read as total, both migrations.
2. Weights written with the machine's own unit.
3. The machine form's own unit.
4. The decimal weight step.
5. The typed weight in the set sheet.
6. The machine list.
7. The specs.

Part B — one plan: day visits and normalization in `domain`; `3.sqm` and `0006`; repositories
and routes by day; home and visit screen; calendar replace; ordering; sync triggers; specs.

Part C — one plan: `link_id`, `4.sqm` and `0007`; `FriendsRepository`; groups screens; the
invite link on both platforms; linked machines in the picker, the sheet and friends' visits;
breaking a link; specs; the manual check.

Parts are sequential: B's and C's migrations number after A's, and B changes the visit routes
C's friend screens reuse.

## 9. Risks

- **1.0.1 devices stop pulling** once an account has a custom unit (§3.6). The changelog must say
  to update every device.
- **Normalization soft-deletes** the earlier of two visits on one day, sets included, both for
  old multi-visit days and for a day two devices created offline. The rows stay in the database,
  flagged deleted.
- **A 1.0.1 device and a 1.0.2 device on one account** fight over running visits: 1.0.1 starts
  them, 1.0.2 normalizes them onto days and may delete the older one.
- **Every own-data query must keep `owned(owner)` or an id the owner holds** once Part C widens
  `select`: a web query filtering by anything else starts returning friends' rows. The calendar's
  `byId` callers take their ids from the owner's own `all`, so an id from elsewhere never reaches
  one.
- **`shares_group_with` runs per row** of every friend-visible table. Fine at a few hundred rows
  per member; an index on `group_member (user_id)` is its only help.
- **PostgREST's row cap (1000)** applies to a friend's `visits(member)` as to `all(owner)`.
- **Invite codes are not rate-limited**; 32⁸ codes keep guessing impractical, not impossible.
