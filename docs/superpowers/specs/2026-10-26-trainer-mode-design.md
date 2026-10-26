# Trainer mode — design

## Intent

A trainer makes workout plans for a trainee. The trainer opens the app only to make the plan; the
trainee then does the plan as a visit, as with any plan.

## Decided by the owner

- A trainer or a trainee is added the way a child or a parent is: under "Люди" → "Добавить".
- The trainer generates the code and the link, as a parent does for a child. The trainee must
  agree explicitly: typing the code and pressing "Добавить", or "Добавить" in the question an
  opened link asks.
- The trainer works through the avatar-menu switch, as a parent does for a child (design 10f):
  the trainee's data is synced to the trainer's device and shown in the normal screens.
- The trainer only sees the trainee's machines, visits and plans.
- The trainer makes plans and changes every plan of the trainee, not only their own.
- In a plan, the trainer sees and chooses only the trainee's machines, and can create new
  machines for the trainee.
- Comments on a plan and its machines, and results while planning, go to
  `docs/future_features.md` as "Requires Design".

## Built

- Server: `coaching` and `coaching_invite` tables and their definer functions, as for guardians
  (migration `0029`). Policies let a trainer read the trainee's gym rows, write their plans, and
  insert and update their machines and tags.
- Device: a trainee is a managed account with a trainee flag. Its sync pass pushes only
  machines, tags and plans and pulls no friend links; its visits are not normalized on the
  trainer's device.
- App: while a trainee is active, every child restriction applies (no profile, measures, groups
  or friends), and the screens hide recording, changing visits, starting plans and photos. An
  existing machine of the trainee opens read-only. The People screen has the groups "Тренер" and
  "Ученики" and the choices "Тренера" and "Ученика"; the choices carry no hints.

## Owner's answers after the first build

- An existing machine opens read-only for the trainer.
- The trainer sees the trainee's statistics.
- The home card starts no visit for the trainer.
- The add panel shows no hints: the design's hints were left there by mistake.
- No "Открыть тренировки", "Приглашён" badge or avatar-menu headings.
- "Тренер" stays a singular heading, and the menu item stays "Убрать".
- A person who changes between child and trainee may lose records not yet synced.
- Two invitation questions at once are fixed later.


