# Kachalochka

An Android / Web app to save sport results, share them with friends and collect statistics.

# Requirements

Online data storage (for web app to be synced with android one), login using Google OAuth.
Android app should be local-first (can work offline and without login), but sync all the data
online whenever possible, for every account signed in on the device. The web app cannot work
without an account. The app is for users aged 13 and over.

Several accounts can be signed in on one device at once, with one of them active. Everything is
recorded into the active account, and each account keeps its own visits, machines and history.
Switching between them is easy and works from inside a visit. The account in use is
signed out on its own once the server stops accepting its sign-in; on Android its rows stay on
the device. Any other account the server stops accepting stays listed but stops syncing; switching
to it fails to activate and reports the failure, and the account stays listed.

Both apps must support a dark and a light theme. By default the theme follows the system setting;
the user can override it in Settings, choosing Системная, Светлая or Тёмная the way the weight unit
is chosen. The choice is remembered between launches.

The app speaks Russian and English. At first it follows the device: Russian on a device set to
Russian, English on any other. Settings has "Язык" — Системный, English or Русский — remembered
on the device like the theme and applied with the rest of the settings. The shared text is written
in the app's language; names, own units, tags and comments stay as they were typed. In English a
machine is called a machine.

Numbers are written with a decimal point everywhere, the shared text included ("11.3кг"); a typed
number takes a point or a comma.

Moving between screens is a short fade that never flashes a colour outside the theme. Settings
has an "Анимация переходов" field for its length in milliseconds, 0 to 1000, 150 by default;
0 switches screens instantly. Like the theme, it is remembered on the device and not synced.

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

Every screen shares a top bar: a back arrow (except on the home screen), the screen title and the
avatar of the active account. Only a machine's page in a visit adds a rest timer chip before the
avatar. The timer counts down from 1:30; tapping the chip, or saving a set, restarts it, and it
shows the full 1:30 when idle. Tapping the avatar
opens the account menu: the signed-in accounts with the active one marked, then adding another
account, the settings and signing out. With nobody signed in the avatar is an empty outline. An
avatar, wherever a person is shown, is the photo they chose in Settings, else their Google
picture, else their initial; friends see it too.

The home screen shows today's card: the date, as "Вторник, 29 сентября 2026", and a button
opening today's visit, "Начать" while today has no visit and "Продолжить" once it has a set or a
started plan. A visit is one calendar
day; it has no start or end and comes into being with its first set. Below the card are rows for
visits, machines, plans, statistics, friends and body measures. On Android with nobody signed
in, "Войти через Google" sits at the bottom of the screen. The very bottom names the app's
version, "Версия 1.1.0".

The visit screen is headed by its day, as "Вторник, 29 сентября" (with the year when it is not
this one), and lists the day's machines in the order of their first set, ending with "Добавить",
which opens the machine picker. A machine's row shows its photo, as the machine list does, or a
barbell without one, then its name with its tags, each in a small frame; its comment (the setup
note) and its results follow in grey, each on a line of its own, and the results wrap between the
weights and the reps when they do not fit one line. Tapping the photo opens the machine form;
tapping the row opens the machine's page. The list has no count row. A visit with sets has an
order button in the top bar, left of "Поделиться", drawn like the drag handle and lit while on: it
shows every machine's sets, numbered "#1", "#2" and so on, and a handle at the left of every
machine and set; dragging a machine's handle moves the machine with its sets, dragging a set's
handle moves it among its machine's sets. While it is on, "Готово" replaces "Добавить" at the end
of the list, and it or the button again hides the handles. When a machine of the visit has tags,
"Группировать по тегам" splits the list into sections, one per distinct set of tags, headed by the
tags and in the order of their first machine; untagged machines come last under "Остальное". A
row under a heading does not repeat its tags. The choice is the account's, saved in its profile
and synced, and ordering shows the plain list.

A machine's page covers the list. Its top bar has a chart button opening the machine's statistics,
a gear opening the machine form, whose photo opens it too, and the rest timer. It shows the photo,
the name with the platform, the tags and the comment, then two cards: "Рекорд", the best set ever
on the machine
— the heaviest weight with the most reps at it, the lightest on a gravitron — and the previous
visit's results, headed by how long ago it was ("12 дней назад"). Friends' latest visits on the
machine follow, each with the friend's avatar and name ("Олег  вчера · 80-85кг 8-6"). Under
"Сегодня · 3 подхода" (another day's date instead of "Сегодня") are the day's sets on the machine,
"#1  (-)29.5 кг × 10" with the set's comment; tapping one opens it for editing. "Добавить" opens
the form for the next set, and a planned machine without sets also offers "Убрать", which takes it
out of the visit and returns to the list. Back, in the top bar or on the phone, closes an open
form, then the page.

A visit with sets has a "Поделиться" button in the top bar that hands the visit over as text:
Android opens its share sheet, the web copies the text and says "Скопировано" (or "Не удалось
скопировать"). The notice stays until it is tapped or another account becomes active.

The text starts with the nickname and the short weekday ("ГДМ, чт", or only "чт" without a
nickname), then an empty line, then one line per machine in visit order: the name, the platform
weight in brackets when it is not included in the record, the weights and the reps, for example
"Жим ногами (+76кг) 20-20-30-40-40кг 5x10". Equal weights are written once ("Пресс сидя 41кг
10-15-15-15"), different ones joined with dashes; equal reps are written as sets x reps, different
ones joined with dashes ("Жим от груди 30° 35-35-30кг 10-10-15"). Weights are in the unit chosen in
the profile: with "кг", the default, pounds are converted to the nearest half kilogram; with "lb",
kilograms are converted to the nearest half pound ("Тяга 99lb 2x10"); with "Смешанные", every
machine's weights stay in its own unit. An own unit is never converted and is written as is with
its name ("Блок 3-4 плитка 2x10"). A machine whose weights are all zero shows only its reps
("Подтягивания 3x10"). A machine whose weight is counted per side says so after the weights
("Гребная тяга (+11.3кг) 15кг на каждую, 3x12"). A gravitron's weights are written after "(-)",
since a smaller one is progress ("Подтягивания в гравитроне (-)27-25-22.5кг 10-8-6"). The machine
rows of the visit screen and of a friend's visit, and the previous visit's and friends' results on
the machine's page, write a machine's weights and reps exactly the same way. When the visit is
grouped by tags, the machine lines form the same sections, separated by an empty line, each tagged
one starting with a line of its tags.

Adding or editing a set uses the same form, which slides up over the page. Adding shows the
signed-in accounts as a row of chips, then "#4 Новый подход"; editing shows "#3 Правка: 70 кг ×
10". Weight and reps steppers follow, both typed as well, the weight with a comma or a point; − and
+ step the weight by the machine's weight step and the reps by one, and reps that are not a whole
number above zero cannot be saved. The weight is the one place where the machine's own unit comes
first: it is typed and stepped in that unit, written beside it ("65 lb"), a gravitron's with "−"
before it, and when the profile's unit is "кг" or "lb" and differs from it, the converted weight
follows under it ("29.5 кг"), to the nearest half unit. The page's other weights follow the
profile's unit. A "Комментарий" field of several lines takes up to 200 characters, saved with the
set and shown after its number; a shared visit never includes them. The first set on a machine
starts from the previous visit's best set, picked as the record is; each later one repeats the
weight and the reps of the set just recorded. "Добавить" records the set and keeps the form open
for the next one, without a comment; until it is written, the button shows progress and takes no
taps. Editing offers
"Сохранить", which closes the form, and a trash button that deletes the set. "Отмена", swiping the
form down, or back, closes it, leaving any edit; starting to order closes the page. Tapping another
account's chip switches to it: the shown history follows that person, and the set is recorded
into their own visit. The chips show who records, so the button stays "Добавить".

Every list of machines draws a machine the same way: its photo, its name, its tags, then, for the
account's own machine, its comment and the day it was last used, and a trophy with its record — the
heaviest set with the most reps at that weight, the lightest on a gravitron — in the viewer's unit.
The machine list and the picker sort their machines by the chips at their top, drawn like the tags:
"Недавние", the last used first (the default), "А–Я", or "Частые", those used on the most visits
first; unused machines come last. The choice orders the account's machines and each friend's
section, and is the account's, saved in its profile and synced.
The machine picker lists the account's machines in that order under "Мои упражнения", then, signed
in and
online, one section per friend headed by their avatar and name, with the friend's machines that are
not yet the same machine as one of the account's. Opened from a visit, the machines the visit
already holds, with sets or planned, come last of all, dimmed and marked "Уже в визите"; picking
one opens its page with the form for its next set. A search keeps the machines whose name holds
what is typed; a cross at its end, shown while it holds text, empties it. The tags of all these machines follow the search field as chips, and the chosen
ones keep only the machines carrying every one of them. With nothing left, the picker says
"Ничего не найдено". "Создать «…»" opens the machine form pre-filled with the typed name and the
chosen tags ("С тегом «Руки»"). When a machine was opened in the visit, "Скопировать упражнение"
opens the form pre-filled from that machine, with the typed name instead of its own.

The machine form has no title. It collects a name, a comment (its setup note), how the weight is
counted ("Всего", "На сторону" or "Гравитрон", which explains that the weight counts as negative,
the less the better), the platform weight and whether it is added to the recorded weight, the unit
and the weight step. The empty name and comment fields show "Название" and "Комментарий" inside
them. The unit is kg, lb or an own unit such as "плитка", whose name is then written
after every weight on that machine, with no conversion. The weight step is any positive number,
typed in its field. Under the comment, every tag of the account's machines is a chip to tap on or
off, and a last "Новый тег" chip adds what is typed in it; a machine has any number of tags, and
they are the account's own: a machine taken from a friend starts without any. Signed in and online,
the tags of friends' machines the account has not got come between the account's tags and "Новый
тег", as dashed chips with the friend's avatar; tapping one gives it to the machine, and saving
makes it the account's own.

A set's total weight is its weight plus the platform when the platform is not added to the record.
When "Сохранить" changes that for a machine with recorded sets (for example, a platform typed in
after imported totals), "Пересчитать историю?" asks first and names the change, such as "−25 кг"
for "2 подхода". "Пересчитать" changes every recorded weight by it, so that the totals stay the
same, never below zero; "Оставить как есть" keeps the recorded weights. Both save the machine.

Under the name, the machine's photos run in a row, oldest first, then, signed in and online, the
photos of the friends' machines it is linked with, each with its owner's avatar; the row ends with
a camera tile, which offers "Снять фото" and "Из галереи". With no photo yet, the machines it is
linked with stand beside the tile. On Android they open the phone's camera and its photo picker; on
the web the first opens a phone's camera and the second picks a file, and a computer picks a file
for both. A star marks the photo standing for the machine in every list. Tapping a photo opens it
over the whole screen, with "Удалить" for an own photo and "Сделать основным" for any but the
starred one. New and removed photos and the chosen one are part of the form's edits: "Сохранить"
writes them, and leaving the form drops them. A photo is shrunk to at most 1600
pixels on its long edge before it is kept.

The form of a saved machine of the active account has "Привязать к…". It opens a list with a
search field, which starts with the machine's name: "Мои упражнения", the account's other machines, and, signed in and online,
"Упражнения друзей", one row per friends' machine that is not yet the same machine as one of the
account's. Choosing an own machine removes a duplicate. "Объединить упражнения?" shows both
machines under "Оставить", each with its set count and the day of its last set, and the user
chooses the one that stays. The machine used last is chosen first and marked "Рекомендуется" (a
machine with no sets counts as the oldest, and of two equal ones the edited one is marked):
imported history adds duplicates with older sets. The other one's sets move to the machine that
stays, and the other one disappears. When the two platforms differ (see below), a check box
"Пересчитать подходы «…» под платформу «…»: −5 кг", on at first, changes the moved weights so
that their totals stay the same. Friends who linked to
the removed machine are linked to the one that stays. The form of the machine that stays then
replaces the list, and unsaved edits in the old form are dropped. The other machine's photos move
to the one that stays. Choosing a friend's machine
links the two as one physical machine (see Group sharing) and returns to the form. Over the
friends' machines, "Скопировать настройки", off at first, also takes the chosen machine's weight
counting, platform, unit and weight step into the form; saving it then asks about recorded sets
as any other edit does.

The "Упражнения" row lists the active account's machines in the chosen order, each drawn as every
list draws it, with its photo: the one chosen in its form, else the machine's first photo, or, when
it has none, the first photo of a friend's machine linked with it. Tapping one opens it in the
machine form; "Добавить" adds one. Saving returns to the list. Signed in and online, the
friends' machines follow once the server answers: one section per friend, headed by their avatar
(or their initial in their calendar colour), name and machine count, one machine per physical
machine that is not yet the same machine as one of the account's. Tapping one opens "Упражнение
друга", the friend's machine to read: its name and owner, its tags, its photos with the one the
friend chose starred, its setup note, how its weight is counted and its platform in the machine's
own unit, then the
friend's statistics on it as the statistics screen shows a machine's — the period chips, the
period's best set, the chart and "Все результаты" — in the viewer's unit. Nothing on it can be
edited. "Взять себе" saves the account's own copy linked to it (see Group sharing) and opens that
copy in the machine form; a machine already linked to one of the account's, directly or through
friends' links, is shown without it. Offline that view says "Нет связи с сервером" and offers a
retry.

The "Визиты" row opens a calendar of the active account's visits, a month at a time. Days with a
visit are marked, today and the chosen day are highlighted, and future days cannot be chosen. Below
the month is the chosen day's visit as a card: the account's avatar, every tag its machines carry
("Спина · Грудь · Руки"), then the names of its machines in visit order in a small light font.
Tapping it opens it on the visit screen, where its sets are added, edited, deleted and ordered as
today's are. A day without a visit offers "Добавить визит". The card's "⋮" menu has "Перенести",
which moves the visit, with its sets, to the day tapped next; if that day already has a visit, the
app asks whether to replace it, and replacing removes it with its sets. Its "Сохранить как план"
opens the form of a new plan with the visit's machines, as the visit's own menu does. Its "Удалить"
asks "Удалить визит?" under the visit's date, then removes the visit and its sets from the history
and the statistics.

With a signed-in account and a network, the calendar also shows the visits of everyone sharing a
group with it: each friend who trained on a day adds a dot in that friend's colour after the
account's own dot, at most four dots a day. Below the chosen day's own visit is one card per friend
who trained that day, drawn as the own one — the friend's avatar, or their initial in their colour,
then, once read, the day's tags and machines; tapping it opens that friend's visit, read-only: the
friend's avatar and name in the top bar, the day and how many machines they did, then one row per
machine as on the visit screen — photo, name, tags, comment and results — without its sets; a
row opens "Упражнение друга" for that machine. Friends are read again on entering the calendar,
on changing the month and after switching accounts; without a network the calendar shows only
the account's own visits. Each friend gets a colour at
random at first, and the account can change it on the group screen. The colours are personal to the
account: friends never see them, and they follow the account to its other devices.

## Sign-in and accounts

Google is the only way in. On Android the app works without an account: everything recorded stays
on the device, and the first account to sign in takes ownership of it. Accounts added later start
empty. The web app shows nothing but the sign-in screen until an account is chosen.

Adding an account asks Google. Switching between accounts afterwards does not, and works with no
network — either from the avatar menu or, while recording, from the chips in the set form, so a
parent can record their own sets and their child's between one machine and the next.

Signing out of an account removes it from the device but keeps what it recorded, and signing back
in finds it again. On Android, signing out of the last account returns the app to working without
one. Friends stay locked until an account is signed in.

Settings has "Дети" while an account is signed in. It lists the children linked to the account,
each with their avatar, name and "Убрать", which asks "Убрать ребёнка?" before it ends the link.
"Добавить ребёнка" shows a new code, "Код для ребёнка: …", and on the web its link too, with
"Поделиться", which hands it over as a group invite is handed over. The child enters it on their
own device within a day, and a code works once; a new code replaces the last one. Family Link may
ask the parent once to let the child sign in to the app. Without a network the screen says "Нет
связи с сервером" and changes nothing.

A signed-in account's Settings also have "Родители": the account's parents, each with "Убрать",
which asks "Убрать родителя?" first, and under "Добавить родителя" a field for the code from a
parent and "Добавить". The screen says what a parent can do: record and see the account's
visits, machines, photos and plans, never its profile, measures or groups. An unknown or expired
code says "Код не найден или устарел", and the account's own code "Это ваш собственный код".
Linking and removing need the network, as on "Дети".

A parent's code shared from the web, or from an Android build that knows the web app's address,
carries a link to the web app. Opening it asks "Добавить родителя по приглашению?" before
linking, and a link opened while signed out is offered to whichever account signs in next in
that browser.

A linked child appears in each of their parents' account menus and among the set form's chips,
with their avatar, their name and "Ребёнок" under it, on Android and on the web; one child under
two parents signed in on one device appears once. Choosing the child records for them exactly as
for any account: the home card, the visit and its sets, the machines with their photos and links,
the calendar, the statistics and the plans are the child's, and they sync between the parent's
devices and the child's own. The child's profile, measures and groups stay the child's: while the
child is chosen, "Замеры" and "Друзья" leave the home screen, a screen of them that was open
closes, Settings keeps only the settings of the device, and sorting or grouping choices are not
saved. The friends' visits, machines, results and photos shown beside the child's are those of
the people the parent and the child share a group with, the parent included. A child has no
sign-out of its own: removing it in "Дети" takes it, with what was recorded for it, off the
parent's device, and the child keeps what has already synced. Signing a parent out takes their
children off that device's menu until the parent signs back in. A child signs in on their own
phone or browser with their own Google account, as anyone does.

Settings ends with "Дополнительно", a heading that opens, for the active account, a red "Удалить
аккаунт". Its question, "Удалить аккаунт?", asks to type DELETE, in any case, before "Удалить"
works; then it deletes the account and everything it recorded — visits, machines with their photos,
measures, profile and the groups it owns — from the server and, on Android, from the device, then
signs it out as signing out does. Without a network it says "Нет связи с сервером" and changes
nothing.

Settings has a "Профиль" section. One "Применить", under the theme and the transition length,
applies every change on the screen at once, the theme included; leaving with changes not applied
asks "Применить изменения?", with "Применить" and "Не применять". Signed in, the avatar sits
left of "Ник"; tapping it offers "Снять фото" and "Из галереи", as a machine's photo does, and,
once a photo is chosen, "Удалить", which brings the Google picture back. Its "Ник" field, for the
signed-in account, takes up to 40 characters; the Google account name is shown as a placeholder and
used when the field is left blank. Friends see this nickname instead of the Google name, and it
heads a visit shared as text. Пол (Мужской / Женский), Дата рождения (ДД.ММ.ГГГГ, from 1900 to
today) and Рост, см (50 to 250) feed the body fat formulas; they are there without an account too,
and an emptied field clears its value. Nobody else sees them.

"Единицы веса" in the same section, also there without an account, chooses how gym weights are
shown: "кг" (the default), "lb" or "Смешанные", where each machine shows its own unit. With "кг"
or "lb" every set, platform, step and last result on every screen, a friend's included, is shown
in that unit, converted as in a shared visit; a step converts to one decimal. An own unit is never
converted. The machine form keeps the machine's own unit, and body measures keep theirs.

## Body measures

Alongside the gym, the user tracks their body: weight and girths, typically once a week on a day of
their choice, and sees the body fat they imply. Measures are private: they sync between the
account's devices like its visits, but friends never see them, whatever groups they share.

Seven measures are ready from the start: Вес (кг), Талия, Грудь, Обхват бёдер, Бицепс, Окружность
бедра and Шея (см). Their names and units are the app's own and cannot be changed. The user adds
their own measures with a name and a free-text unit, and can rename them, change their unit or
delete them. A ready-made measure a body fat formula reads is marked with that formula's tag and
cannot be deleted; if it was deleted before, it comes back without its old values. Other
ready-made measures can be deleted and do not come back. Hips count as read only while the sex in
the profile is not "Мужской". Body fat is only ever calculated: an account's former typed body fat
measure is removed with all its values.

The "Замеры" row on the home screen lists the measures, each with its latest value and unit, the
change since the value before it ("−0.4", omitted when there is only one) and how long ago it was
taken ("вчера", "7 дней назад"), and the tags of the formulas that read it. "Порядок" shows drag
handles to reorder them. "Новый замер" opens
the measurement form for today; "Добавить показатель" asks for a name and a unit.

The measurement form ("Замер") is one day's values, one decimal field per measure with its unit,
the previous value shown as a placeholder. Under each ready-made measure's name a line says how to
take it, e.g. for Шея "Сразу под кадыком, лента чуть наклонена вперёд и вниз, шея расслаблена". The
day at the top is today by default; tapping it opens a month calendar where days with values are
marked and future days cannot be chosen. Choosing a day that has values loads them for editing.
"Сохранить" writes the changed values, and an emptied field clears that value. "Удалить замер", on
a day that has values, asks and then clears the whole day.

Under the list, "Процент жира" estimates body fat by three standard formulas, each row headed by
its tag: `NAVY` "ВМС США" (waist, neck and height, plus hips for a woman), `YMCA` (weight and
waist) and `BMI` "Дойренберг" (weight, height and age). Each uses the latest value of every
measure, and sex, birth date and height from the profile; age is counted in whole years to today.
A row shows its result ("18.4 %") or what it lacks ("Нужно: шея, рост"). While sex, birth date or
height is missing, a line under the rows leads to Settings.

Each measure row ends with a chevron; tapping it opens the measure. The screen starts with today's
value ("Сегодня, 29 сентября"): a decimal field between − and +, which step by 0.5 (from the latest
value when the field is empty), and "Сохранить". Then come its latest value and the change over
the chosen period, and a line chart of its values over 1 мес, 3 мес (the default), 6 мес, Год or
Всё. A period with a single value shows that value instead of a line.

"Редактировать историю" opens the measure's history ("Талия · история"): a month calendar with a
dot on every day that has a value; future days cannot be chosen. Tapping a day shows its value in
the same field with "Сохранить" and, when the day has a value, a trash button deleting it; an
empty day takes a new value. Back closes the history first. The menu in the top bar edits the name
and unit of the user's own measure, and deletes a measure that can be deleted with all its values
after a confirmation.

## Plans

A plan is a list of machines to do on a visit to come, with no sets. "Планы" on the home screen
lists the account's plans, oldest first: each by its name, or by its machines' names when it has
none, with how many machines it holds and a "Начать" button. "Планов пока нет" stands in for an
empty list, and "Новый план" makes one.

"Начать" asks "Начать план?", since a started plan cannot be taken back. Confirmed, it adds the
plan's machines to today's visit, creating it when there is none, deletes the plan and opens the
visit; back from it returns home. A machine the visit already has is not
added again. In the visit the added machines follow the ones with sets, each saying
"Запланировано"; tapping one opens its page, where "Добавить" opens the form for its first set
and "Убрать" takes it out of the visit. A machine with a set is listed like any
other. Ordering shows only the machines with sets. The shared text counts only machines with
sets. A started plan makes the day a visit even before its first set, so the
calendar marks the day and friends see the visit. Plans sync like visits and friends never see
them.

The plan form opens from a row or from "Новый план". "Название" takes up to 40 characters and
reads "Без названия" while empty. Below it are the plan's machines in order, each with its photo
and a remove button. The order button in the top bar shows a drag handle at the left of every
machine. "Добавить упражнение" opens the machine picker, which in a plan shows each machine's
last result and offers no "Скопировать упражнение"; a machine already in the plan is not added
twice. "Сохранить план" saves and returns to the list; a plan without machines cannot be saved.
Leaving without saving drops the edits. The menu of a saved plan has "Удалить план", which asks
"Удалить план?". Switching accounts returns to the list.

The menu in the top bar of a visit with machines has "Сохранить как план". It opens the form of a
new plan holding the visit's machines in the visit's order, planned ones included; saving returns
to the visit, which stays as it was.

## Custom exercsies

Someone may want to record non-machine exercises, like "Run 1km" where measure will be in minutes,
or "Doing some press" measured in times.

## Statistics

"Статистика" on the home screen opens the statistics on "Общая"; a machine's page opens them on
that machine. At the top a dropdown chooses "Общая" or one of the account's machines, listed by
name with their photos, and chips under it choose the period: "Месяц", "3 месяца", "6 месяцев" or
"Год". A period counts its months back from today, as the measure screen's do: on 1 October
"Месяц" starts on 1 September and "3 месяца" on 1 July. Neither choice is remembered.

A machine shows "Лучший подход · с 1 сентября" ("· противовес" before the date on a gravitron)
with the period's best set beside it, then a line chart of each day's best weight across the whole
period. A gravitron's weights are drawn negative, with zero on top. A period without sets says
"Нет подходов за период". "Все результаты" follows: every visit on the machine, newest first, its
date and its results written as the visit screen writes them, whatever the period.

"Общая" starts with sort chips drawn like the tags: "Недавние" (the default), "А–Я" and
"Частые", as in the machine list, and "Рост", the biggest weight gain in percent of the starting
weight first, then the most reps gained. The sort is not remembered. When a machine has tags,
"Группировать по тегам" follows, the same choice as the visit screen's: it splits the cards into
sections headed by their tags, as the visit does. The sort and the grouping order the dropdown's
machines too. "Только улучшения", on each time the screen opens, keeps only the cards with a green
chip; with none left, the list says "Нет улучшений за период".

Under "Упражнения за период · с 1 сентября" come the machines with a set in the period: each with
its photo, its name and two boxes joined by an arrow. With a set before the period they hold the
best set before it ("До 1 сентября") and the best in it ("С 1 сентября"); without one, the worst
and the best set of the period ("Начальный", "Лучший"). A chip sums up the change: in green
"+2.5 кг" when the weight grew, or "+2 повт." when the weight stayed and the reps grew; in grey
"−2.5 кг", "−1 повт." or "Без изменений". A machine first used in the period without a change
shows "Новое упражнение" in green. On a gravitron a lighter weight is the growth. Tapping a card
opens its machine. The best set is picked as the record is; the worst is the lightest weight with
the fewest reps at it, the heaviest on a gravitron. Weights follow the unit chosen in the profile.

With cards shown, "Общая" has an export button in the top bar. It hands the cards over as text, as
"Поделиться" hands a visit: the nickname and the period ("ГДМ, за месяц"), an empty line, then one
line per card with its name, its change and its best set in brackets ("Пресс сидя +3 повт. (45 кг
× 15)", "Бицепс в тренажёре — новое упражнение (32 кг × 12)"). Grouped by tags, the lines form the
visit's sections, separated by an empty line, each tagged one starting with a line of its tags.

## Data import / export

User should be able to import or export data in some predefined hardcoded format.

## Group sharing

Signed-in users form groups. The creator owns the group and invites others with a link to the
web app or with its eight-character code; the Android app joins by code. An invite link asks
before joining, warning that the group's members will see the user's visits and machines; a
link opened while signed out is offered to whichever account signs in next in that browser.
Every member sees the other members' calendars, visits and machine photos, read-only, and nothing
is shared outside a group. A member can leave; the owner deletes the group instead. Friends' data is read
online and never stored on the device.

The group screen starts each member's row with their avatar, or their initial in their colour, and
ends every row but the user's own with a colour dot. Tapping it opens a palette of eight colours;
the chosen one marks that friend's visits in the user's calendar and in the friend's calendar,
opened from the group.

A machine can be taken from a friend's list: the copy keeps the friend's settings and stays linked
to the friend's machine as one physical machine. An own machine can also be linked to a friend's
with "Привязать к…". Machines linked directly or through other friends' links are one machine: the
picker no longer offers the friend's machine beside the user's own, the machine's page shows each
friend's latest visit on it ("Олег  вчера · 80-85кг 8-6"), a friend's visit shows their results on
it under the user's own machine name, and the machine form lists the friends' machines it is linked
with, by owner and then by name ("Связано с: Жим ногами (Олег), Платформа (Паша)"). Tapping one
opens that friend's machine, as from the machine list. "Отвязать от друзей" in the form's menu,
after a confirmation, breaks every link between the machine and friends' machines, whichever side
made it.

Unlinking and, when signed in, merging duplicates change friends' links on the server, so they
need the network: without it the app says "Нет связи с сервером" and changes nothing.

## Privacy and terms

The Privacy Policy and the Terms of Service are separate pages published beside the web app
and linked from the footer of its entry page, readable without signing in. The footer shows on
the sign-in screen and the home screen only. Someone who can no longer sign in asks
for their account's deletion through the repository's issues.
