# Kachalochka — functional specification

Kachalochka records gym results, shares them with friends and shows statistics. This document
tells what the app does, screen by screen. Read it before you change a feature or test a release.

- The terms are those of [GLOSSARY.md](../GLOSSARY.md).
- The UI labels are those of the English app. The Russian app has the same screens.
- For how the app is built, see [technical_spec.md](technical_spec.md).

## 1. Platforms

|  | Android | Web |
|---|---|---|
| Account | Optional | Necessary: the app shows only the sign-in screen until an account signs in |
| Data | On the device; syncs with the server when a network is available | On the server only |
| Without a network | Works | Does not work |

- Google is the only sign-in.
- The app is for persons of 13 years and older.
- Android syncs every account that is signed in on the device, not only the active account.

## 2. Home screen and top bar

Every screen has a top bar: a back arrow (not on the home screen), the title and the avatar of
the active account. The visit screen and the machine page have no title in the bar; they show
the day in their content.

- A tap on the avatar opens the account menu. Under "Recording sets as" it lists the accounts on
  the device, the active one marked; a Google account shows its e-mail under the name. Then come
  "Add account", "Settings" and "Sign out of …". A child or a trainee has no
  sign-out.
- Without an account, the avatar is an empty outline.
- An avatar, on every screen, is the photo that the person chose in "Profile", else their Google
  picture, else their initial. Friends see the same avatar.

The home screen shows:

1. Today's card: the date ("Tuesday, 29 September 2026") and a button that opens today's visit.
   The button is "Start" while today has no machine, and "Continue" when today has a set or a
   planned machine.
2. The rows "Visits", "Machines", "Measurements", "Plans", "Statistics" and "Friends". Without an
   account, "Friends" shows a lock and does not open. For a child, "Measurements" and "Friends" do
   not show.
3. On Android without an account: "Sign in with Google" under the rows.
4. At the very bottom: the version, for example "Version 1.1.0". On Android, five taps on it show
   the network check (see [12](#12-network-check)).

## 3. Settings

Settings is one short screen. Each change is saved at once; there is no apply button.

1. The profile card: the avatar, the nickname (else the Google name, else "Profile") and a line
   such as "Male · 39 years · 174 cm · kg". A tap opens "Profile" (see
   [11.3](#113-profile)).
2. "App": the device settings below.
3. Only with an account: "People", a row "Trainer, trainees, family" with the number of linked
   people under it, for example "1 trainer · 2 children" (see
   [11.5](#115-people-children-parents-trainers-and-trainees)).
4. Only with an account: "Account" with the e-mail (see [11.4](#114-account)).

The device settings do not sync. A tap on a row opens its choices in a bottom panel. A choice
applies at once and closes the panel.

| Setting | Choices | Default |
|---|---|---|
| "Theme" | System, Light, Dark | System: the system theme |
| "Language" | System, English, Russian | System: Russian on a Russian device, else English |
| "Animation" | No animation, Fast (150 ms), Normal (300 ms), Smooth (500 ms) | Fast |
| "Timer sound" | Off, On | Off |

- **Language.** The app text and the shared text use the language. Names, own units, tags and
  comments stay as the user typed them.
- **Numbers.** The app always writes a decimal point ("11.3kg"). A typed number can have a point
  or a comma.
- **Animation.** It is a fade between screens. The fade never shows a colour outside the theme.
- **Timer sound.** With "On", the rest timer (see [4.2](#42-machine-page)) sounds at the end
  of each rest.
  - On Android, the row "Sound" under the choice opens the system notification sounds. The default
    sound is the first choice. The sound plays in the headphones when they are connected, also
    with the screen locked and in silent mode. A notification counts the rest down.
  - On Android 13 and later, "On" asks for permission to show notifications. Without the
    permission, the sound plays all the same.
  - The web plays a short beep while its tab is open.

## 4. Visit

A visit is one calendar day of training of one account. It has no start or end. The first set, a
machine picked into the day, a started plan or "Repeat today" makes it.

### 4.1 Visit screen

The heading is the day, for example "Tuesday, 29 September". The year shows when it is not the
current year.

The list shows the machines with sets in the order of their first set. The planned machines
follow; each shows "Planned" in place of results. "Add" at the end of the list opens
the machine picker (see [5.3](#53-machine-picker)).

A machine row shows:

- the photo. A tap on it opens the machine form, so a small gear is in its corner. A gear replaces
  a missing photo.
- the name and the tags.
- the setup note and the results in grey, each on its own line. The results break between the
  weights and the reps when they do not fit on one line.

A tap on the row opens the machine page.

**Order.** A visit with sets has an order button in the top bar, left of "Share". The button
shows a drag handle at the left of each machine. Drag a handle to move the machine with its sets.
While the handles show:

- a photo opens nothing, and a barbell replaces a missing photo.
- the "⋮" menu is hidden.
- "Done" replaces "Add".
- the list is not grouped, and shows only the machines with sets.

"Done", the order button or back hides the handles.

**"⋮" menu.** "Add to plan…", and "Repeat today" except on today's visit (see
[7.3](#73-add-to-a-plan-or-to-today)).

**Group by tags.** When a machine of the visit has tags, "Group by tag" is available. It
splits the list into sections, one for each different set of tags (letter case is ignored). The
tags are the heading, in capitals and joined with ", ". The sections follow the order of their
first machine. Machines without tags are last, under "Other". A row in a section does not
repeat its tags. The choice belongs to the account: it is saved in the profile and syncs.

### 4.2 Machine page

A tap on a machine row opens its page over the list. The top bar has a chart button, which opens
the statistics of the machine, and the rest timer.

The page shows, from the top:

1. The photo, which opens the machine form, with the gear as in the list.
2. The name with the platform weight, the tags and the setup note.
3. "Record": the record of the machine.
4. The results of the previous visit, with how long ago it was ("12 days ago").
5. The latest visit of each friend on the machine: avatar, name, day and results
   ("Oleg  yesterday · 80-85kg 8-6"). The weights use the platform weight of the account's own
   machine, so that they compare with its sets. Example: here the platform is 20 kg; a friend
   records +25 kg on a 10 kg platform; the page shows 15 kg.
6. "Today · 3 sets" (or the date of another day), then the sets of the day, for example
   "#1  (-)29.5 kg × 10", then the comment of the set on one line. A tap on a set opens it for
   editing.
7. "Add", which opens the set form for the next set.

A planned machine without sets also has "Remove". It removes the machine from the visit and
goes back to the list.

**Order of sets.** With two sets or more, the "⋮" menu has "Reorder sets". It closes the set
form and shows a drag handle at the left of each set of the day. Drag a set to move it; the set
numbers follow the new order. While the handles show, a tap on a set opens nothing, the "⋮" menu
is hidden and "Done" replaces "Add". "Done" or back hides the handles.

**Back** closes the set form first, then the page.

**"⋮" menu.** "Reorder sets" (see above), "Add to plan…", and "Repeat today" except on
today's visit (see [7.3](#73-add-to-a-plan-or-to-today)).

**Swipe.** A horizontal swipe opens the previous or the next machine, in the order of the list
(grouped when the list is grouped), planned machines included. The other page opens without the
set form. The swipe stops at the first and the last machine. The swipe does nothing:

- while the set form is open or the sets show drag handles.
- on a machine that is not in the list of the visit.

**Rest timer.** The chip counts down from 1:30. A tap on the chip, or a new set added to today's
visit, starts it again. An edit of a set, or a set on another day, does not. When it does not
run, it shows 1:30.

### 4.3 Set form

The same form adds and edits a set. It moves up over the machine page.

**People.** When it adds a set to today's visit and two or more accounts can record (trainees do
not count), the form starts with a row of avatar chips (see [11.2](#112-several-accounts)). The row
scrolls sideways when the chips do not fit. Only the active person's chip shows the name. The last
chip adds an account. A tap on another person's chip makes that account active:

- The page shows that person's history.
- The set goes into that person's visit, on their machine that is linked to the shown one. Else
  it goes on their machine with the same name (letter case is ignored). Else the app makes them
  a copy of the shown machine with its tags, linked to it and to its linked machines.

**Title.** "#4 New set" for a new set, "#3 Edit: 70 kg × 10" for an edit.

**Weight and reps.** Each has − and + and also takes typed text.

- − and + change the weight by the weight step of the machine, and the reps by one. The weight
  never goes below 0.
- The reps must be a whole number from 1 to 999, else the set cannot be saved.
- The weight is in the machine's own unit and shows it ("65 lb"). A counterweight machine shows
  "−" before the weight.
- When the preferred unit is "kg" or "lb" and is not the machine's unit, the converted weight shows
  under it ("29.5 kg"), to the nearest half unit. All other weights on the page use the preferred
  unit.

**Comment.** "Comment" takes up to 200 characters on several lines. The page shows it after
the set number. A shared visit never shows it.

**Start values.** The first set on a machine starts from the best set of the previous visit,
picked as the record is. Without a previous visit it starts with 10 reps at the platform weight,
when the platform is part of the recorded weight, else at 0. Each next set repeats the weight
and the reps of the set just recorded.

**Buttons.**

- "Add" records the set and keeps the form open for the next set, with an empty comment.
  While the set is written, the button shows progress and ignores taps.
- An edit has "Save", which closes the form, and a trash button, which deletes the set.
- "Cancel", a swipe down or back closes the form and drops the changes.

### 4.4 Pyramids

A pyramid is sets on a machine whose weight goes up or down, for example 40, 31 and 22 kg.

- The set form has a check box row with a pyramid icon above the comment. A tap anywhere on the
  row sets or clears it.
- On a new set the row reads "Start a pyramid". When the day already has a pyramid on the machine,
  it reads "Start a new pyramid", and a tap sets the weight and the reps of the first set of the
  current pyramid.
- An edit reads "Start a pyramid" and shows if that set starts a pyramid. Thus you can make,
  split or join pyramids on any day.
- A pyramid holds the set that starts it and the sets after it, up to the next start. Sets before
  the first start are not in a pyramid.

On the machine page a pyramid is one row with the numbers of its sets and its best set:
"#4–6 ▮▮▮ 36 kg × 10 ⌄". A tap opens the row and shows its sets, indented, with a line at their
left. A tap on one of them edits it. The last pyramid of the machine is open, the others are
closed. While the sets show drag handles, the list is flat, and the first set of a pyramid shows
the pyramid icon.

### 4.5 Share a visit

A visit with sets has "Share" in the top bar. It opens a popup:

- "As text" gives the visit as text. Android opens its share sheet. The web copies the text and
  shows "Copied" or "Couldn't copy". The message stays until a tap or until another
  account becomes active.
- "Group by tag", under "As text", shows when a machine has tags. It is the same choice
  as in the list, and splits the text into the same sections.
- "As image" (Android only) shares a picture of the visit card (see [6.1](#61-calendar)) in the
  current theme. The picture shows every machine and the date ("Thursday, 29 October", with the
  year for an earlier year). It has the same size on every phone.

The text starts with the nickname and the short weekday ("GDM, Thu", or "Thu" without a nickname),
then an empty line, then one line for each machine, in the order of the visit. A line has the
name, the platform weight in brackets when it is not part of the recorded weight, the weights and
the reps.

| Rule | Example |
|---|---|
| Platform weight not part of the recorded weight | "Leg press (+76kg) 20-20-30-40-40kg 5x10" |
| Equal weights show once; equal reps show as sets x reps | "Seated crunch 41kg 10-15-15-15" |
| Different values are joined with dashes | "Chest press 30° 35-35-30kg 10-10-15" |
| Weights in the preferred unit, to the nearest half unit | "Row 99lb 2x10" |
| Own unit: no conversion, its name after the weights | "Cable 3-4 plate 2x10" |
| All weights zero: only the reps | "Pull-ups 3x10" |
| Per side | "Seated row (+11.3kg) 15kg each side, 3x12" |
| Counterweight: "(-)" before the weights | "Gravitron pull-ups (-)27-25-22.5kg 10-8-6" |
| Pyramid: its values joined with "+", groups with " - " | "Press 50 - 40+31+22kg 12 - 10+10+10" |
| Equal groups show once | "Seated row 40+31+22 - 36+27+18kg 2x(10+10+10)" |

- With the preferred unit "Mixed", each machine keeps its own unit.
- With "Group by tag", an empty line separates the sections. A section starts with a
  line of its tags in capitals ("LEGS"), and the machines without tags with "Other".
- The text counts only machines with sets. It never shows set comments.
- The machine rows of a visit, of a friend's visit and the results on the machine page write the
  weights and the reps in the same way.

## 5. Machines

Friends' machines show only with an account. They come from the server. Without a network,
Android shows the last read that it kept (see [10.1](#101-groups)).

### 5.1 How a list shows a machine

The machine list, the picker and the plan form show a machine in the same way:

- the cover photo: the photo chosen in the machine form, else the first photo of the machine, else
  the first photo of a linked machine.
- the name.
- for an own machine, with an account: a link icon and the machines that it is linked with (see
  [10.3](#103-links-between-machines)). In the picker, "Already in the visit" when the visit has
  it.
- the tags.
- the setup note and the day of the last use.
- a trophy with the record, in the preferred unit.

The lists of "Link to…" and "Link to mine" show a shorter row: the photo, the name, the
linked machines and a line of details.

### 5.2 Machine list

"Machines" on the home screen opens the machines of the active account.

- **Sort.** Chips at the top sort the machines: "Recent" (last used first, the default), "A–Z" or
  "Most used" (most visits first). With "Recent" and "Most used", machines without sets are last.
  The sort applies to the own machines and to each friend's section. It belongs to the account: it
  is saved in the profile and syncs.
- **Search.** The field keeps the machines whose name holds the typed text, in any letter case and
  with some typos: none for up to 3 letters, one for 4–5, two for 6–8 and three for more. Each
  machine search in the app works so. An own machine is also found by the names of the friends'
  machines that it is linked with. The cross at the end clears the text and keeps the keyboard
  open.
- **Tags.** The tags of the listed machines follow the search field as chips, each tag once. The
  chosen tags keep only the machines that have all of them.
- With no match, the list shows "Nothing found".
- A tap on a machine opens its form. "Add" makes a new machine. "Save" goes back to the
  list.

**Merge suggestions.** The list suggests a merge for an own machine when another own machine has
the same name (letter case is ignored) or is the same machine through friends' links.

- A machine with a suggestion has a link button after its name. It opens "Link to…" (see
  [5.5](#55-link-and-merge)) with the search set to the name of the first suggested machine.
  There, "Copy settings" is not available, and a merge or a link goes back to the list.
- While a machine has a suggestion, a chip with a link icon follows the sort chips. It keeps only
  the own machines with a suggestion and hides the friends' sections.

**Friends' machines.** With an account, the friends' sections follow the own machines. Each section
has the friend's avatar (or initial in their colour), name and number of machines. It shows one
machine for each physical machine that is not yet the same as an own machine. A tap opens
"Friend's machine" (see [5.6](#56-friends-machine)).

### 5.3 Machine picker

"Add" in a visit or in a plan opens the picker. It has the sort, search and tag chips of the
machine list.

- "My machines" lists the own machines; while you type, the heading is "Similar". With an
  account, one section for each friend follows, as in the machine list.
- Opened from a visit, the machines already in the visit are last, dimmed and marked
  "Already in the visit".
- A pick in a visit opens the machine page with the set form. The machine joins the visit at once
  as a planned machine, so it stays when you go back without a set. A day without a visit gets
  one.
- A pick of a friend's machine uses its stand-in: the own machine of its cluster, else a new copy
  linked to it. Then the page of the own machine opens.
- `Create "…"` shows when you typed a name that no own machine has. It opens the machine form
  with the typed name and the chosen tags (`Tagged "ARMS"`).
- When a machine page is open in the visit, "Copy machine" under "Based on an existing one" opens
  the form filled from that machine, with the typed name.
- In a plan, the picker shows the last result of each machine and has no "Copy machine". A machine
  that is already in the plan is not added again.

### 5.4 Machine form

The form has no title. For a saved machine, a chart button in the top bar opens its statistics.

| Field | Values |
|---|---|
| "Name" | The name |
| "Comment" | The setup note |
| "How the weight counts" | "Total", "Per side" or "Gravitron". "Gravitron" tells that the weight counts as negative: less is better |
| "Platform weight" | The platform weight, and if it is added to the recorded weight |
| Unit | kg, lb or an own unit such as "plate" (up to 12 characters). An own unit shows after each weight and never converts |
| "Weight step" | Any positive number |

**Tags.** Under the setup note, each tag of the account's machines is a chip that you tap on or
off. A machine can have any number of tags.

- The machine's tags come first, as they were when the form opened. A tap never changes the order.
- A tag that no machine would have after the save loses its chip.
- When linked machines have tags that this machine does not have, a row with a link icon shows them
  as dashed chips in the accent colour. A tap adds the tag. Without an account, only the own
  linked machines give tags.
- With an account, the tags of other friends' machines follow as dashed chips with the friend's
  avatar. A tap adds the tag; the save makes it an own tag.
- The last chip, "New tag", adds the typed text (up to 30 characters) as a tag.

**Photos.** The photos run in a row under the name, oldest first. With an account, the photos of
the linked friends' machines follow, each with its owner's avatar. A camera tile ends the row.

- The camera tile offers "Take a photo" and "From the gallery". Android opens the camera or its
  photo picker. On a phone, the web opens the camera or picks a file. On a computer, both pick a
  file.
- When the row has two photos or more, a star marks the cover photo.
- A tap on a photo opens it on the full screen. A swipe shows the next or the previous photo.
  The viewer has "Share", "Delete" (own photos only) and "Make it the cover" (not on the
  cover photo). "Share" opens the share sheet. A browser that cannot share files downloads
  the photo.
- New photos, removed photos and the cover are edits of the form: "Save" writes them.
- The app makes a photo at most 1600 pixels on its long edge before it keeps it.

**Unit change.** A change between kg and lb converts the platform weight and the step in the form
at once:

- lb to kg: to 0.1 kg.
- kg to lb: the step becomes the largest of 5, 2.5 and 1.25 lb that each set recorded in kg is
  within 1 lb of. With no sets, it becomes the one nearest to the old step. The platform weight
  rounds to the step.
- An own unit never converts.

**Recalculate history.** For a machine with recorded sets, "Save" asks "Recalculate the history?"
when the change has an effect on the recorded weights. The question names each change, for example
"−25 kg" for "2 sets" or "kg → lb".

| Change | "Recalculate" does |
|---|---|
| Unit kg ↔ lb | Converts each recorded weight to the new step |
| "Total" ↔ "Per side" | Halves or doubles each recorded weight |
| Platform weight, or if it is added | Changes each weight so that the total stays the same, never below zero |
| To or from "Gravitron" | Nothing: the weights stay |

The total of a set is its weight, plus the platform weight when the platform is not added to the
recorded weight. "Recalculate" applies the changes in this order: unit, sides, platform.
"Keep as recorded" keeps the recorded weights. Both save the machine. When you close the
question without a choice, nothing is saved.

**Leave without a save.** Back leaves at once when nothing is different from the start of the
form. Else it asks "Save changes?":

- "Save" saves as the form's button does, with "Recalculate the history?" when necessary. It is
  off while the form cannot be saved.
- "Don't save" drops the edits and leaves.
- When you close the question, the form stays.
- Text in "New tag" that was not added is not an edit.

**"⋮" menu** of a saved machine:

- "Link to…" (see [5.5](#55-link-and-merge)), for a machine of the active account.
- "Unlink from friends", for a linked machine (see [10.3](#103-links-between-machines)).
- "Add to plan…" and "Repeat today" (see [7.3](#73-add-to-a-plan-or-to-today)).
- "Delete machine", only for a machine without recorded sets, and not for a trainer on a
  trainee's machine. It asks "Delete the machine?" (`"Leg press" will disappear from the list.`).
  "Delete" deletes the machine, its photos and the own links to friends' machines, also without
  a network, and closes the form.

### 5.5 Link and merge

"Link to…" in the machine form opens a list with a search field. The search starts with the
name of the machine.

- "My machines" lists the other own machines.
- With an account, "Friends' machines" lists one row for each friend's machine that
  is not yet the same machine as an own one. Friends' machines that are already linked together
  stay in one frame, the original first; you can choose any of them. The search keeps the whole
  frame when one machine in it matches.
- Each row shows the photo. An own row also shows the friends' machines that it is linked with.

**Choose an own machine** to merge a duplicate. "Merge the machines?" shows the two machines
under "Keep", each with its number of sets and the day of its last set. You choose the
machine that stays.

- The machine used last is selected first and marked "Suggested". A machine without sets
  counts as the oldest. When the two are equal, the edited machine is marked.
- The sets and the photos of the other machine move to the machine that stays. The other machine
  is deleted.
- When the platform weights are different, a check box (on by default) changes the moved weights
  so that their totals stay the same: `Recalculate the sets of "…" for the platform of "…": −5 kg`.
- When one machine is in kg and the other in lb, a check box (on by default) converts the moved
  weights to the unit of the machine that stays: `Convert the sets of "…" from kg to lb`. The
  platform weights are then compared in that unit.
- Friends that linked to the deleted machine become linked to the machine that stays.
- The form of the machine that stays replaces the list. Unsaved edits of the old form are dropped.

**Choose a friend's machine** to link the two as one physical machine (see
[10.3](#103-links-between-machines)). The form opens again.

"Copy settings", above the friends' machines, is off by default. When it is on, the link
also copies the name, the weight mode, the platform weight, the unit and the step of the friend's
machine into the form, and adds its tags. The save then asks about recorded sets as each edit does.

### 5.6 Friend's machine

A tap on a friend's machine opens "Friend's machine", which you can only read. It shows:

- the name and the owner, the linked machines, the tags.
- the photos, with a star on the photo that the friend chose when there are two or more.
- the setup note, the weight mode and the platform weight, in the machine's own unit.
- the friend's statistics on the machine, as on the statistics screen (see
  [8.2](#82-one-machine)), in the preferred unit.

Next to the name and to the setup note, a copy button puts the text on the clipboard. A check
shows on the button for two seconds. A tap on a linked machine opens it: an own machine in its
form, a friend's machine in this view.

- "Take for myself" saves an own copy linked to the friend's machine (see
  [10.3](#103-links-between-machines)) and opens it in the machine form.
- "Link to mine" opens a list of the own machines with a search field. The search starts with
  the name of the friend's machine. The list leaves out machines that are already the same
  machine. A choice links the two and goes back to the view, which then shows the new link.
- A machine that is already linked to an own machine, directly or through friends' links, shows
  neither button.
- Without a network the view shows "No connection to the server" and a retry button.

### 5.7 Tags

- A tag has no letter case and no extra spaces: "legs", "Legs" and " LEGS " are one tag, kept in
  lower case. A machine never has two forms of one tag.
- When no machine of the account has a tag (deleted machines do not count), the tag is deleted. It
  is no longer a chip, and a list filtered by it drops the filter. A filter by a friend's tag stays
  while the friends' machines cannot be read.
- Each screen shows a tag in capitals, in a small font with wide letter spacing. On the machine
  rows and pages and on the visit cards, each tag is a small chip filled with the accent colour.
- An app older than 1.7.1 shows the tags as they were when the account's tags moved to 1.7.1. Tag
  edits in such an app go only to other old apps.

## 6. Visits: calendar and feed

"Visits" on the home screen opens the visits of the active account. A switch under the top bar
chooses "Calendar" or "Feed". The device keeps the choice.

### 6.1 Calendar

The calendar shows one month. Days with a visit are marked. Today and the chosen day are
highlighted. You cannot choose a future day.

Under the month is the visit card of the chosen day:

- The account's avatar and name, and the tags of its machines.
- One row for each machine, in the order of the visit: the name, the day's best set in the
  preferred unit, and the gain in green: "+5 kg", at the same weight "+1 reps", and at the same
  best set "+1 set". The first day on a machine shows a green "New" chip.
- The card shows three machines. "2 more machines" shows the others, and "Show less" hides them.
- A visit without sets shows "No sets yet".
- A tap opens the visit on the visit screen. There you add, edit, delete and order sets as on
  today's visit.

A day without a visit shows "No visit" and "Add visit".

The "⋮" menu of the card:

- **"Move"** shows "Choose the new day" with "Cancel", then moves the visit with its sets to
  the next day that you tap. When that day has a visit, the app asks if it replaces it, and names
  its machines and number of sets. The replaced visit is deleted with its sets.
- **"Add to plan…"** works as in the visit (see [7.3](#73-add-to-a-plan-or-to-today)). The message
  shows under the card.
- **"Repeat today"**, not on today's visit, adds the machines of the visit to today's visit
  as planned machines and opens today's visit. Back goes to the calendar.
- **"Delete"** asks "Delete the visit?" with the date under it, then deletes the visit and its sets
  from the history and the statistics.
- **"Share"** opens the popup of the visit (see [4.5](#45-share-a-visit)). The text message
  shows under the card.

**Friends.** With an account and a network, the calendar also shows the visits of the friends:

- Each friend who trained on a day adds a dot in the friend colour after the account's dot, at
  most four dots on a day.
- Under the account's card, each friend who trained that day has a card, without the menu.
- A tap on a friend's card opens their visit, read-only: the avatar and the name in the top bar,
  the day and the number of machines, then one row for each machine as on the visit screen,
  without its sets. Under each row are the machines that it is linked with. A tap on a row opens
  "Friend's machine".
- The app reads the friends again when you open the calendar, change the month or switch the
  account. The friend's visit is read again each time you go back to it.
- Without a network the calendar shows only the account's visits.

Each friend gets a random colour first. You can change it on the group screen (see
[10.2](#102-group-screen)). The colours belong to the account: friends never see them, and they
sync to the account's other devices.

### 6.2 Feed

"Feed" shows the visit cards of the account and, with an account, of its friends, newest day
first. Without a network, Android shows the friends' cards that it kept.

- Each day has a heading with the weekday and the date ("Friday, 24 October"), or "Today" or
  "Yesterday" with the date at the right. The heading stays at the top while its cards scroll.
- The top day and the first day of each new month show the month above the heading, with the
  year for an earlier year.
- The feed reads three weeks at a time, and reads more until it has a visit to show. When you
  scroll near its end, it reads the next weeks.
- After the oldest visit it shows "This is the first visit". With no visits at all it shows
  "No visits yet".
- The account's card has the menu of the calendar card. Its "Move" opens the calendar on the
  day of the visit, ready for the next tap.

**People.** With an account, a row of avatars without names is above the feed: the account first,
then its friends by name.

- First the feed shows everyone.
- A tap on a person shows only their cards and dims the other avatars. A second tap shows
  everyone again.
- The row is one line; a swipe moves it when the avatars do not fit.
- The device keeps the choice for each account.

## 7. Plans

A plan is a list of machines for a visit to come, without sets. Plans sync as visits do. Friends
never see them.

### 7.1 Plan list

"Plans" on the home screen lists the plans of the account: pinned plans first, each with a pin,
then the others. Each group is oldest first.

- A row shows the name of the plan (or the names of its machines when it has no name), the number
  of machines and "Start".
- An empty list shows "No plans yet". "New plan" makes a plan.

**"Start"** asks "Start the plan?", because you cannot undo a start. Then it:

1. adds the machines of the plan to today's visit (and makes the visit when necessary). A machine
   that is already in the visit is not added again.
2. deletes the plan.
3. opens the visit. Back from the visit goes to the home screen.

A pinned plan starts at once, without the question, and stays in the list.

In the visit, the added machines follow the machines with sets, each with "Planned". A tap
opens the machine page, with "Add" for the first set and "Remove" to remove the machine.
A started plan makes a visit before its first set, so the calendar marks the day and friends see
the visit.

### 7.2 Plan form

A tap on a row or "New plan" opens the plan form.

- "Name" takes up to 40 characters and shows "Untitled" while it is empty.
- The machines of the plan follow in order, each with its cover photo and a remove button. A tap on
  a machine opens its form; back or "Save" goes back to the plan.
- The order button in the top bar shows drag handles. While they show, a tap on a machine opens
  nothing.
- "Add a machine" opens the machine picker (see [5.3](#53-machine-picker)).
- The pin button ("Pin") pins the plan and stays lit. A second tap unpins it.
- The "⋮" menu of a saved plan has "Delete plan", which asks "Delete the plan?".

The form saves itself; it has no save button:

- a change of the machines or the pin saves at once.
- the name saves one second after the typing stops, when the field loses focus, and when you
  leave the form.
- a new plan exists from its first change.
- a plan without a name and without machines is deleted. Its next change brings it back.
- a switch of the account saves the plan and goes back to the list.

### 7.3 Add to a plan or to today

**"Add to plan…"** is in the "⋮" menu of a visit with machines, of a calendar card, of the machine
form, of the machine page and of "Friend's machine". It asks "Which plan?" and lists each plan, as
in the plan list, and "New plan".

- A chosen plan gets the machines at its end, in the order of the visit, planned machines
  included, but not the machines that it already has. The screen stays and shows
  "Added to “Legs”" until a tap.
- "New plan" opens the form of a new plan with those machines, saved at once. Back goes to the
  visit or the machine.

**"Repeat today"** is in the same menus, but not for today's visit. It adds the machines to
today's visit as planned machines, as a started plan does, and opens the visit.

For a friend's machine, both items use its stand-in: the own machine linked with it, else a new
linked copy, as "Take for myself" makes. Without a network the app shows "No connection to the
server". For a machine that the friend no longer shares, it shows "The machine is no longer
available". In both cases nothing changes.

A switch of the account closes the question and the message.

## 8. Statistics

"Statistics" on the home screen opens the statistics on "Overall". The machine page and the machine
form open them on that machine.

The field at the top shows the chosen machine with its photo, or "Overall" with a chart icon.

- A tap on the field starts a search. The field shows an empty text with a cross, and a list
  opens under it: "Overall" while the text is empty, then the account's machines with their photos.
  The chosen one has a check.
- The typed text keeps the machines whose name, or the name of a linked friend's machine, matches,
  as in the machine list. The matched part of the name has a colour. With no match, the list shows
  "Nothing found".
- A choice closes the list and clears the text. The cross or back does the same without a choice.

Chips under the field choose the period: "Month", "3 months", "6 months" or "Year". A period
counts its months back from today: on 1 October, "Month" starts on 1 September. The app does not
keep the machine or the period.

### 8.1 Overview ("Overall")

The overview starts with sort chips: "Recent" (the default), "A–Z" and "Most used", as in the
machine list, and "Growth". "Growth" puts the largest gain first, as a visit card measures it: the
most weight first (compared in kg across kg and lb machines), then the most reps, then the most
sets of the best result. The app does not keep the sort.

- "Group by tag", when a machine has tags, is the choice of the visit list. It splits the
  cards into sections with the tags as headings.
- The sort and the groups also apply to the search list.
- "Improvements only", on each time the screen opens, keeps only the cards with a green chip. With
  none left, the list shows "No improvements in the period".

"Machines in the period · since 1 September" lists the machines with a set in the period. Each card
has the photo, the name and two boxes joined by an arrow:

- With a set before the period: the best set before it ("Before 1 September") and the best set in
  it ("Since 1 September").
- Without one: the worst and the best set of the period ("Start", "Best").

A chip tells the change: "+2.5 kg" in green when the weight increased, "+2 reps" when the weight
stayed and the reps increased, and "−2.5 kg", "−1 reps" or "No change" in grey. A machine
used first in the period without a change shows "New machine" in green. On a counterweight
machine, a lower weight is the increase.

The best set is picked as the record is. The worst set is the lowest weight with the fewest reps
at it (the highest on a counterweight machine). A tap on a card shows the statistics of its
machine. A period without sets shows "No sets in this period".

**Export.** With cards shown, the top bar has an export button. It gives the cards as text, as
"Share" gives a visit:

```text
GDM, past month

Seated crunch +3 reps (45 kg × 15)
Biceps curl — new machine (32 kg × 12)
```

With groups by tags, the lines make sections as in a shared visit.

### 8.2 One machine

- A trophy with the best set of the period.
- A line chart of the best weight of each day in the period. A counterweight machine has negative
  weights, with zero at the top. For "Month" and "3 months", the chart shows the phases of a
  woman's cycle (see [9.4](#94-cycle-day)).
- A period without sets shows "No sets in this period".
- "All results": each visit on the machine, newest first, with its date and its results as the
  visit screen writes them, for all time. A tap opens the visit on its machine page, where you can
  edit the sets.

Weights use the preferred unit.

## 9. Body measures

The user records body measures, such as weight and girths, usually once a week, and sees the body
fat that they give. Measures are private: they sync between the account's devices, but friends
never see them.

### 9.1 Measures

Seven measures are ready-made: Weight (kg), Waist, Chest, Hips, Biceps, Thigh and
Neck (cm). You cannot change their names and units.

- You can add your own measures with a name and a free-text unit, and rename, change or delete
  them.
- A ready-made measure that a body fat formula reads shows the tag of that formula, and you cannot
  delete it. When it was deleted before, it comes back without its old values. You can delete the
  other ready-made measures, and they do not come back.
- Hips count as read only when the sex in the profile is not "Male".
- With the sex "Female", an eighth ready-made measure follows: Cycle day (day) (see
  [9.4](#94-cycle-day)).

"Measurements" on the home screen opens the measures screen. Under "Measures" it lists the
measures. Each row shows:

- the latest value and the unit.
- the change from the value before it ("−0.4"), when there are two values or more.
- how long ago it was taken ("yesterday", "7 days ago").
- the tags of the formulas that read it.

"Reorder" shows drag handles to change the order. "New measurement" opens the measurement form for
today. "Add measure" asks for a name and a unit.

### 9.2 Measurement form

"Measurement" shows the values of one day: one decimal field for each measure, with its unit. The
previous value is the placeholder.

- Under the name of each ready-made measure, a line tells how to measure it. For example, Neck:
  "Just below the Adam's apple, tape tilted slightly forward and down, neck relaxed."
- The day at the top is today. A tap opens a month calendar. Days with values are marked; you
  cannot choose a future day. A day with values loads them for editing.
- "Save" writes the changed values. An empty field clears its value.
- "Delete measurement", on a day with values, asks and then clears the whole day.

### 9.3 Measure screen

A tap on the chevron at the end of a measure row opens the measure:

1. Today's value ("Today, 29 September"). A ready-made measure tells how to measure it.
2. A decimal field between − and +, and "Save". A day without a value starts with the latest
   earlier value; "Save" can keep it as it is.
3. The latest value, its change in the period, and its day ("Last measured: 12 October, 4 days
   ago", or only "today" or "yesterday").
4. A line chart over "1 mo", "3 mo" (the default), "6 mo", "Year" or "All". A period with one
   value shows that value.
5. "Edit history", then "All results": each value, newest first, with its date.

− and + change the value by the largest step that fits all its values: 1 while all values are
whole, 0.5 while all are halves, else 0.1. A measure without values uses 1. An empty field steps
from the value nearest to its day. The value never goes below 0.

**History.** "Edit history" opens "Waist · history": a month calendar with a dot on each
day with a value. You cannot choose a future day. A tap on a day shows its value in the field,
with "Save" and, for a day with a value, a trash button. A tap on a row of "All results"
opens the history on that day. Back closes the history first.

The menu in the top bar renames your own measure and changes its unit. It also deletes a measure
that you can delete, with all its values, after a question.

### 9.4 Cycle day

Cycle day is shown only with the sex "Female". Day 1 is the first day of menstruation.

- No formula reads it, so you can delete it, and it does not come back.
- With another sex it is hidden with its values until the sex is "Female" again.
- Its row shows no change, because the days repeat.
- The placeholder in the form, and the start value on its screen, is the expected day: the last
  value plus the days since then, starting again after the cycle length of the profile. Before
  its first value it counts back.
- − and + go round the cycle: after the last day comes day 1.

Its chart has no line and no change. From the period start to today (from the first value for
"All"), it fills days 1 to 5 of each cycle and outlines the ovulation window with a dashed line:
days (length − 14) to (length − 12), day 1 at the earliest. A legend names "Menstruation" and
"Ovulation". A recorded day starts a cycle that continues until the next recorded one; the latest
one continues after it.

After a woman records a day of the cycle, the same phases show behind the line of each chart of
her own data: each measure, each body fat formula and her own machines in the statistics, never a
friend's machine. They show for "1 mo" and "3 mo", and for "All" while its values cover less
than three months. Next to the latest value, a measure screen shows today's day of the cycle with
a drop: "Day 12 of 28".

### 9.5 Body fat

"Body fat", under the list, estimates the body fat with three formulas:

| Tag | Name | Reads |
|---|---|---|
| `NAVY` | "US Navy" | Waist, neck and height; for a woman also hips |
| `YMCA` | YMCA | Weight and waist |
| `BMI` | "Deurenberg" | Weight, height and age |

Each formula uses the latest value of each measure, and the sex, the birth date and the height of
the profile. The age is in whole years. A row shows its result ("18.4 %") or what it needs
("Needs: neck, height"). While the sex, the birth date or the height is missing, a line under the
rows opens Settings.

A tap on the chevron of a row opens the chart of that formula, titled with its name ("US Navy"):

- Under the title, it names each input ("Needs: sex, height, waist, neck").
- The latest result, its change in the period, the day of the latest result, the periods and the
  chart of a measure, and "All results".
- A point is on each day with a value of a measure that the formula reads, from the first day that
  all of them have a value. On a day without its own value, a measure uses the straight line
  between its values before and after that day, and its last value after them. The age is counted
  on each day.
- A result outside 2–70 % gives no point.
- Without a result, "No values" replaces the latest value, and a period without points shows
  "No values in this period". A missing sex, birth date or height adds the link to Settings
  under the latest value.

## 10. Groups and friends

### 10.1 Groups

Accounts make groups. The members of a group see each other's calendars, visits, machines and
photos, read-only. Nothing is shared outside a group.

- The owner makes the group and invites others with a link to the web app or with its
  eight-character code. The Android app joins by code.
- An invite link asks before it joins, and tells that the members will see the user's visits and
  machines. A link opened without an account goes to the next account that signs in in that
  browser.
- A member can leave. The owner deletes the group instead.

Friends' data is read from the server. On Android, the app keeps some of it on the device:

- A friend's photo, after it showed once, shows also without a network.
- The feed, a friend's calendar and the friends' results on a machine page keep what they read
  last for one month. They show it at once, and the server answer replaces it. With a bad network
  or none, they show what they kept.
- Sign-out removes what was kept for that account.

### 10.2 Group screen

The group screen shows "Invite code: …" and "Invite", then the members. The owner is
marked "owner". The owner has "Delete group"; another member has "Leave group". Without
a network the screen shows "No connection to the server" and "Retry".

Each member row starts with the avatar, or the initial in the friend colour. Each row except the
user's own ends with a colour dot. A tap on the dot opens a palette of eight colours. The colour
marks that friend in the user's calendars, feed and machine lists.

### 10.3 Links between machines

A link tells that an own machine and a friend's machine are the same physical machine. Machines
joined by links, directly or through other friends' links, are one cluster: one machine.

You make a link in three ways:

- "Take for myself" on a friend's machine, or a pick of a friend's machine: an own copy with the
  friend's settings and tags, linked to the friend's machine.
- "Link to…" in the machine form.
- "Link to mine" on a friend's machine.

For one cluster:

- the picker does not offer the friend's machine next to the own machine.
- the machine page shows each friend's latest visit on it.
- a friend's visit, a friend's machine and the machine form list the linked machines.

The app keeps each link direct. When an own machine is the same as a friend's machine only through
other friends' links, the app links the two itself. It does this at each start, at each account
switch and each time it reads the friends' machines. The three ways above also link at once to
each friend's machine of the cluster. The app sees only the links of the user's friends, so a
chain through a person outside the user's groups stops there.

**Lists of linked machines.** Each list starts with a link icon, then the owner's avatar before
each machine name: own machines first, then friends' machines by owner and by name.

- In the machine list, the pickers and a friend's visit, machines with the same name (letter case
  and outer spaces ignored) show the name once, after the avatars of all their owners. A name that
  is equal to the name of the machine itself is not shown; only the avatars stay.
- The avatars before one name overlap, the first on top.
- The link icon stays alone at the left. A long name breaks under its first line; a name that
  does not fit after the one before starts a new line.
- In the form and in a friend's machine, each linked machine has its own row. A tap opens it: an
  own machine in its form, a friend's machine as from the machine list.

After a read of the friends' machines, the machine list, the picker, the form, the link lists, a
friend's machine, the visit screen and the statistics use that read at once. They update it when
the server answers. When the server does not answer, they keep the last read; only a friend's
machine shows "No connection to the server".

"Unlink from friends" in the form's menu, after a question, removes each link between the machine
and friends' machines, whoever made it.

An unlink, and with an account a merge, change friends' links on the server, so they need the
network. Without it the app shows "No connection to the server" and changes nothing.

## 11. Accounts

### 11.1 Sign-in

- Google is the only sign-in.
- On Android the app works without an account. All data stays on the device, and the first account
  that signs in takes it. Accounts added after that start empty.
- The web app shows only the sign-in screen until an account signs in.

### 11.2 Several accounts

Several accounts can be signed in on one device. One of them is active, and the app records into
it. Each account keeps its own visits, machines and history.

- Only the addition of an account asks Google. A switch does not, and works without a network.
- You switch from the avatar menu or, while you record, from the chips of the set form. Thus a
  parent records their own sets and their child's sets one after the other.
- A sign-out removes the account from the device but keeps its data. A new sign-in finds it again.
  On Android, the sign-out of the last account makes the app work without an account again.
- Friends are not available until an account signs in.

When the server stops accepting the sign-in of the active account, the app signs it out. On
Android its data stays on the device. Another account that the server stops accepting stays in
the list but does not sync. A switch to it fails and tells so; the account stays in the list.

### 11.3 Profile

The profile card in Settings opens "Profile". The form saves itself: a choice (such as the sex,
the unit or the avatar) at once, a typed field one second after the typing stops and when the
form closes. "Changes are saved at once" at its foot tells this. A field with a value that is
not correct is red and keeps the value saved before.

| Field | Values | Without an account |
|---|---|---|
| Avatar | "Take a photo" or "From the gallery"; "Delete" brings back the Google picture | No |
| "Nickname" | Up to 40 characters. The Google name is the placeholder and is used when the field is empty | No |
| "Sex" | Male, Female | Yes |
| "Birth date" | DD.MM.YYYY, from 1900 to today | Yes |
| "Height, cm" | 50 to 250 | Yes |
| "Cycle length, days" | 14 to 60; empty means 28. Only with "Female" | Yes |
| "Weight units" | "kg" (the default), "lb" or "Mixed" | Yes |

- The nickname replaces the Google name everywhere: the account menu, the set form and each list
  of people. Friends see it, and it starts a visit shared as text.
- The sex, the birth date and the height are for the body fat formulas. Nobody else sees them. An
  empty field clears its value.
- **Weight unit.** With "kg" or "lb", each set, platform weight, step and result on each screen,
  a friend's included, shows in that unit, converted as in a shared visit. A step converts to one
  decimal. "Mixed" shows each machine in its own unit. An own unit never converts. The machine
  form keeps the machine's own unit, and body measures keep theirs.

### 11.4 Account

"Account" in Settings shows the e-mail of the active account with "Signed in with Google", then
"Leave", which signs the account out.

Under "Danger zone" is a red "Delete account". Its question "Delete the account?" asks you to type
DELETE (any letter case) before "Delete" works. Then the app deletes the account and all its data
from the server and, on Android, from the device: visits, machines with their photos, plans,
measures, the profile, the groups that it owns, its group memberships and its links to parents,
children, trainers and trainees. Then it signs the account out.

- When the account is the only parent of a child signed in on the device, the child's data also
  leaves the device.
- A child cannot delete its account from a parent's device.
- Without a network the screen shows "No connection to the server" and changes nothing.
- After a sign-out or a deletion, the screen closes.

### 11.5 People: children, parents, trainers and trainees

"People" in Settings lists each person linked to the account, under "Trainer", "Trainees",
"Children" and "Parents". An empty heading does not show. With nobody linked, the screen shows
"Nobody here yet".

- Each person shows the avatar and the name. The "⋮" menu has "Remove", which asks "Remove the
  trainer?", "Remove the trainee?", "Remove the child?" or "Remove the parent?" and then removes
  that one link.
- Without a network the screen shows "No connection to the server" and changes nothing.

"Add" in the top bar asks "Whom to add?" with "A trainer", "A trainee", "A child" and
"A parent".

**Codes.** A child and a trainee link by a code from the other person:

- **"A child"** opens "Add a child": a new code, "Code for your child: …", with its link,
  "Share" and "Done".
- **"A parent"** opens "Add a parent": what a parent can do, then a field for the parent's
  code and "Add".
- **"A trainee"** opens "Add a trainee": a new code, "Code for your trainee: …", with its link,
  "Share" and "Done".
- **"A trainer"** opens "Add a trainer": what a trainer can do, then a field for the trainer's
  code and "Add".

- A code works once and for one day. A new code replaces the previous one. The code dialog tells
  where the other person types it: "Settings → People → Add".
- The link shows on the web, and on Android when the build knows the address of the web app.
  Else Android shows and shares the code only.
- An unknown or expired code shows "Code not found or expired". The account's own code shows
  "That is your own code".
- A link of a code opens the web app and asks "Add a parent from the invitation?" or "Add a trainer
  from the invitation?" before it links. A link opened without an account goes to the next account
  that signs in in that browser. After the link, "People" opens.
- Family Link can ask the parent once to let the child sign in to the app.
- A link and its removal need a network.

**Child.** A parent records for a child and sees the child's gym data, but never the child's
profile, measures or groups.

- A linked child shows in each of their parents' account menus, with "Child" under the name,
  and among the chips of the set form. A child of two parents signed in on one device shows once.
- When the parent chooses the child, the home card, the visits and sets, the machines with their
  photos and links, the calendar, the statistics and the plans are the child's. They sync between
  the parent's devices and the child's.
- While the child is chosen, "Measurements" and "Friends" leave the home screen, and Settings shows
  only "App". The sort and group choices are not saved: they stay for the child until the app
  closes.
- The friends shown next to the child's data are the people that the parent and the child share a
  group with, the parent included.
- A child has no sign-out. "Remove" in "People" removes the child and its data from the parent's
  device; the child keeps what already synced. The sign-out of a parent removes their children
  from that device until the parent signs in again.
- A child signs in on their own phone or browser with their own Google account.

**Trainee.** A trainer sees a trainee's machines, visits and plans, and writes the trainee's
plans. A trainer never sees the trainee's profile, measures, groups or friends. The trainee's
code entry, or their "Add" in the link question, is their consent; nothing links without it.

- A linked trainee shows in the trainer's account menu with "Trainee" under the name, but never
  among the chips of the set form.
- The trainer only looks at the trainee's visits: the home card starts no visit, a visit opens
  without "Add", the gear and the order button, and its sets do not open. The calendar has no
  "Add visit", and its card menu has only "Add to plan…" and "Share". "Repeat today" and
  "Start" on a plan are not available.
- The trainer makes, changes and deletes the trainee's plans, pinned plans included.
- The trainer can add a machine for the trainee, from the machine list or from the picker of a
  plan, without photos. The picker shows only the trainee's own machines. An existing machine
  opens read-only: no "Save", no "Link to…", no camera tile, and only "Add to plan…" in its
  menu. The machine list shows no merge suggestions.
- A person who is both a child and a trainee of accounts on one device is a child.

## 12. Network check

The network check helps the owner of the app find why a device cannot connect to the server. It
is hidden and is only on Android.

1. Tap the version at the bottom of the home screen five times. A link "Network" shows next to the
   version until the app stops.
2. Tap "Network" to open the network screen.

The screen starts with the proxy setting (see below). Then it shows the app and Android versions,
the device, the time and the time zone, the server host, the network type, the DNS servers and
Private DNS, the system proxy and the proxy in use, the number of signed-in accounts, and the time
and result of the last sync and the last background error since the app started.

Then it does the checks: it finds the addresses of the server, and sends requests to the sign-in,
data and photo services of the server, to ya.ru and to google.com. Each check shows progress, then
its result, its time in milliseconds and the answer or the error text. A check is good when an
answer comes, whatever its HTTP status.

"Share" sends the full report as text. The report never contains keys, tokens or e-mail
addresses.

**Proxy.** The screen sets an HTTP proxy: an address ("host:port") and "Use proxy".
When it is set, all requests of the app to the server go through it: sign-in, sync, friends' data,
photos and the checks. The device keeps the setting. A change applies after the app starts again,
as the note "The proxy starts to work after the app restarts." tells.

## 13. Privacy and terms

The Privacy Policy and the Terms of Service are pages next to the web app. The footer of the web
page links to them; you can read them without an account. The footer shows only on the sign-in
screen and the home screen.

A person who cannot sign in any more asks for the deletion of their account through the issues of
the repository.
