# Plans — design

A plan is a list of machines to do on some future visit, with no sets. The user builds it with
the machine picker the visit already uses. On the day, "Начать" adds the plan's machines to
today's visit, deletes the plan and opens the visit, where the user records sets as usual.

## 1. Decisions

- **Plans are the account's own and private.** They sync between the account's devices like its
  visits, on Android and the web; friends never read them, whatever groups they share.
- **A started plan lives on the visit.** A visit gains a planned list of machine ids. The plan row
  is soft-deleted on start, so moving or deleting the visit on the calendar carries its planned
  machines with it and nothing else has to follow.
- **A plan's name is optional.** A blank name shows as the plan's machine names, joined.
- **"Начать" always targets today.** It adds to today's visit, creating it when there is none.
- **A planned machine without sets is removable by hand.** Left alone, it stays on the visit.
  Everything that summarises a visit — the shared text, the calendar's visit card, the home card,
  a friend's view of the visit — counts only machines with sets.
- **Gone machines drop out.** Readers skip plan and planned machine ids that name no live machine
  of the account, so a machine removed by a merge drops out of plans and planned lists.
- **Two devices, one day.** If one device starts a plan offline and another records sets offline
  on the same day, normalization keeps the visit with sets and the planned list of the other is
  lost. This is accepted.

## 2. Data

### 2.1 `workout_plan`

A new synced table, owner-only like `measure`. It is not called `plan`, a keyword in SQLite,
as `workout_set` is not called `set`:

| Column | Type | Rule |
|---|---|---|
| `id`, `user_id`, `updated_at`, `deleted` | as every synced table | |
| `name` | text, not null, default `''` | trimmed, at most 40 characters |
| `machine_ids` | text, not null, default `'[]'` | JSON array of machine ids, distinct, in order |
| `created_at` | timestamptz, not null | set once on creation; the plans list sorts by it |

`machine_ids` is text holding JSON, as `machine.tags` and `profile.friend_colors` are; unreadable
text or entries read as no machines. `machine_ids` holds no foreign key: a plan may reach the
server before a machine it names.

Row-level security: one owner-only policy for all commands, no group policy. Grants: `select`,
`insert`, `update` to `authenticated`.

### 2.2 `visit.planned`

A new column on `visit`: text, not null, default `'[]'`, a JSON array of machine ids in the order
they were planned, read like `machine_ids`. Clients before this version ignore it when pulling,
and their upserts leave it unchanged, since a wire row declares no defaults.

### 2.3 Migrations

- Postgres `0018_plans.sql`: creates `workout_plan` with its indexes, policy and grants, and adds
  `visit.planned`.
- SQLDelight `13.sqm`: creates `workout_plan`, adds `visit.planned` (declared last in
  `Visit.sq`), and resets `lastPullAt`, so visits pulled before the update come down again with
  their column. `Schema.version` moves with it.

### 2.4 Domain and repositories

- `domain/gym/Plan.kt`: `PlanId` (a v4 UUID value class, as the other ids), and
  `Plan(id, userId, name, machineIds, createdAt, updatedAt, deleted)`.
- `Visit` gains `planned: List<MachineId>`.
- `PlanRepository` in `domain/gym`: `all(owner)` (live plans, oldest first), `byId`, `upsert`.
  Implementations: `LocalPlanRepository` (SQLDelight plus the outbox) and `RemotePlanRepository`
  (PostgREST), with one wire mapping in `core/data/gym` shared by both.
- Pure functions in `domain/gym`, each with its own tests:
  - `startedPlanned(planned, recorded, plan, live)`, all machine ids: the visit's new planned
    list — its current one followed by the plan's live machines that are neither recorded in the
    visit nor already planned, in plan order.
  - `plannedWithoutSets(planned, sets, live)`: the planned machines the visit screen shows
    as planned rows — live, and without a live set in the visit.
- `planTitle(name, machineNames)` in `app/ui/format`: the name, or the machine names joined with
  ", " when it is blank, or "Без названия" when both are empty.

### 2.5 Sync and account upkeep

- The outbox ranks `workout_plan` after `visit`; the server checks nothing a plan names, and it
  follows the machines it names as a link does.
- The pull fetches `workout_plan` with every other table.
- The first sign-in claims ownerless plans (`SqlOwnerlessRows`); deleting an account removes its
  plans on the server by cascade and on the device through `SqlOwnedRowsPurge`.

## 3. Screens and flow

### 3.1 Home

The "Планы" row becomes available and opens the plans list.

### 3.2 Plans list ("Планы")

One row per live plan, oldest first: the plan's title (§2.4), under it the machine count, and at
the right a "Начать" button. Tapping the row opens the plan form. The list ends with "Новый план",
which opens an empty plan form. With no plans, a line says "Планов пока нет" above it.

"Начать":

1. Finds today's visit of the active account, or creates it. `SetRecorder.record` does that
   lookup inline today; it moves into a `SetRecorder` function both call.
2. Writes the visit with `startedPlanned` as its planned list.
3. Soft-deletes the plan.
4. Opens today's visit, replacing the plans list on the back stack, so back returns home.

A plan whose machines are all gone, or already in the visit, still starts: it is deleted and the
visit opens.

### 3.3 Plan form ("План")

- A "Название" field, up to 40 characters, "Без названия" as its placeholder.
- The plan's machines in order, each row as in the machine list: cover photo, name, and a remove
  button at the right.
- An order button in the top bar, drawn and behaving like the visit's: it shows drag handles that
  reorder the machines, reusing the `Reorder` component.
- "Добавить упражнение" opens the machine picker in plan mode (§3.4). The machine it returns is
  appended; one already in the plan is not added twice.
- "Сохранить план" writes the name and machines and returns to the list. Leaving without saving
  drops the edits, as the machine form does. A plan with no machines cannot be saved.
- The top-bar menu of a saved plan has "Удалить план", which asks "Удалить план?" and soft-deletes
  it.

The form follows the active account: a switch returns to the plans list.

### 3.4 Machine picker in plan mode

The picker is reused as it is, with `MachinePickerRoute.day` made optional: null means plan mode.
In plan mode rows show only the last result, never "сегодня" or "в этом визите", and "Скопировать
упражнение" is not offered. "Создать «…»" and taking a friend's machine work as from a visit.

The chosen, created or taken machine goes back to the screen that opened the picker. The picker
and the machine form hand it to the nearest visit or plan entry on the back stack, through the
same saved-state key the visit uses today.

### 3.5 Visit screen

- Planned rows follow the machines with sets, in planned order, and fall into tag sections like
  any other machine when the visit is grouped by tags. A row shows the photo, name, tags and setup
  note as a recorded machine's does, with "Запланировано" where the results would be.
- Tapping a planned row expands it to "Добавить подход", which opens the set sheet for the
  machine's first set, and "Убрать", which removes the machine from `visit.planned` at once.
- Once a planned machine has a set it becomes an ordinary machine row, ordered by its first set.
  It stays in `visit.planned`; the screen simply no longer shows it as planned. Deleting its last
  set therefore brings the planned row back.
- Order mode shows only the machines with sets; planned rows return when it ends.
- The order and share buttons appear only when the visit has sets, as now.

## 4. Testing

Test first throughout.

- `core`: the `workout_plan` and `visit.planned` wire and SQLDelight mappings, unreadable JSON
  read as empty, `LocalPlanRepository` with the outbox, `workout_plan` in the pull and the push
  order, the claim and the purge, and the two pure functions.
- `app`: the plans list (titles, count, empty state, "Начать" opening today's visit with the plan
  gone), the plan form (picker round trip, duplicates, remove, reorder, save, delete, leaving
  without saving), the picker in plan mode, and planned rows on the visit ("Запланировано",
  "Добавить подход" recording the first set, "Убрать", absence from the shared text and home card).

## 5. Specs

`functional_spec.md` gains a "Plans" section and loses "plans … not yet available" from the home
screen description; `technical_spec.md` §4.5 describes `workout_plan` and `visit.planned`, and
§4.2's push order gains `workout_plan`. Both are updated in the commits that implement them.
