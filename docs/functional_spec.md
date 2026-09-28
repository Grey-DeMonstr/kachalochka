# Kachalochka

An Android / Web app to save sport results, share them with friends and collect statistics.

# Requirements

Online data storage (for web app to be synced with android one), login using Google OAuth.
Android app should be local-first (can work offline and without login), but sync all the data
online whenever possible, for every account signed in on the device. The web app cannot work
without an account.

Several accounts can be signed in on one device at once, with one of them active. Everything is
recorded into the active account, and each account keeps its own visits, machines and history.
Switching between them is easy and works from inside a visit. The account in use is
signed out on its own once the server stops accepting its sign-in; on Android its rows stay on
the device. Any other account the server stops accepting stays listed but stops syncing; switching
to it fails to activate and reports the failure, and the account stays listed.

Both apps must support a dark and a light theme. By default the theme follows the system setting;
the user can override it and pick light or dark explicitly. The choice is remembered between
launches.

# Use cases

## Regular gym visit

After doing some exercise user wants to record weight and sets for the exercise machine in the UI.
There should be an option to add a new machine or copy and edit existing - if we are doing another
exercise on the same machine.
There should be an option to add a photo or several photos (on Android - from camera as well).
There should be an option to set the machine default weight (platform weight) and choose whether
this weight is total - or per each hand/leg separately.
When the machine is chosen, UI should suggest to add a set with one click on a chosen weight (the
suggested one should be taken from the previos day). Statistics from the previous day on the same
machine should be visible as well.
There should be ability to measure weight (kg / ft), length (km), time (seconds / minutes) or just
count without anything. Some machines measure weight negatively (e.g. gravitron).
Simple editing of data entered should be available.
Comments to exercise AND to the whole day / visit should be allowed.

### Screens

Every screen shares a top bar: a back arrow (except on the home screen), the screen title, a
rest timer chip and the avatar of the active account. The timer counts down from 1:30; tapping
the chip, or saving a set, restarts it, and it shows the full 1:30 when idle. Tapping the avatar
opens the account menu: the signed-in accounts with the active one marked, then adding another
account, the settings and signing out. With nobody signed in the avatar is an empty outline.

The home screen shows today's card: how many machines and sets were recorded today and the
last set, or "Подходов пока нет", and a button opening today's visit. A visit is one calendar
day; it has no start or end and comes into being with its first set. Below the card are rows for
visits, machines, plans, statistics and friends; plans and statistics are shown but not yet
available. On Android with nobody signed in, "Войти через Google" sits at the bottom of the
screen.

The visit screen lists the day's sets grouped by machine, machines in the order of their first
set, and ends with "Новый тренажёр", which opens the machine picker. Tapping a machine's row
expands it to show its sets; tapping a set opens it for editing or deletion. «Порядок» shows a
handle at the left of every machine and set; dragging a machine's handle moves the machine with
its sets, dragging a set's handle moves it among its machine's sets. «Готово» hides the handles.
Today's visit is titled "Сегодня", any other day's with its date.

Adding or editing a set uses the same sheet. It shows the signed-in accounts as a row of chips,
the machine, the set number, the machine's setup note, the previous visit's sets on that machine,
and weight and reps steppers seeded from the previous visit's set. Saving records the set. The
weight can also be typed, with a comma or a point; − and + step it by the machine's weight step.
Tapping another account's chip switches to it: the shown history and the save button follow that
person, and the set is recorded into their own visit. Swiping the sheet down, or pressing back on
the phone or in the top bar, collapses it to a bar naming the machine and the next set; tapping
the bar or swiping it up opens the sheet again. Pressing back while a set is being edited leaves
the edit and collapses the sheet. The machine's name is not a button: another machine is chosen
with "Новый тренажёр".

The machine picker lets the user search machines by name; "Создать «…»" opens the machine form
pre-filled with the typed name. When a machine is already chosen for the visit, "Скопировать
тренажёр" opens the form pre-filled from that machine, with the typed name instead of its own.
Recently used machines are listed with their last result.

The machine form collects a name, a setup note, how the weight is counted (total or per side),
the platform weight and whether it is added to the recorded weight, the unit and the weight
step. The unit is kg, lb or an own unit such as "плитка", whose name is then written after
every weight on that machine, with no conversion. The weight step is any positive number, with
1, 2,5, 5 and 10 one tap away.

The "Тренажёры" row lists the active account's machines by name, each with its unit, how its
weight is counted and its step. Tapping one opens it in the machine form; "Новый тренажёр"
adds one. Saving returns to the list.

The "Визиты" row opens a calendar of the active account's visits, a month at a time. Days with a
visit are marked, today and the chosen day are highlighted, and future days cannot be chosen.
Below the month is the chosen day's visit, with its machines and set count. Tapping it opens
it on the visit screen, where its sets are added, edited, deleted and ordered as today's are.
A day without a visit offers "Добавить визит". "Перенести" moves a visit, with its sets, to the
day tapped next; if that day already has a visit, the app asks whether to replace it, and
replacing removes it with its sets. "Удалить" asks for confirmation, then removes the visit
and its sets from the history and the statistics.

Photos, comments on a set and counting left and right separately are shown on these screens but
not yet available.

## Sign-in and accounts

Google is the only way in. On Android the app works without an account: everything recorded stays
on the device, and the first account to sign in takes ownership of it. Accounts added later start
empty. The web app shows nothing but the sign-in screen until an account is chosen.

Adding an account asks Google. Switching between accounts afterwards does not, and works with no
network — either from the avatar menu or, while recording, from the chips in the set sheet, so a
parent can record their own sets and their child's between one machine and the next.

Signing out of an account removes it from the device but keeps what it recorded, and signing back
in finds it again. On Android, signing out of the last account returns the app to working without
one. Friends stay locked until an account is signed in.

## Custom exercsies

Someone may want to record non-machine exercises, like "Run 1km" where measure will be in minutes,
or "Doing some press" measured in times.

## Statistics

User should be able to view statistics over month, year or any arbitrary period. Some simple
graphs - one per exercise.

## Data import / export

User should be able to import or export data in some predefined hardcoded format.

## Group sharing

Signed-in users form groups. The creator owns the group and invites others with a link to the
web app or with its eight-character code; the Android app joins by code. An invite link asks
before joining, warning that the group's members will see the user's visits and machines; a
link opened while signed out is offered to whichever account signs in next in that browser.
Every member sees the other members' calendars and visits, read-only, and nothing is shared
outside a group. A member can leave; the owner deletes the group instead. Friends' data is read
online and never stored on the device.

A machine can be taken from a friend's list: the copy keeps the friend's settings and stays
linked to the friend's machine as one physical machine. The set sheet then shows friends'
latest results on it, and a friend's visit shows their sets on it under the user's own machine
name. A link can be broken from the machine form.
