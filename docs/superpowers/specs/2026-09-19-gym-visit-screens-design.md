# Gym visit screens — design

**Date:** 2026-09-19

The first product feature: recording a regular gym visit on the screens drawn in the Claude
Design project "Gym visit screen options" (Nocturne design system). Five frames are in scope:

| Frame | Screen |
|---|---|
| 4a | Главная — the home screen and the top bar shared by every screen |
| 1b | Визит — the visit list behind the stepper sheet |
| 2c | Правка подхода — the same sheet editing a recorded set |
| 2a | Тренажёр — the machine picker |
| 2b | Тренажёр — the machine form, for a new, copied or existing machine |

Anything the frames do not draw stays unbuilt, and a control the frames draw for an unbuilt
feature is shown disabled.

---

## 1. Scope

Built:

- Machines with a name, a setup note, how weight is counted (total, per side, counterweight),
  a platform weight and whether it is added to the record, a unit (kg or lb) and a weight step
  (1, 2.5, 5 or 10).
- Visits: start, continue, end. One visit is active at a time: the newest one without an end.
- Sets of weight × reps on a machine within a visit: add, edit, soft-delete.
- The rest timer in the top bar.
- Dark and light Nocturne colour schemes; the design only draws dark, light is derived from the
  same tonal ramps.

Shown disabled, built later: photos (the photo tile and thumbnails are placeholders),
comments on a set, the custom unit ("Своя единица"), counting left and right separately, and the
Планы / Статистика / Друзья rows on the home screen.

Not built: exercises measured in distance or time (the "Бег 1 км · 6:20" row of 1b), sign-in,
the sync pass.

---

## 2. Domain

In `core/domain/gym`, following technical spec §4.1 (client UUID v4 ids, owner, `updated_at`,
soft delete):

```
Machine     id, userId, name, setupNote, weightMode, platformWeight, platformIncluded,
            unit, weightStep, updatedAt, deleted
Visit       id, userId, startedAt, endedAt?, updatedAt, deleted
WorkoutSet  id, userId, visitId, machineId, weight, reps, recordedAt, updatedAt, deleted
```

`WorkoutSet` avoids `set`, a keyword in both SQL dialects. Weights are `Double`: every step is a
multiple of 0.5, which binary floating point holds exactly, and stepping rounds to three
decimals so a sum never drifts.

Repositories are suspend-only. `domain/` may not import kotlinx.coroutines, so there is no
`Flow`; a view model reloads after each write, which the web implementation would have to do
anyway.

`CurrentUser` answers who owns a new row. On Android it is null until sign-in exists, and
§4.3 of the technical spec stamps those rows later; on the web it is the Supabase session user,
and a write without one is refused by row-level security.

Pure functions, each with its own tests:

- **Suggested set.** For the n-th set on a machine in this visit: the n-th set of the previous
  visit on that machine, else the last set of this visit, else the last set of the previous
  visit, else the platform weight when it is added to the record (0 otherwise) × 10.
- **Previous visit sets** — the sets of the most recent other visit on a machine.
- **Stepping** — weight ± step, never below 0; reps ± 1, never below 1.
- **Picker ranking** — machines whose name contains the query, most recently used first; when
  none contains it, all machines in that order. "Создать" is offered for a non-blank query that
  no machine name equals, ignoring case.
- **Visit summary** — machine count, set count, last set.
- **Rest remaining** — 1:30 from the moment a set is saved or the timer chip is tapped.
- **Calendar days between** two instants at a UTC offset, and the local minute of the day, for
  "вчера" and "записано 19:52". `kotlin.time` has no time zones, so the offset comes from the
  platform: `java.util.TimeZone` on Android and the JVM, `Date.getTimezoneOffset` on the web.

## 3. Data

Three synced tables, each in SQLDelight and in `supabase/migrations/0002_gym.sql`:
`machine`, `visit`, `workout_set`. Row-level security matches the profile table: a user reads
and writes their own rows. Enum columns hold `total` / `per_side` / `counterweight` and
`kg` / `lb`, one mapping shared by both implementations.

Local repositories follow `LocalProfileRepository`: row and outbox entry in one transaction,
unowned rows stay out of the outbox. Remote repositories follow `RemoteProfileRepository`.

## 4. Screens

Russian copy exactly as drawn. The top bar is on every screen: back arrow (not on the home
screen), title, rest timer chip, settings gear (not on the settings screen). Tapping the timer
chip restarts the rest countdown; idle, it shows the full 1:30.

**Главная.** A visit card: while a visit runs, "Визит идёт", elapsed time, "N тренажёров ·
M подходов", the last set and "Продолжить"; otherwise "Начать визит", which starts one. Below,
the three section rows, not yet clickable.

**Визит.** Title "Визит · 42:10". The list: "N подходов", "Завершить визит" (ends the visit and
returns home), then one row per machine with its sets summarised — "2 × 45 кг" when all weights
are equal, "60, 70, 70 кг" otherwise. Tapping a machine row expands its sets; tapping a set opens
the sheet in edit mode (2c) with that row highlighted.

The sheet, in add mode (1b): machine name with "(+20 кг)" when a platform weight is not added
to the record, "подход N", the setup note, "Вчера · 70×10 · …" from the previous visit, the
timer chip, the weight and reps steppers ("кг всего · ±2,5"), "Сохранить подход", then
"Комментарий" (disabled) and "Настройки" (the machine form). Tapping the machine name opens the
picker; with no machine chosen yet the sheet shows only "Выбрать тренажёр".

In edit mode (2c): "подход N" in the accent, "Правка · записано 19:52, было 70 кг × 10",
the steppers seeded from the set, "Сохранить", "Комментарий" (disabled) and "Удалить подход".
The top bar's back arrow and the system back gesture leave edit mode before they leave the
screen.

**Тренажёр — picker.** A search field; "Создать «…»" opens the form with that name; "Похожие"
lists the ranked machines with "N подходов сегодня" or "Было 80 кг × 8 · 4 дня назад";
"Скопировать тренажёр" appears when the visit has a machine chosen and opens the form pre-filled
from it with the typed name. Choosing a machine returns to the visit with it selected.

**Тренажёр — form.** Everything but the name has a default, so "Сохранить тренажёр" is enabled
once the name is not blank. Saving returns to the visit with the machine selected.

## 5. Icons

Phosphor Regular, vendored as `ImageVector`s under `app/.../ui/icons` with the MIT licence
beside them. The multiplatform Phosphor library ships every weight of every icon, about 27 MB
per platform artifact, for the 19 icons the screens use.
