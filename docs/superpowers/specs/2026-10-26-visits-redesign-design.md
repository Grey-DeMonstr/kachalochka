# Visits redesign: calendar and feed

Source: the design project's "Visits redesign" canvas (variant a of `VisitsScreen` and
`VisitCard`).

## Intent

The calendar card showed only the machine names of a visit. The user wants to see what was done:
the best set of the day on each machine, and whether it beat the earlier best. The user also wants
a second mode, an endless feed of the own and the friends' visits, newest first.

## Decisions taken with the user

- The growth compares the day's best set on a machine with the best set on that machine from all
  earlier days (not only the previous visit).
- When growth has no good solution now, it goes to the backlog.
- The new tag chip is used in the machines list too.

## Screen

- Under the top bar, a two-segment switch: "Календарь" and "Лента". The device keeps the chosen
  mode (as the theme and the transition length are kept).
- "Календарь": the month header with caret buttons, the month grid with round days and up to four
  dots under the number, then the chosen day's title and its cards.
- "Лента": days newest first, each with a sticky heading ("Пятница, 24 октября"; "Сегодня" and
  "Вчера" with the date at the right), a month label where the month changes, the day's cards.
  Three weeks at a time; scrolling to the end reads three more. "Это первый визит" after the oldest
  visit of the account and its friends.

## Visit card

- Avatar (36 dp, a friend's in their calendar colour) and name; "⋮" on own cards only.
- Tag chips of the visit's machines; no chip row without tags.
- One row per machine in visit order: name, the day's best set in the viewer's unit, the growth in
  green. The first three rows show; "Ещё N упражнений" / "Свернуть" toggles the rest.
- The whole card opens the visit.

## Growth

For a machine, the best set is the one `bestSet` picks (heaviest, then most reps; lightest on a
gravitron). The growth is the statistics screen's change from the earlier best to the day's best,
shown only when it is a gain: "+x кг", or at the same shown weight "+x повт.". No earlier day, no
growth. A set belongs to its visit's day.

Own visits: every own set is local (Android) or read once per load (web), so growth is cheap.

Friends' visits: growth needs each friend's whole history per machine, one read per friend and
machine. This goes to the backlog; friends' cards show the best set without growth.

## Feed reads

- Own visits come from the local rows, as on the calendar.
- Friends' visits come from `groupVisits` per three-week window, and each friend's day reads its
  sets and the friend's machines once read.
- The feed ends at the oldest of: the account's oldest visit and the group mates' oldest visit
  (`FriendsRepository.oldestGroupVisit`, one request ordered by `recorded_at`).

## Calls left to the builder (taken by the agent)

- Own card first on a calendar day, then friends' by name (as before), rather than by time.
- A day without an own visit keeps "Нет визита" and "Добавить визит".
- "Перенести" in the feed switches to the calendar on the visit's day and starts the move there.
- Tags keep their capitals in the new chip, as every other tag on every screen.
- The restyled chip replaces the outlined one everywhere it was used: machine rows, machine pages,
  a friend's machine and the visit's section headings.
