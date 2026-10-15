# Release 1.7.0 design

Fifteen changes from the backlog, agreed with the user in one round of questions. The functional
and technical specs take each change in the commit that implements it; this note keeps the
decisions and the reasons in one place.

## Tags

Tags compare without letter case everywhere: on a machine, in the chips, in the sections of a
visit and of the statistics, and when a friend's tag is taken. A tag keeps the spelling it was
first typed in, and every screen and the shared text draw it in capitals, in a small font with
wide letter spacing, as the design's section labels are.

## Machine links

Links stay rows of `machine_link`; the shared key of 1.0.2 is not brought back. A shared key
would make one client rewrite friends' `machine` rows through a new server function, and a
friend on an older version would keep writing links that the key never sees.

Instead every device keeps its owner's links direct. When an own machine is in one cluster with
friends' machines it has no direct link to, the device writes those links as the owner's own
rows. Linking, taking a friend's machine and the machine list's group read all lead to that
upkeep, so the friend-link suggestions go away; merging the owner's duplicates stays a
suggestion. A device sees only links written by its group mates, so a chain through a person
outside the viewer's groups still stops there.

## Copies of a machine

A copy of another person's machine keeps its tags, in the receiving account's spelling when it
already has the tag in another letter case. This holds for "Взять себе", for a set recorded for
another account on the device, such as a child, and for a friend's machine added to a plan or to
today's visit. A set recorded for another account uses that account's machine of the same cluster
when it has one, else one of the same name, else a new copy linked to the machine shown.

## Linked names

Lists that only show the links (the cards of the machine list and the picker, the visit's rows
and a friend's visit) write each distinct name once, case-blind, with the avatars of every
owner using it before it, and leave out a name equal to the machine's own, keeping its avatars.
The machine form keeps one row per machine, since each opens its own machine.

## People

The set form's chips are avatars only, scrolled when they do not fit; the active person's
nickname follows their avatar. A nickname, when set, replaces the Google name everywhere,
including the account menu. Every account on a device keeps its nickname with its entry in the
account store, refreshed whenever its profile is read or saved there, because the web can read
only the active account's profile.

## Visits

The question before replacing a visit while moving another names the day's machines and its set
count. "Повторить сегодня" in the calendar card's menu and the visit's own menu (not for today's
visit) adds the visit's machines to today's visit as planned ones, as starting a plan does, and
opens today's visit.

## Adding a machine elsewhere

The menu of the machine form, of a machine page in a visit and of "Упражнение друга" has "В
план…" and, except on today's visit, "Сегодня". "В план…" lists every plan and "Новый план"; a
plan takes the machine at its end and a notice says so; "Новый план" opens the form of a new
plan holding it. "Сегодня" adds the machine to today's visit as planned and opens the visit. A
friend's machine is first replaced by the account's machine of its cluster, else by a new linked
copy.

## Plans

The plan form saves itself: the machines on every change, the name when its field loses focus or
the form is left. A new plan comes into being with its first change, and a plan made from a visit
as soon as the form opens. A plan left with neither a name nor machines is deleted. A plan can be
pinned in its form's top bar (`workout_plan.pinned`); pinned plans head the list with a pin, and
starting one keeps it and asks nothing.

## Machine form

Back with unsaved edits asks "Сохранить изменения?" with "Сохранить" and "Не сохранять";
dismissing the question stays in the form. "Сохранить" runs the ordinary save, recalculation
question included, and then leaves.

## Statistics search

The machine choice is design 9b: a field showing the chosen machine, which on focus takes a
search with a cross. The list under it hides "Общая" while text is typed, keeps the tag sections
and highlights the matched part of each name.

## Body measures

- The measure screen's field starts with the latest value, shows how to take a predefined measure
  and the day of the latest value. − and + step by the finest precision of the measure's own
  values: 1, 0.5 or 0.1, 1 with no values.
- Under the chart, "Все результаты" lists every value, newest first, as the statistics history
  does; tapping one opens that day in the history.
- Each "Процент жира" row opens its formula's chart over the periods. Points fall on every day
  with a value of one of its inputs, from the first day all inputs have one; a missing input is
  interpolated linearly between its neighbours and keeps its last value after it. Age is counted
  on each day.
- With the profile's sex "Женский", "День цикла" is a predefined measure (kind `cycle_day`). A
  new value starts from the last one plus the days since, wrapped by the profile's "Длина цикла,
  дней" (`profile.cycle_length`, 14 to 60, empty meaning 28). Its own screen draws the phases
  only.
- Once a cycle day is recorded, every chart of the account's own data — measures, body fat and
  the account's machine statistics — shades days 1 to 5 of each cycle and outlines the ovulation
  window, days length − 14 to length − 12, for periods of one or three months, and for "Всё"
  shorter than three months (design 10a). Cycles run from each recorded value, by the cycle
  length, backwards before the first and onwards after the last.

## Rest timer sound

"Звук таймера" in Settings, off by default, is a device setting. On Android it picks a system
notification sound and plays it through a short foreground service that shows the rest as a
notification, so the sound comes with the screen locked; it asks for the notification permission
on Android 13 and later when switched on. The web beeps while the tab is open. There is no
vibration.
