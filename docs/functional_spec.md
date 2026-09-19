# Kachalochka

An Android / Web app to save sport results, share them with friends and collect statistics.

# Requirements

Online data storage (for web app to be synced with android one), login using Google OAuth.
Android app should be local-first (can work offline and without login), but sync all the data
online whenever possible.

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
rest timer chip and a settings gear (except on the settings screen). The timer counts down from
1:30; tapping the chip, or saving a set, restarts it, and it shows the full 1:30 when idle.

The home screen shows a visit card. While a visit is running, the card shows "Визит идёт", the
elapsed time, how many machines and sets so far, the last recorded set and a "Продолжить"
button. With no visit running, the card offers "Начать визит", which starts one. Below the card
are rows for plans, statistics and friends; they are shown but not yet available.

The visit screen lists the visit's sets grouped by machine. Tapping a machine's row expands it
to show its sets; tapping a set opens it for editing or deletion. "Завершить визит" ends the
visit and returns to the home screen.

Adding or editing a set uses the same sheet. It shows the machine, the set number, the machine's
setup note, the previous visit's sets on that machine, and weight and reps steppers seeded from
the previous visit's set. Saving records the set.

The machine picker lets the user search machines by name; "Создать «…»" creates a new machine
from the typed name. When a machine is already chosen for the visit, "Скопировать тренажёр"
starts a new machine pre-filled from it. Recently used machines are listed with their last
result.

The machine form collects a name, a setup note, how the weight is counted (total, per side or
counterweight), the platform weight and whether it is added to the recorded weight, the unit
(kg or lb) and the weight step.

Photos, comments on a set, a custom unit and counting left and right separately are shown on
these screens but not yet available.

## Custom exercsies

Someone may want to record non-machine exercises, like "Run 1km" where measure will be in minutes,
or "Doing some press" measured in times.

## Statistics

User should be able to view statistics over month, year or any arbitrary period. Some simple
graphs - one per exercise.

## Data import / export

User should be able to import or export data in some predefined hardcoded format.

## Group sharing

Users should be able to form a group (party), where everyone sees others' results. There should be
a party newsfeed, populated automatically when someone ends the visit. Data should be dynamic, so
if a user modifies his visit history (updates or changes something), newsfeed should be updated as
well.
