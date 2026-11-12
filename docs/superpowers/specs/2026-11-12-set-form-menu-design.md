# Set form menu

## Intent

The set form shows only what each set needs: the weight and the reps. The options that are used
less often move into a menu, and the row of people shows only the people who train today.

## Layout

From the top of the form:

1. The drag handle.
2. The people row: the avatar chips at the left, the "⋮" button at the right. The row always
   shows.
3. The title: "#4 New set" or "#3 Edit: 70 kg × 10". While "Pyramid" is checked, the pyramid icon
   follows the title.
4. The weight and the reps.
5. The comment field, only while it shows (see _Comment_).
6. The buttons, without a change.

The check box row of the pyramid above the comment goes away.

## People row

- The chip of the active account always shows, with the name.
- When the form adds a set to today's visit, the chips of the other accounts that can record
  follow it (trainees do not count), but only of the accounts that have a visit today. A visit
  that has only planned machines counts.
- With "Show everyone" checked, all the other accounts that can record show.
- On an edit, or on a day that is not today, only the chip of the active account shows.
- A tap on another chip makes that account active, as before.
- The "+" chip that adds an account goes away. The avatar menu still adds an account.
- The row scrolls sideways under the "⋮" button when the chips do not fit.

## Menu

"⋮" opens a dropdown menu of check box rows. A tap on a row changes it and closes the menu.

- **"Start a pyramid" / "Start a new pyramid"**, with the pyramid icon. It always shows. It does
  the same as the check box row before: the same two texts, the same reset of the weight and the
  reps. The state belongs to the set.
- **"Comment"**. It always shows. It shows or hides the comment field. The state is a device
  setting.
- **"Show everyone"**. It shows when the form adds a set to today's visit and two or more
  accounts can record. It shows all accounts in the people row. The state stays in the memory of
  the app.

## Comment

- "Comment" is a device setting: one value for all machines and all accounts on the device. It
  does not sync. It is clear on a new install, thus the field starts hidden.
- The field also shows while the set has comment text. Thus an edit of a set with a comment
  always shows it, and the text is never lost when "Comment" is cleared.

## Show everyone

- The value stays in the memory of the app. It is clear after each start of the app.
- It is one value for all visits and machines.
- An account that gets its first set of the day through "Show everyone" has a visit after that,
  thus its chip stays when the box is cleared.

## Not included

No change to the avatar menu, to the set values, or to how a pyramid groups its sets.
