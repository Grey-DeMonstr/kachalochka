# Body fat from the profile — design

Body fat is calculated on its own from the latest measures and the profile, instead of on demand
inside the measurement form. The profile inputs move to Settings, and the measures the formulas
depend on are marked and protected.

## Profile

- Settings gains a "Профиль" section: Ник (signed in only), Пол (Мужской / Женский), Дата
  рождения (`ДД.ММ.ГГГГ`), Рост, см. One "Сохранить" saves the section; any field may be empty.
  A birth date lies between 1900-01-01 and today, a height between 50 and 250 cm.
- `Profile.birthYear: Int?` becomes `birthDate: CalendarDay?`. Age is whole years up to a day.
- Supabase `0011_profile_birth_date.sql` adds `birth_date date`, fills it with 1 January of
  `birth_year`, and drops `birth_year`. SQLDelight `8.sqm` rebuilds `profile` the same way and
  resets `lastPullAt`. A 1.0.3 client can no longer push a profile once `0011` is applied, so
  the migration ships with the release.

## Body fat on the measures screen

- Below the measure list, "Процент жира" lists every `BodyFatMethod` with its tag (`NAVY`,
  `YMCA`, `BMI`), its name and either the percent or "Нужно: …".
- Inputs are the latest value of each measure kind and the profile; age is counted to today.
- While sex, birth date or height is unset, a line under the section leads to Settings.
- The calculator sheet on the measurement form goes away with its view-model state.

## Tags and locking

- `formulaTags(kind, sex)` in `domain/measures` names the methods that read a kind: weight
  `YMCA`, `BMI`; waist `NAVY`, `YMCA`; neck `NAVY`; hips `NAVY` unless the sex is male.
- A tagged measure cannot be deleted. Loading the measures screen revives a tagged measure the
  owner deleted, dated now so the revival syncs; its old values stay deleted.
- A predefined measure always shows the app's name and unit (`Measure.title`, `Measure.unitName`)
  and has no rename. Untagged predefined measures can still be deleted.

## Names, fat measure, hints

- "Бёдра" reads "Обхват бёдер", "Бедро" reads "Окружность бедра".
- `BodyFat` is no longer seeded. On load a live `BodyFat` measure without values is deleted; one
  with values stays, named "Жир по весам или калиперу", and may be deleted.
- Each predefined kind has a one-line hint on how to measure it, shown under its field in the
  measurement form.

## Testing

Core: age, tags by sex, seeding and revival, the fat-measure cleanup, the profile wire shape,
`8.sqm`. App: the fat section and its Settings link, locking in the measure screen, the hints,
the Settings profile fields and their validation.
