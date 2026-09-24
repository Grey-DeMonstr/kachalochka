# Visit screen and visit calendar — design

**Date:** 2026-09-24

Three changes around the gym visit, from the issue backlog:

- **Issue 1.** The set sheet looks swipeable and must be: swiping it down, the phone's back and
  the top bar's back collapse it. Tapping the machine's name does nothing.
- **Issue 2.** The visit's machine list ends with "Новый тренажёр", so adding the next row is
  obvious.
- **Issue 4.** A calendar of every visit, where a visit is added, opened, moved to another day
  or removed.

The design project draws none of this, so every new element is built from the Nocturne
components already in `ui/components` and follows the frames' copy style.

---

## 1. Scope

Built:

- The set sheet gains a collapsed state: a bar naming the machine and the next set. Swipe down,
  system back and top-bar back collapse it; tapping or swiping up the bar expands it.
- The machine's name in the sheet stops being a button.
- "Новый тренажёр" at the end of the visit list; the empty "Выбрать тренажёр" sheet goes.
- A "Визиты" row on the home screen opening a month calendar of the active account's visits.
- Adding a visit on a past day, opening any visit on the visit screen, moving a visit with its
  sets to another day, removing a visit with its sets after a confirmation.

Not built: a year view, search, statistics on the calendar, editing the clock time of a visit.

---

## 2. Decisions

Each decision, then why.

- **The sheet stays an inline panel under the list, with its own drag handling
  (`Modifier.draggable`), not Material3's `ModalBottomSheet`.** A modal's scrim blocks the list,
  which must stay tappable for "Новый тренажёр" and the set rows; `draggable` takes touch and
  mouse drags alike on Android and Wasm.
- **Collapsing hides the sheet behind a bar and keeps the machine.** The next set is usually on
  the same machine, and the picker's "Скопировать тренажёр" needs a chosen machine; an accidental
  swipe costs one tap.
- **Back while editing a set leaves the edit and collapses.** The edit was opened from the list,
  so one back returns to the list, whatever the sheet shows.
- **Back handling stays on `NavigationBackHandler` from navigationevent-compose 1.1.0, enabled
  only while the sheet is expanded.** The visit screen already uses it in `commonMain`; the
  Android system back reaches it and the web uses the top-bar arrow.
- **The machine is changed only through "Новый тренажёр".** One entry point to the picker, and
  reading the headline no longer navigates away.
- **No sheet at all while no machine is chosen.** The list's button replaces "Выбрать
  тренажёр"; the sheet is only ever about one machine.
- **A custom month grid in `commonMain`, no new dependency.** Material3's `DatePicker` cannot
  mark the days that have visits, and the stack is fixed.
- **Moving a visit happens in the same grid ("Перенести", then tap a day), not in
  `DatePickerDialog`.** One calendar control, Russian copy whatever the platform locale, and day
  cells a test can tap by tag.
- **The grid selects one day and lists that day's visits below it.** The grid is the overview;
  the panel keeps the screen to one scroll.
- **A visit added on a past day is born ended: `recorded_at` = `ended_at` = local noon.** It can
  never become the active visit, and noon keeps the day under any offset change.
- **Adding on today starts the running visit, offered only while none runs.** Same as "Начать
  визит" at home; an account has at most one active visit.
- **A visit that has not ended cannot be moved.** The running visit is today's by definition,
  and an unended visit moved to today could take over as the active one. It can still be
  removed. The calendar shows every unended visit as running.
- **Removing a visit soft-deletes its sets too.** Every reader of sets — suggestions, the picker,
  statistics to come — filters on the set's own `deleted` and never joins `visit`; a join in
  every query, SQL and PostgREST, is where a leak would hide.
- **Moving a visit moves its sets; each keeps its clock time, placed after the visit's own
  time.** Suggestions and the picker order by the set's `recorded_at`, and placing by clock time
  makes a repeated move a no-op.
- **Rewrites go sets first, the visit last.** The web writes row by row; a retry after a
  half-finished write converges on the same rows.
- **A set added to an ended visit is stamped one second after the visit's last set.** It lands
  on the visit's day and in order, never on today.
- **The previous-visit sets are those before the visit on screen, and "N дней назад" counts from
  it.** A past visit's reference is the visit before it, not yesterday.
- **An ended visit hides "Завершить визит" and the person chips, and does not restart the rest
  timer.** Nobody is resting, and a chip switch would move the screen to the other account's
  running visit.
- **`VisitRepository.all(owner)` reads the whole history, not a month.** A few hundred rows a
  year; one read per screen open beats a round trip per arrow on the web.
- **Every calendar write, and every write to an ended visit, requests a sync pass.** No
  "Завершить визит" follows them to trigger one.
- **The removal confirmation is a Material3 `AlertDialog`, driven by view-model state.** It is
  themed from `MaterialTheme`, and the test drives it through state and by tag.
- **"Визиты" is the first home section row, enabled, with Phosphor's `CalendarBlank` icon.** The
  rows are where sections live, and it is the only one that exists.

---

## 3. The set sheet (issue 1)

Three states:

| State | Bottom of the screen | List |
|---|---|---|
| No machine chosen | nothing | full opacity |
| Expanded | the sheet as today | dimmed to 0.55, still tappable |
| Collapsed | a bar: handle, "Жим ногами · подход 3", an upward caret | full opacity |

- Choosing a machine (picker, form) or tapping a set expands the sheet.
- **Swipe down** on the expanded sheet: the sheet follows the finger downwards. Released past
  72 dp, or flung down faster than 800 dp/s, it collapses; otherwise it springs back.
- **Swipe up** on the bar, past 24 dp or flung up faster than 800 dp/s, expands it. Tapping the
  bar expands it too.
- **Back** — system or top bar — collapses an expanded sheet, leaving edit mode if it was in it.
  With the sheet collapsed or absent, back leaves the screen.
- Stepper values survive collapse and expand.
- The machine block (`sheet-machine`) is not clickable in any mode.

The view model gains `collapseSheet(): Boolean` (false when there was nothing to collapse, so
the top bar knows to leave) and `expandSheet()`. `leaveEdit()` goes; `collapseSheet()` covers it.
`SheetUi` gains `expanded`.

## 4. "Новый тренажёр" (issue 2)

The list ends, after the last machine group, with a full-width `OutlineButton` "Новый тренажёр"
with the Plus icon. It opens the picker with the chosen machine, as the sheet's name did, so
"Скопировать тренажёр" keeps working. It is shown in every sheet state and for ended visits.
It keeps the `pick-machine` test tag the old sheet button had.

---

## 5. The visit calendar (issue 4)

### 5.1 Reaching it

The home screen's first section row is "Визиты" (`section-visits`), enabled, opening
`CalendarRoute`. The screen's title is "Визиты"; back returns home.

### 5.2 The screen

- A month header: "Ноябрь 2023" with arrows either side. The forward arrow is absent on the
  current month — no visit can be in the future.
- Weekday labels "Пн … Вс", then Monday-first weeks. Each day cell shows its number; a day with a
  visit has an accent dot under it; today's number is in the secondary colour; the selected day
  has an accent ring. Future days are disabled at `DISABLED_ALPHA`.
- The screen opens on the current month with today selected.
- Under the grid: the selected day as "Вторник, 14 ноября", then one card per visit that day,
  in recording order. A card shows "ВИЗИТ ИДЁТ" for the running visit, "2 тренажёра · 3 подхода",
  the machines' names in recording order, and two buttons: "Перенести" (not on the running visit)
  and "Удалить". Tapping the card opens the visit.
- A day without visits shows "Нет визитов".
- The add button: "Добавить визит" on a past day; "Начать визит" on today while no visit runs;
  absent otherwise.
- The screen follows the active account like every other screen: a switch reloads it and drops
  a move in progress.

### 5.3 Adding

A past day gets an ended visit at local noon (§2); today gets a running visit at now. Either way
the visit screen opens on it.

### 5.4 Moving

"Перенести" enters move mode: a banner "Выберите новый день" with "Отмена" above the grid. The
next tap on an enabled day moves the visit and its sets there and selects that day. Back, system
or top bar, and "Отмена" leave move mode without writing.

A moved visit keeps its clock time and its length (`ended_at − recorded_at`). Each set is placed
at the visit's new `recorded_at` plus the set's clock time after the visit's clock time, modulo a
day — so sets past midnight stay after the others, and a repeated or retried move writes the same
rows.

### 5.5 Removing

"Удалить" opens the dialog "Удалить визит?" with "12 ноября · 3 подхода. Подходы пропадут из
истории и статистики." and "Удалить" / "Отмена". Confirming soft-deletes every set, then the
visit. Removing the running visit is allowed; home then offers "Начать визит" again.

### 5.6 Opening a visit

The calendar opens any visit on the existing visit screen (`VisitRoute`). For an ended visit:

- the title is "Визит · 12 ноября", with the year when it is not the current one;
- "Завершить визит" and the person chips are hidden;
- saving a set does not restart the rest timer, and requests a sync pass, as does saving or
  deleting an edited set;
- a new set is stamped one second after the visit's last set, or after the visit itself;
- the sheet's previous-visit line and suggestion come from the last visit before this one.

The running visit behaves as today.

---

## 6. Domain

In `core/domain/gym`, pure and host-tested:

```
CalendarDay(year, month, day)   epochDay, dayOfWeek (1 = Monday), plusDays,
                                at(millisOfDay, offset), of(instant, offset), ofEpochDay,
                                Comparable
CalendarMonth(year, month)      length, first(), plusMonths, weeks() — Monday-first,
                                null-padded, of(day)
millisOfDay(instant, offset)    beside minuteOfDay in LocalTime.kt

VisitRows(visit, sets)
pastVisit(day, owner, offset, now): Visit                 ended at local noon
movedVisit(visit, sets, day, offset, now): VisitRows      §5.4
removedVisit(visit, sets, now): VisitRows                 every row deleted
recordingInstant(visit, visitSets, now): Instant          §5.6
previousVisitSets(machineSets, currentVisit, before)      new optional bound
```

`kotlin.time` has no calendar and the stack adds no date library, so the civil-date arithmetic
is Howard Hinnant's days-from-civil algorithm.

## 7. Data

- `VisitRepository.all(owner): List<Visit>` — the owner's visits that are not deleted, newest
  `recorded_at` first. SQLDelight query `forOwner` in `Visit.sq`; PostgREST select with
  `deleted = false`, `owned(owner)` and descending order. A query addition, so no `.sqm`.
- No column changes, so `supabase/migrations/` is untouched.
- Every write goes through the existing `upsert`s, so Android enqueues each row for sync and the
  web writes it directly. A `VisitRows` is written sets first, visit last.
- `CalendarViewModel` calls `SyncTrigger.request()` after every add, move and removal;
  `VisitViewModel` after every write to an ended visit. On the web the trigger is a no-op.

## 8. Tests

- **Calendar arithmetic and visit rewrites:** `core` domain tests with `GymFixtures`.
- **`all(owner)`:** `LocalVisitRepositoryTest` on the in-memory SQLite; the wasm source is
  compiled with `:core:compileKotlinWasmJs`, which the gate does not run.
- **Sheet state, ended visits, the calendar's logic:** view-model tests with `FakeGym`.
- **Top-bar back:** `performClick()` on `top-bar-back`.
- **System back:** a `DirectNavigationEventInput` behind `LocalNavigationEventDispatcherOwner`,
  calling `backCompleted()`, as the visit screen's test does now.
- **Swipes:** `performTouchInput { swipeDown() }` on `set-sheet` and `swipeUp()` on
  `sheet-peek`; a slow 30 px drag proves the sheet springs back.
- **The dialog:** tags on its buttons; the test host finds dialog nodes as it finds the account
  menu's popup items.
- **Navigation:** `AppTest` through `runNavigationUiTest`.

---

## 9. Spec text to apply

### 9.1 `docs/functional_spec.md`

In "### Screens", replace the home-screen paragraph's sentence "Below the card are rows for
plans, statistics and friends; they are shown but not yet available." with:

> Below the card are rows for visits, plans, statistics and friends; plans, statistics and
> friends are shown but not yet available.

Replace the visit-screen paragraph with:

> The visit screen lists the visit's sets grouped by machine and ends with "Новый тренажёр",
> which opens the machine picker. Tapping a machine's row expands it to show its sets; tapping
> a set opens it for editing or deletion. "Завершить визит" ends the visit and returns to the
> home screen.

Append to the set-sheet paragraph ("Adding or editing a set uses the same sheet. …"):

> Swiping the sheet down, or pressing back on the phone or in the top bar, collapses it to a bar
> naming the machine and the next set; tapping the bar or swiping it up opens the sheet again.
> Pressing back while a set is being edited leaves the edit and collapses the sheet. The
> machine's name is not a button: another machine is chosen with "Новый тренажёр".

Add after the machine-form paragraph:

> The "Визиты" row opens a calendar of the active account's visits, a month at a time. Days with
> a visit are marked, today and the chosen day are highlighted, and future days cannot be chosen.
> Below the month are the chosen day's visits, each with its machines and set count. Tapping one
> opens it on the visit screen, where its sets are added, edited and deleted as in a running
> visit; a past visit's title carries its date and it has no "Завершить визит". "Добавить визит"
> records a visit on a past day; on today the button is "Начать визит", offered while no visit
> is running. "Перенести" moves a visit, with its sets, to the day tapped next; the running visit
> cannot be moved. "Удалить" asks for confirmation, then removes the visit and its sets from the
> history and the statistics.

### 9.2 `docs/technical_spec.md`

Set **Last reviewed** to 2026-09-24.

§4.2, replace the **Triggers** bullet's first sentence with:

> - **Triggers.** A pass runs on app start, on connectivity gain, after the user ends a visit,
>   after a visit is added, moved or removed on the calendar, and after any write to an ended
>   visit.

§4.5, add after the paragraph "A visit is active while it has no end; …":

> A visit added for a past day is ended when it is created, with `recorded_at` and `ended_at`
> both at local noon of that day, so it can never become the active visit; only a visit started
> now is running. Moving a visit to another day moves its sets, and removing a visit
> soft-deletes its sets: every reader of sets filters on the set's own `deleted` and
> `recorded_at` and never joins `visit`. Such a rewrite writes the sets first and the visit last,
> and places each set by its clock time after the visit's, so a retry after a half-finished write
> on the web writes the same rows. A set added to an ended visit is stamped one second after the
> visit's last set, which keeps a late correction on the visit's day and in order.

§11, append:

> Calendar dates are `CalendarDay` values in `domain/`, with epoch-day arithmetic, because
> `kotlin.time` has no calendar and the stack adds no date library. The calendar screen, adding a
> visit on a past day and moving one all go through them.

---

## 10. Order of work

1. The sheet collapses on back, into a bar that expands again.
2. Swipe gestures on the sheet and the bar.
3. "Новый тренажёр" in the list; the machine's name stops being a button.
4. `CalendarDay` and `CalendarMonth`.
5. Visit rewrites: past visits, moves, removals, recording instants.
6. `VisitRepository.all(owner)` on both implementations.
7. The visit screen for an ended visit.
8. `CalendarViewModel` and the date formats.
9. The calendar screen, its route and the home row.
10. The functional and technical specs.

Step 3 carries issue 1's "the name does nothing": until the list has its button, the name is the
only way to change machine.

## 11. Risks

- PostgREST caps a response at the project's maximum rows, 1000 by default; `all(owner)` reaches
  it after years of visits and would then need paging.
- Moving a visit across a daylight-saving change keeps the local clock time at today's offset,
  so a set can shift by an hour. Nothing reads visit durations, so nothing breaks.
- A web removal or move that fails half-way leaves the visit on screen with some sets rewritten;
  repeating the action converges.
