# Pyramid

Design option 12a of the "Pyramid mode options" design, with these changes from the user.

## Intent

A user does pyramids: sets whose weight steps down (40, 31, 22 kg) or up, often several in a row.
The app records them as usual sets, but shows them and writes them as groups.

## Data

- `workout_set.pyramid`, boolean, false by default, in SQLite and in Supabase. True marks the set
  that starts a pyramid.
- A pyramid is its start set and the sets after it, in visit order, up to the next start. Sets
  before the first start are alone. A pyramid has no end mark, so it cannot be "not finished".
- Grouping is derived from the order, so a reorder or an edit of the flag changes the pyramids.

## Set form

- A checkbox row above the comment, with the pyramid icon. A tap on the full row toggles it.
- New set, no pyramid on the machine in the visit: "Начать пирамиду". Checking changes nothing
  else.
- New set, a pyramid exists: "Начать новую пирамиду". Checking sets the weight and reps of the
  current pyramid's first set. Clearing it does not restore the earlier values.
- Edit: "Начать пирамиду", bound to the set's flag. It does not change the values.
- The checkbox is clear by default for a new set.

## Set list on the machine page

- A pyramid is one row: "#4–6", the icon, its best set, a caret. A tap opens or closes it.
- Open: its sets follow, indented, with a line at their left. A tap on a set edits it.
- The last pyramid on the machine is open by default, the others are closed.
- In reorder mode the list is flat, and a start set shows the pyramid icon.

## Icon

Static, three bars that go down, as the collapsed row of option 12b4. It does not follow the
weights.

## Summaries

Without a pyramid nothing changes. With one, each pyramid and each set outside a pyramid is a
group: values in a group are joined with "+", groups with " - ", and equal groups are written once:
"40+31+22 - 36+27+18 кг 2x(10+10+10)", "50 - 40+31+22 кг 12 - 10+10+10".

## Not included

No "not finished" mark, no hint about where the reset weight comes from, no automatic check of the
box.
