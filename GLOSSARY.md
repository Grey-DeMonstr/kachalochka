# Kachalochka

An app to record gym results, share them with friends and see statistics. These are the words
for the domain. Use them in code, specs, commits and talks with the user. _UI_ gives the word the
Russian app shows for a term; the full texts stay in `RuStrings.kt`.

## People and accounts

**Account**:
One Google sign-in. It keeps its own visits, machines, plans, measures and history.
_UI_: Аккаунт
_Avoid_: user (when one account of several is meant), login

**Active account**:
The account that the app records into now. A device can hold several accounts, but only one is
active at a time.
_Avoid_: current user, selected account

**Anonymous owner**:
The person who records on Android when no account is signed in. The first account that signs in
takes their rows.
_Avoid_: guest, local user

**Profile**:
The personal data of an account: nickname, avatar, sex, birth date, height and preferred unit. It
syncs with the account.
_UI_: Профиль
_Avoid_: settings (device settings do not sync)

**Parent**:
An account that records for a child and sees the child's gym data.
_UI_: Родитель
_Avoid_: guardian

**Child**:
An account that a parent records for. Its profile, measures and groups stay its own.
_UI_: Ребёнок
_Avoid_: dependent, sub-account

**Trainer**:
An account that sees a trainee's machines, visits and plans, and writes the trainee's plans. A
trainer records no sets for a trainee.
_UI_: Тренер
_Avoid_: coach

**Trainee**:
An account that a trainer writes plans for.
_UI_: Ученик
_Avoid_: student, client

**Managed account**:
A child or a trainee: an account that another account on the device acts for.

**Group**:
Accounts that see each other's calendars, visits and machines, read-only. Its owner makes it, and
the others join with a code or a link.
_UI_: Группа
_Avoid_: team, circle

**Friend**:
Another member of a group that holds the account.
_UI_: Друг
_Avoid_: mate, group mate, member (when the account itself is not meant)

**Friend colour**:
The colour that an account gives a friend in its calendar and feed. The friend never sees it.
_UI_: Цвет в календаре

## Gym

**Machine**:
An exercise that an account records sets on, usually one gym machine, with its own settings.
_UI_: Упражнение
_Avoid_: exercise, equipment

**Setup note**:
The comment of a machine, for example the seat position.
_UI_: Комментарий
_Avoid_: description

**Platform weight**:
The weight of the machine itself, such as a sled or a bar. It is added to each set's weight or is
already part of it.
_UI_: Вес платформы
_Avoid_: base weight, default weight

**Weight mode**:
How a machine counts its weight: total, per side or counterweight.
_UI_: Как считается вес

**Per side**:
A weight mode where the weight is for one hand or one leg.
_UI_: На сторону
_Avoid_: per hand, per limb

**Counterweight**:
A weight mode where a smaller weight is progress, as on a gravitron.
_UI_: Гравитрон
_Avoid_: negative weight

**Weight step**:
The amount that one tap of − or + changes a machine's weight by.
_UI_: Шаг веса

**Own unit**:
A unit of a machine that is not kg or lb, for example "плитка". It is never converted.
_UI_: Своя единица
_Avoid_: custom unit

**Preferred unit**:
The unit the profile shows all gym weights in: kg, lb or mixed, where each machine keeps its unit.
_UI_: Единицы веса

**Tag**:
A label on a machine, for example "Ноги", that groups machines in lists and in a visit.
_UI_: Тег
_Avoid_: category, label

**Visit**:
One calendar day of training of one account. It has no start or end. Its first set or a started
plan makes it, and an account has at most one visit on a day.
_UI_: Визит
_Avoid_: workout, session, training

**Set**:
One try on a machine: a weight, the reps and an optional comment.
_UI_: Подход
_Avoid_: approach, rep

**Planned machine**:
A machine in a visit that has no set yet.
_UI_: Запланировано

**Plan**:
A list of machines for a visit to come, with no sets. A start puts its machines into today's visit
as planned machines and deletes the plan.
_UI_: План
_Avoid_: program, routine, template

**Pinned plan**:
A plan that stays after a start.
_UI_: Закреплён

**Record**:
The best set ever on a machine: the heaviest weight with the most reps at it, or the lightest on a
counterweight machine.
_UI_: Рекорд
_Avoid_: max, peak, personal best

**Day's best**:
The best set on a machine on one day, with how many sets of that day equal it.

**Prior best**:
The greatest day's best on a machine before a given day.
_Avoid_: previous best, last result

**First day**:
The first day an account trains on a machine. It has no prior best.
_UI_: Новое

**Gain**:
How much a day's best beats the prior best: more weight, more reps at the same weight, or more
sets at the same best set.
_Avoid_: progress, improvement

**Visit card**:
A visit in short form: its tags, then each machine with its day's best and gain.

**Feed**:
The visit cards of the account and its friends, newest day first.
_UI_: Лента
_Avoid_: timeline, history

## Shared machines

**Own machine**:
A machine of the active account.

**Friend's machine**:
A machine of a friend. The account can see it but not change it.
_UI_: Упражнение друга

**Link**:
An account's statement that one of its machines and a friend's machine are the same physical
machine.
_UI_: Привязать к…
_Avoid_: binding, connection

**Cluster**:
All machines that links join, directly or through other links. A cluster is one physical machine.
_Avoid_: linked group

**Copy**:
An own machine made from a friend's machine. It keeps the friend's settings and tags and is linked
to the friend's machine.
_UI_: Взять себе
_Avoid_: clone, import

**Stand-in**:
The own machine that stands for a shown machine: the machine itself when it is own, else the own
machine of its cluster, else a new copy.

**Merge**:
The join of two own machines into one. The sets of the removed machine move to the kept one.
_UI_: Объединить
_Avoid_: combine

## Body

**Measure**:
A body value that an account tracks over time, for example "Талия" in cm. It is ready-made or the
account's own.
_UI_: Показатель
_Avoid_: metric, parameter

**Measurement**:
The values of the measures on one day.
_UI_: Замер
_Avoid_: measure (for one value), entry

**Body fat**:
A value that a formula calculates from measures. Nobody types it.
_UI_: Процент жира
