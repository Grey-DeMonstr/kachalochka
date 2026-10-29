# Stored prior bests and set-count gains — design

## Intent

The gain on a visit card compares the day's best set on a machine with the best set of all
earlier days. Today the own card computes that from the account's whole local history, and a
friend's card shows no gain, because a friend's history would cost one request per friend and
machine. The owner wants:

- gains on friends' cards too, in the feed and on the calendar;
- a "+1 подход" gain when the day repeats the best set more often than before;
- a "Новое" chip on a machine's first day;
- less work for the statistics later.

The fix is to store, on every set, the best result the machine had on the days before the set's
day. A card then needs only the day's own rows, for the account and for a friend alike.

## Goals

- Every `workout_set` row carries the machine's prior best: the best result of the earlier days.
- Visit cards, own and friends', show gains in weight, reps or sets from the stored values, and a
  "Новое" chip where there is no prior best.
- Every write that can change a machine's history keeps the stored values right, on Android and on
  the web, and a sync pull repairs what two devices computed apart.

## Non-goals

- Dropping `recorded_at` and `ended_at` from `visit`, or `recorded_at` from `workout_set`. That
  is the next project; its clean form adds a `day` column to `workout_set`.
- Changing the statistics screen or the friend's machine page. They may read the stored values
  later.
- A server-side trigger. Android must compute the values locally for offline display, so a
  trigger would be a second implementation of the same rule that has to agree to the last float.

## Anti-goals

- Showing a wrong gain or a wrong "Новое" to a friend, so the stored values must be right on the
  server, for old rows too, before the release.
- Special-case code per edit path. One pure function recomputes a machine; every writer calls it.

## A day's result

A machine's result on one day is a triple, `DayBest`:

| Field | Meaning |
|---|---|
| `weight` | the best set's weight, in the machine's unit |
| `reps` | that set's reps |
| `sets` | how many of the day's live sets equal that set in weight and reps |

The best set is `bestSet`'s pick: the heaviest with the most reps at it, the lightest on a
gravitron (`WeightMode.Counterweight`). Two results compare by weight (inverted on a gravitron),
then reps, then sets. Confirmed cases, yesterday being 16×10, 14×10, 14×10:

| Today | Card shows |
|---|---|
| 16×10, 16×10, 14×10 | "+1 подход" |
| 16×10, 16×8, 14×10 | nothing: the second 16 kg set has fewer reps |
| 16×12, 14×10 | "+2 повт." |
| 18×6, 16×10, 16×10 | "+2 кг" |
| 16×10 once, after a day of 16×10 twice | nothing: cards show only gains |

## Stored columns

`workout_set` gains three nullable columns, in both schemas in the same commit:

| Column | Type |
|---|---|
| `prior_best_weight` | double precision / REAL |
| `prior_best_reps` | integer / INTEGER |
| `prior_best_sets` | integer / INTEGER |

They hold the greatest `DayBest` of the same machine over the days before the set's day, under the
machine's weight mode at the time of writing. Every live set of one machine on one day carries the
same values. The domain `WorkoutSet` exposes them as one `priorBest: DayBest?`, with two special
states:

| Stored | Meaning | Card |
|---|---|---|
| all three null | not computed yet: a 1.8.0 client wrote it, or a recompute is due | nothing |
| `0, 0, 0` (`DayBest.NONE`) | the machine had no live set on an earlier day | "Новое" chip |

supabase-kt leaves a null field out of the JSON it sends, and PostgREST leaves an absent column as
it is. That is the wanted behaviour: a row a client has not computed yet keeps the value the
server's backfill gave it, and the recompute never writes null, so nothing is ever lost.

A set's day is its visit's day, `Visit.dayAt`, as every reader places it today. Deleted sets are
left out of the computation and their own stored values are not touched.

## The recompute

`withPriorBests(sets, dayOf, mode, now)` in `domain/gym` is the one rule. It takes a machine's
sets, a function from visit id to day, and the machine's weight mode; it walks the days in order,
carries the greatest `DayBest` so far, and returns the rows whose stored triple differs from the
right one, each with `updatedAt = now`. A second run over its own output returns nothing.

`PriorBestsKeeper` in `core/data` applies it through the repositories, as `VisitNormalizer`
applies `normalizedVisits`: `refresh(owner, machineIds)` reads the owner's visits once, each
machine's sets and weight mode, and writes the rows the rule returns. A set whose visit is
missing is skipped. It is one class for both targets: on Android every read is local; on the web
each machine costs one paged request for its sets, plus one for the visits, and writes are rare.

The write goes through a new `WorkoutSetRepository.storePriorBest(id, best, updatedAt)` that
updates only the three columns and `updated_at`: an UPDATE plus an outbox entry on Android, a
PATCH by id on the web. The keeper runs in the sync worker while a screen may amend the same set,
and a whole-row upsert would lose that amendment.

On the web a recompute that fails after the set was written is sent to the console and the action
counts as done; the next write on that machine recomputes. On Android the keeper reads and writes
locally and does not fail.

Writers that call it, for the machines whose sets they touched:

- `SetRecorder`: record, amend, remove. A reorder changes no values.
- A visit move, replacement or removal on the calendar or in the feed.
- The machine form's save when the weight mode changes or "Пересчитать" rewrote the sets.
- A machine merge, for the kept machine.
- Visit normalization, for the machines of the sets it soft-deleted.
- The Android sync pull, for the machines of the pulled sets and the pulled machines. Two devices
  writing offline each compute from a partial view; the recompute after the pull is what makes
  them agree. It runs where normalization runs, under the same rules: never for a trainee on the
  trainer's device, and what it wrote goes out in the pass's second run.

Account claim, tags, photos, links and plans change no values.

## Reading the values

`exerciseRows` takes a machine's prior best from the day's sets instead of from `OwnHistory`,
which goes away. The gain is the triple comparison in the viewer's unit: the weight converted as
`shownWeight` converts a set's, the text "+5 кг", "+1 повт." or "+1 подход" (plural forms as the
app's other counts), in green, only when the day's result is the greater. `DayBest.NONE` shows
the "Новое" chip in the gain's place ("New" in English); a null prior best shows nothing.
Friends' cards get the same rows, since their sets already come with the day.

The gain uses the machine's weight mode as the viewer reads it. Between a mode change and the
recompute it may be off for a moment, which is accepted.

## Migration and backfill

- Server migration `0030`: adds the columns and fills them for every existing row with one SQL
  statement over `workout_set` joined to `visit`: the day is `coalesce(day, recorded_at at UTC)`,
  the mode comes from `machine.weight_mode`. It does not bump `updated_at`. Friends and web-only
  accounts then read right values at once.
- Local migration `20.sqm`: adds the columns and resets `lastPullAt`, so the next pass pulls every
  set again with the server's values. Until then a signed-in account's cards show no gain and no
  chip; rows waiting in the outbox stay null until the post-pull recompute fills them.
- The anonymous owner on Android never pulls: the start-up normalization is followed by a
  `refresh` of all their machines at every start. The refresh is idempotent and the anonymous
  data is small, so no once-flag is needed.

A one-off divergence between the SQL backfill and `withPriorBests` on an old undated visit is
corrected by the first Android pass that pulls it.

## Spec updates

- Functional spec: the card's gain gains "+1 подход" and the "Новое" chip; friends' cards show
  gains and the chip too.
- Technical spec §4.5: the columns, `DayBest`, `withPriorBests`, the keeper and its callers; the
  feed paragraph that explains why friends have no gain is replaced.

## Testing

TDD throughout, host tests only:

- `withPriorBests`: a chain over days, the set count, a gravitron, two visits on one day, an
  undated visit, deleted sets, and that a second run writes nothing.
- The keeper over the SQLDelight JVM driver: which rows it writes, and the outbox entries.
- `SchemaMigrationTest` for `20.sqm`.
- View-model tests for the own and friends' cards: the three gain kinds and the chip.
- The sync pass: a pulled set triggers a refresh and the fix goes out in the second run.

## Left to the builder

- Names and placement of the string resources and the chip's look within the card.
- Whether the keeper takes the pass's pulled rows or reads the machines again.
- The exact SQL of the backfill.

## Release

One release, 1.8.1.
