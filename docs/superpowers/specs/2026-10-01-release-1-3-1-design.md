# Release 1.3.1 — design

The design project's turn 7 ("Сейчас → предложение") reworks most screens from the owner's
comments, and two more requests join it: reps are typed like the weight, and the web page's
footer shows only on the main page.

## 1. Decisions

- **A machine of the visit gets its own page.** Tapping a machine row, or picking a machine,
  opens a full page for it inside the visit screen; the bottom sheet goes away. The set form
  slides up over that page.
- **"Добавить" keeps the form open** for the next set, refilled with the suggestion, so a set is
  one tap as before. "Отмена" closes it. Saving an edit closes it.
- **A picked or created machine opens with the form already open**; one opened from the list
  opens with it closed.
- **Weights keep the app's own format everywhere** — "(-)29.5 кг × 10", "3x10" — so every screen
  still reads like the shared text. The form's stepper is the one exception the owner asked for:
  a gravitron's weight shows as "−65 lb".
- **The footer shows on the home screen and on the web sign-in screen**, the page Google's OAuth
  review looks at.
- **Friends see avatars.** An account's avatar is the photo it chose in Settings, else its Google
  picture, else its initial. Friends read both through the group.

## 2. Home

The today card holds the full date ("Вторник, 29 сентября 2026") and one button: "Начать" when
today has no visit, "Продолжить" when it has one, planned machines included. The counts and the
last set leave the card.

## 3. Visit list

- The top bar has no title; the day ("Вторник, 29 сентября") heads the list on its own row, for
  today too.
- A machine row is its photo, then the name with its platform and tags, the machine's comment
  (the setup note) as a grey line, and the results as a second grey line. The results wrap
  between the weights and the reps when they do not fit one line.
- Tapping a row opens the machine page; rows no longer expand. Ordering still shows every set
  with its handle.
- "Новое упражнение" becomes "Добавить".

## 4. Machine page

The top bar holds back, a gear opening the machine form, the timer and the avatar. Below:

- The cover photo (tapping it also opens the form), the name with the platform suffix, the tags
  as chips and the machine's comment, read-only.
- Two cards: "Рекорд", the best set ever on the machine (§6), and the previous visit's results
  headed by how long ago it was ("12 дней назад"). A card without data is left out.
- One line per friend's latest visit on the machine: avatar, name, "2 дня назад · results".
- "Сегодня · 3 подхода" (another day: "12 ноября · 3 подхода"), then the day's sets on the machine
  as rows "#1  (-)29.5 кг × 10", each with its comment; tapping a row opens it in the form.
- "Добавить" opens the form for the next set. A planned machine without sets also offers
  "Убрать", which takes it out of the visit and returns to the list.

Back, from the top bar or the phone, closes an open form first, then returns to the list.

## 5. Set form

- With more than one account signed in and a new set, the person chips head the form.
- "#4 Новый подход", or "#3 Правка: (-)29.5 кг × 10" when editing.
- The weight stepper shows the value and the machine's unit ("65 lb"); a gravitron's value has a
  "−" before it. When the shown unit differs, the converted weight follows under it, larger and
  brighter than a caption ("29.5 кг"); the mode and step captions go. With one line it is
  centred.
- The reps stepper takes typed reps too; anything but a whole number above zero disables saving.
- A multi-line "Комментарий" field is always there, up to 200 characters.
- Buttons: a trash icon when editing, "Отмена", and "Добавить" or "Сохранить".

## 6. Machine card and records

One `MachineCard` draws a machine in the picker, the machine list and the friends' sections:
photo or barbell, name, tags as chips, then for an own machine its comment, and a last line with
the last day it was used (own machines only) and a trophy with the record in the viewer's unit.

The record is the heaviest set, the lightest on a gravitron, with the most reps at that weight.
`machinePeaks` in `domain/gym` reduces a machine's sets to its heaviest and lightest weights with
their best reps and its last set's instant; `MachinePeaks.best(mode)` picks the record. Android
reduces the own sets locally; the web and friends' machines ask the server's `machine_peaks`,
which returns the same reduction per machine, so the row cap never truncates a history.

## 7. Machine picker

- Tag chips under the search field, every tag of the own and the offered friends' machines; the
  chosen ones filter both, a machine needing all of them.
- The search keeps only machines whose name matches; nothing matching shows "Ничего не найдено".
- "Создать «…»" says "С тегом «Руки»" when tags are chosen, and the new machine starts with them.
- Own machines, then one section per friend headed by avatar and name, every row a
  `MachineCard`.

## 8. Machine list

`MachineCard` rows, then one section per friend with avatar, name and machine count. "Добавить"
replaces "Новое упражнение".

## 9. Machine form

- The add-photo tile is only a larger camera icon; "Связано с: …" sits beside the photos.
- "Заметка о настройке" is renamed "Комментарий".
- Tags: own tags as chips and a "Новый тег" chip field that adds on done. Under them, "Теги друзей
  — добавятся к вашим после выбора": friends' tags the account lacks, as dashed chips with the
  friend's avatar; tapping one adds it to the machine, and saving makes it the account's own.
- The platform weight loses "Своя масса снаряда"; the save button reads "Сохранить".

## 10. Friend's visit

The top bar shows the friend's avatar and name; the list is headed by the date and the machine
count, and each machine is a visit-list row (§3) without sets.

## 11. Calendar

Own and friends' visits are identical cards: avatar, then the day's tags joined with " · ", then
the machine names in a small light font. The own card has a "⋮" menu with "Перенести" and
"Удалить"; the delete question shows only the date and a red "Удалить". A friend's avatar
without a picture is their initial in their calendar colour.

## 12. Avatars

- `Account` gains the Google picture URL, read from the session's `avatar_url` or `picture`
  claim and stored with the account.
- `profile.avatar_photo` names a photo the owner chose. Its bytes travel as any photo's: a
  `photo` row whose `machine_id` is the profile's id, so sync, storage and deletion need nothing
  new. Settings: the avatar sits left of "Ник"; tapping it offers "Сделать фото", "Выбрать из
  галереи" and "Удалить", which returns to the Google picture. The change applies with the rest
  of the screen.
- `group_member` gains `avatar_photo` and `picture_url`, kept current by the trigger that already
  renames members and by one on member rows, so friends read both with the member list.
- `Friend` and `GroupMember` carry an `Avatar`: the chosen photo, else the Google picture, else
  none. `PersonAvatar` draws it, or the initial.
- Coil loads Google pictures through `coil-network-ktor3`; googleusercontent answers CORS for the
  web.
- Migration `0019_avatars.sql` and `14.sqm` add the columns; `14.sqm` resets `lastPullAt`.

## 13. Group screen

Each member row starts with the avatar (or initial) and ends with the colour dot.

## 14. Measures

- The list's measure rows end with a chevron.
- The measure screen opens with today's value: a decimal field between − and +, stepping by 0.5,
  and "Сохранить". The chart follows as before; the value list is replaced by "Редактировать
  историю".
- The history page is a month calendar with dots on days with a value. Tapping a day shows its
  value in the same field with "Сохранить" and, for a day with a value, a trash button.

## 15. Web footer

`App` reports whether the home screen or the sign-in screen is shown; the web binding shows the
footer only then.

## 16. Testing

Test first throughout: `machinePeaks` and `best`, strict ranking with tags, the stepper's reps
typing, the machine page and form (open, add keeping the form, edit, delete, cancel, back, unplan,
chips), the new home card, the visit rows, the picker's tags and empty state, the machine cards,
the form's friends' tags, avatars in the wire mappings and `accountFrom`, the calendar card menu,
the measure value and history, and the footer's visibility.
