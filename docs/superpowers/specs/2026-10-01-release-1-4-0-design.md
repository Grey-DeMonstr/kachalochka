# Release 1.4.0 — design

Five requests from the owner: a better first-set suggestion, machines already in the visit kept
apart in the picker, sorting the machine lists, friends' photos in the machine form with a chosen
cover, and the statistics screen the home screen has listed since the start. The statistics
screen follows the design project's turn 8 (frames 8a–8e).

## 1. Suggested first set

The first set on a machine starts from the previous visit's best set, not its first: the heaviest
weight with the most reps at it, the lightest on a gravitron — the same pick as the record.
`suggestNextSet` takes it through `bestSet`, shared with the statistics.

## 2. Machines already in the visit

The picker opened from a visit reads the day's shown visit. Own machines it holds, with sets or
planned, leave their place in the list and follow everything else at its very end, dimmed, with
"Уже в визите" under the name. They still obey the search and the tags. Tapping one returns it to
the visit as any pick does, so its page opens with the form for its next set. The plan's picker is
unchanged.

## 3. Sorting machine lists

The machine list and the picker show three chips under the search (the list: at the top), drawn
like the tag chips: "Недавние" (last used first, unused ones last by name), "А–Я" and "Частые"
(most visits first, then last used, then name). The default is "Недавние". The sort orders the own
machines and, within each friend's section, the friend's machines.

The choice is the account's: `profile.machine_sort` (`recent` / `name` / `frequent`), synced like
`group_by_tag` and written as soon as a chip is tapped; an unknown name reads as `recent`.

Frequency is the number of visits with a live set on the machine, `MachinePeaks.visits`.
`machinePeaks` counts distinct visits on the device; the server's `machine_peaks` returns a new
`visits` column, so friends' machines sort by their owner's use. Migration `0021` adds the profile
column and recreates the function; `15.sqm` adds the column and resets `lastPullAt`.

## 4. Friends' photos and the cover photo

The machine form's photo row shows the machine's own photos, then, signed in and online, the photos
of the friends' machines in its cluster, each with a small avatar of its owner. The photo standing
for the machine in every list carries a star.

Tapping a photo opens the viewer as before. It gains "Сделать основным", which makes that photo the
cover; a friend's photo has no "Удалить". The choice is an edit like the others: "Сохранить"
writes it.

`machine.cover_photo` names the chosen photo, null by default. `coverPhoto` returns it while it is
live and belongs to the machine's cluster; otherwise the own first photo, then the cluster's first,
as before. A friend's machine shown in a list uses its owner's choice the same way. Migration
`0022` adds the column; `16.sqm` adds it and resets `lastPullAt`.

## 5. Statistics

"Статистика" on the home screen opens the statistics screen on "Общая". The machine's page in a
visit gets a chart button left of the gear, opening the screen on that machine.

- **Top:** a dropdown with "Общая" first, then every own machine by name with its photo. Under it,
  period chips: "Месяц", "3 месяца", "6 месяцев", "Год". A period is whole calendar months ending
  with the current one: "Месяц" in September starts on 1 September, "3 месяца" on 1 July. The
  chosen machine and period are the screen's own and not remembered.
- **A machine:** "Лучший подход · сентябрь" ("· с 1 июля" for longer periods), with the period's
  best set on the right, then a line chart of each visit's best weight over the period, x spanning
  the whole period. A gravitron's chart is negative with zero on top. A period without sets says
  "Нет подходов за период". Below, "Все результаты": every visit on the machine, newest first, the
  date and the visit's results as `setsSummary` writes them, independent of the period.
- **"Общая":** "Упражнения за период · сентябрь", then one card per machine with a set in the
  period, last used first: photo, name, and two boxes joined by an arrow. With a set before the
  period they hold the best set before it ("До 1 сентября") and the best in it ("Сентябрь" or "С 1
  июля"); without one, the worst and the best set in the period ("Худший", "Лучший"). A chip on
  the card gives the change: green "+2.5 кг" when the weight improved, green "+2 повт." when the
  weight is the same and the reps improved, otherwise grey ("−2.5 кг", "−1 повт.", "Без
  изменений"). On a gravitron a smaller weight is the improvement, still written "+x кг". Tapping a
  card opens that machine.

Weights follow the profile's unit. The best set is `bestSet` (§1) and the worst its opposite: the
lightest weight with the fewest reps at it, the heaviest on a gravitron. The pure functions —
`StatsPeriod`, `bestSet`, `worstSet`, `machineProgress`, `bestPerDay` — live in `domain/gym`.

The screen reads the account's live sets once with `WorkoutSetRepository.all`. The web reads them
in pages of 1000 ordered by `(recorded_at, id)`, as every set read there now does, so no row cap
cuts a long history short.

## 6. Testing

Test first throughout: `bestSet` and the suggestion, the picker's visit section, `MachineSort` and
both lists' chips with the profile write, peaks' visits and the wire column, `coverPhoto` with a
chosen photo, the form's friends' photos and cover choice, the migrations, the period starts,
`machineProgress` in its two cases and the gravitron, the statistics view model and screen, and
the home and machine-page entries.
