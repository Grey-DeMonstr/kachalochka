# Visit screen rework, machine tags, settings and English — design

Thirteen changes from the backlog, in three parts. Each change is its own commit, test first, with
`.\gradlew check` clean; the two specs are updated in the same commit as the code they describe.

- **Part A — the visit screen.** Reps carry on like the weight. Every visit result uses the shared
  format. Set rows read "#1", "#2" and show their comment. A machine's header takes two lines. The
  set sheet closes completely and a "+" row adds a set to any expanded machine. Photos show in the
  visit and the sheet, and tapping one opens the machine form. Sets take comments. "Гравитрон"
  becomes a third weight mode. The Russian text says "упражнение" for a machine.
- **Part B — tags.** A machine has one personal tag, shown beside its name in the visit. "Group by
  tag", kept per account, splits the visit and its shared text into sections.
- **Part C — settings and English.** One "Применить" saves every setting; theme and language are
  chosen like the weight unit; deleting the account hides under "Дополнительно" and asks for
  DELETE. The app speaks English as well as Russian, following the system until the user picks
  one.

Order of work: A, B, C. The English translation comes last, so it translates the strings the other
changes add.

## 1. Decisions

- **Reps carry on.** The first set on a machine starts from the previous visit's first set; every
  later one repeats the weight and the reps of the set just recorded.
- **One results format.** `setsSummary` writes every multi-set result: the visit's machine rows, a
  friend's visit, friends' lines in the sheet, the sheet's previous-visit line and the shared text.
  A single set (a set row, the home card, the picker) keeps `setValue`.
- **The sheet is open or gone.** No collapsed bar. Swiping it down, back on the phone or in the top
  bar closes it and leaves any edit. It opens from a set row (to edit), from the "+" row of an
  expanded machine (to add), and from the picker after "Новое упражнение". After a save it stays
  open for the next set, as now.
- **Thumbnails open the form.** Each machine in the visit and the sheet's header show the machine's
  cover photo, or the barbell icon without one. Tapping it opens the machine form of the active
  account's own copy, as the sheet's "Настройки" does.
- **Comments belong to a set.** A new synced column, `workout_set.comment`, empty by default. They
  show on screen only; the shared text never carries them.
- **"Гравитрон" is a weight mode.** `WeightMode.Counterweight`, wire name `counterweight`, which
  the server's check constraint still accepts: no migration. Clients before this release read it as
  `total`, as they already do.
- **"Тренажёр" becomes "упражнение" in Russian only.** Every Russian label, with its grammar
  ("Новое упражнение", "3 упражнения"). The English text says "machine"; code and docs keep
  `Machine`.
- **One tag per machine.** `machine.tag`, empty for none, personal: a copy taken from a friend
  starts without one. Sections follow the visit order of their first machine; untagged machines
  come last, without a header.
- **Grouping is per account.** `profile.group_by_tag`, synced, false by default.
- **Settings apply together.** Theme, language and transition length become drafts saved by the
  same button as the profile. Leaving with unapplied changes asks first.
- **Our own strings layer.** A `Strings` interface with a Russian and an English implementation in
  Kotlin, plural rules in code. Compose resource files would leave view models and the shared text,
  which build most of the app's strings, without a language, and switching at runtime is awkward on
  Wasm.
- **The language is per device**, like the theme: System (the default), English or Русский. System
  means Russian when the device's language is Russian, else English. The shared text follows the
  app's language. User data — machine names, own units, tags, comments — is never translated.

## 2. Part A — the visit screen

### 2.1 Reps carry on

`suggestNextSet`: with sets already on the machine in this visit, the next set repeats the last
one's weight and reps; otherwise the previous visit's first set, else the machine's default.

### 2.2 The previous-visit line

The sheet's line becomes "Вчера · 20-25кг 3x10": the days-ago label and `setsSummary` of the
previous visit's sets. `shortSet` has no other caller and goes.

### 2.3 Set rows

A set row shows "#N", then its comment in the muted colour on one line with an ellipsis, then the
set's value at the right. A friend's visit shows its rows the same way.

### 2.4 Machine header

The header's first line holds the thumbnail, the machine's title (name and platform, the only
bright text) and, at the right, its setup note, muted, one line with an ellipsis; a long name wraps
and the note gives way first. The second line is the results summary, muted, wrapping as needed.

### 2.5 The sheet and the "+" row

`VisitViewModel` drops `sheetExpanded`, `expandSheet` and the peek: `closeSheet()` clears the open
machine and any edit, and returns false when nothing was open, so back falls through to leaving the
screen. An expanded machine ends with a row "+ Добавить подход" (`add-set-<id>`), hidden while
ordering, which calls `selectMachine`. The list dims while the sheet is open, as now.

### 2.6 Photos and the machine form

`VisitViewModel` reads own photos (`PhotoRepository.all`) on every reload, and group mates'
(`FriendsRepository.groupPhotos`) together with the friends' results, best-effort; `coverPhoto`
picks each machine's. `SetGroupUi` and `SheetUi` carry the `Photo?`. A shared `MachineThumbnail`
composable draws the photo or the icon, used by the picker, the list and the visit. Its tap calls
`openMachineSettings(id)`, which now takes the machine to open.

### 2.7 Comments

- `WorkoutSet.comment: String`, in SQLDelight (`11.sqm`, `ALTER TABLE ... DEFAULT ''`) and in
  migration `0016_set_comment.sql`; `GymWire`, the sync gateway and the friends' set read carry it.
- "Комментарий" in the sheet shows a text field under the steppers, prefilled while editing. The
  comment is saved with the set; after a save the next set starts without one. Trimmed, at most
  200 characters.
- The functional spec's "Comments on a set are shown on these screens but not yet available" goes.

### 2.8 Гравитрон

- The machine form offers "Всего", "На сторону" and "Гравитрон". With "Гравитрон" chosen, a hint:
  "Вес считается отрицательным: чем меньше, тем лучше."
- `setsSummary` writes such weights after "(-)": "Подтягивания в гравитроне
  (-)27-25-22.5-22.5-22.5кг 10-8-6-6-6"; `setValue` writes "(-)27 кг × 10". The stepper keeps positive numbers; its caption
  names the mode "гравитрон".
- Statistics are not built yet; when they are, a smaller weight on such a machine is progress.

### 2.9 "Упражнения"

Every Russian label naming a machine is reworded, with the tests that assert them: "Упражнения",
"Новое упражнение", "Сохранить упражнение", "Скопировать упражнение", "Мои упражнения",
"Упражнения друзей", "Упражнение друга", "Объединить упражнения?", `machineCount` → "1 упражнение,
2 упражнения, 5 упражнений". Russian quoted in the specs follows.

## 3. Part B — tags

- `Machine.tag: String`, trimmed, empty for none: `machine.tag` in SQLDelight (`12.sqm`) and in
  migration `0017_machine_tag.sql`, with `profile.group_by_tag boolean not null default false`.
  `Profile.groupByTag` maps it. `linkedCopy` clears the tag; a merge keeps the kept machine's.
- The machine form has a "Тег" field under the setup note, and below it one chip per distinct tag
  of the account's live machines, sorted; tapping one fills the field.
- The visit shows a machine's tag after its title, muted, in a small outlined label.
- A "Группировать по тегам" checkbox (`group-by-tag`) sits in the row with the set count. It writes
  the profile at once and requests a sync. When on and some machine has a tag, the list is split
  into sections, each headed by its tag, untagged machines last and unheaded. "Порядок" shows the
  plain list while ordering, since a drag moves machines in the visit order.
- `visitShareText` takes the grouping: sections separated by an empty line, each tagged one headed
  by its tag.

```
ГДМ, чт

Ноги
Жим ногами (+76кг) 20-20-30кг 3x10

Руки
Бицепс 14кг 3x12
Трицепс 5lb 4x10

Пресс сидя 41кг 10-15-15
```

## 4. Part C — settings and English

### 4.1 Settings

- Order: "Профиль" (nickname, sex, birth date, height, weight units), "Тема" (a `ChoiceRow`:
  Системная / Светлая / Тёмная), "Язык" (a `ChoiceRow`: Системный / English / Русский), "Анимация
  переходов", then "Применить" (`apply-settings`), enabled while anything differs from what is
  saved. It writes the profile, when changed, and the three device preferences.
- `SettingsViewModel` owns the whole draft; it reads the theme, language and transition
  preferences instead of `App` passing them in.
- Back, on the phone or in the top bar, with unapplied changes asks "Применить изменения?":
  "Применить" applies and leaves, "Не применять" leaves, dismissing stays.
- "Дополнительно" (`settings-advanced`) is a collapsed header at the bottom, shown while an account
  is signed in; expanded it holds "Удалить аккаунт" in the error colour. Its dialog asks to type
  DELETE; "Удалить" is enabled once the field holds that word, ignoring case and surrounding
  spaces.

### 4.2 Strings

- `ui/strings`: `interface Strings` with a property or function per text, `RuStrings` and
  `EnStrings`, and `plural(n, forms)` rules per language. Formatting functions (`Formats.kt`,
  `ShareText.kt`, `WeightUnits.kt`, measure formats) take a `Strings`.
- `AppLanguage { System, English, Russian }` and `LanguagePreference`, stored as the theme is:
  DataStore on Android, `localStorage` on the web, in memory on the JVM. `systemLanguage()` is
  expect/actual: the default `Locale` on Android and the JVM, `navigator.language` on the web.
- `AppStrings` (Koin single) exposes `StateFlow<Strings>` from the preference and the system
  language. Screens read `LocalStrings`, provided in `App`; view models inject `AppStrings` and
  publish again when it changes.
- Predefined measures, body-fat formula names and measuring hints are shown by kind from `Strings`;
  the stored names stay as they are. Units follow: "kg", "lb", "cm".
- Host tests keep Russian: the test modules bind `AppStrings` to Russian, so existing assertions
  stand. New tests cover English formatting, plurals and switching language on a live screen.

## 5. Specs to update

- `docs/functional_spec.md`: the visit screen and the sheet (2.1–2.8), the Russian labels (2.9),
  tags and the shared text (3), settings (4.1), languages under Requirements (4.2).
- `docs/technical_spec.md` §4.5: `comment`, `tag`, `counterweight`; the profile's `group_by_tag`;
  a new section on strings and languages beside §10 Theming.

## 6. Release

Migrations `0016` and `0017` must be pushed with `supabase db push` before the release is tagged:
until then the web and the sync pass fail on the unknown columns. Clients before this release keep
working, since PostgREST updates only the columns an upsert sends.
