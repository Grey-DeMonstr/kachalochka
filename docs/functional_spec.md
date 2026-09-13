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
