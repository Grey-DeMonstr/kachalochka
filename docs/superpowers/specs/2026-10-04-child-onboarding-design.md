# Child accounts — design

**Date:** 2026-10-04

A child trains with their parents and has a phone of their own. The child signs in with their
own Google account on their own device and records and syncs their own visits. Each parent, with
their own account and device, links the child once by a code; from then on the child appears in
the parent's account menu, and the parent records on the child's behalf — machines, visits, sets,
photos, links and plans — with the same fast flow the set form's account chips give today. The
parent's app does this under the parent's own session, because Google's Family Link keeps the
child's Google account off the parent's phone.

---

## 1. The constraint, checked

### 1.1 How an account is added today

"Добавить аккаунт" runs `Accounts.addAccount`: `GoogleSignIn.signIn()` obtains a Google ID token
— through Credential Manager's `GetSignInWithGoogleOption` on Android, whose sheet lists the
Google accounts present on the device, through a redirect to Google on the web — and exchanges it
for a Supabase session, which is stored beside the other accounts and becomes the active one.
The session is the only credential the app keeps; every switch reuses it, and the sync pass
refreshes each account's token on its own (technical spec §4.2, §4.3).

### 1.2 On the parent's phone

Family Link does not let a supervised child account be added to an Android device that already
has another Google account; the system answers "Can't add account. This Google Account is managed
with Family Link", and Google's remedies are to remove every other account or to make a separate
Android user for the child (Google For Families Help, "Sign your child in on an Android device",
support.google.com/families/answer/7158477). Credential Manager can only offer what the device
holds, so the child's account is never signed in on the parent's phone.

### 1.3 On the child's own device

A supervised account can sign in to a third-party app with Google:

- Google's developer documentation states that "the Sign in with Google button supports Google
  Accounts with Family Link" ("Sign in with Google features",
  developers.google.com/identity/siwg/features).
- Family Link lets the parent decide how: under Controls → Account settings → "Controls for
  third-party apps" the parent selects the level of control — approve each request, or let the
  child sign in to apps that ask for little data — and reviews or removes access under "Manage
  third-party app access" ("Third-party sites & apps with access to your child's account",
  support.google.com/families/answer/9204736). A supervised teen's supervision is optional and
  ends with the parent's approval before 18 (support.google.com/families/answer/9055704).
- The app asks only for the ID token's basic profile, the lowest category.

So the child signs in on their own phone or browser as any user does. The one-time step in the
child's onboarding is the parent allowing the app in Family Link, once per child.

### 1.4 What the backend offers

No custom server code, as technical spec §1 keeps it: Postgres row-level security and
`security definer` functions through PostgREST, the pattern of `create_group`, `join_group`,
`break_machine_links`, `delete_my_account` and `machine_peaks`. Everything below is tables,
policies and such functions.

---

## 2. Decisions

- **The app is for users aged 13 and over.** The privacy policy and the terms say so; nothing in
  the app treats a child differently from any user. A guardian link is a relation between two
  ordinary accounts.
- **A guardian records on the child's behalf.** A linked child is a *managed account* on the
  parent's device: it has no session; the parent's session acts for it. The guardian's scope is
  the gym and the plans — `machine`, `visit`, `workout_set`, `machine_link`, `photo`, the photos'
  bytes and `workout_plan`. The child's `profile`, `measure`, `measurement` and groups stay the
  child's alone and are hidden on the parent's device while the child is selected.
- **Parent and child see each other through a group, as today.** The link gives the child no
  view of the parent; a parent who wants the child to see their visits shares a group with them.
- **One linking path.** The parent shows a code, the child enters it on their own device. A
  second parent links with a code of their own. A code works once and expires after 24 hours.
- **Either side ends a link.** One function serves both: a child withdraws from a guardian in
  "Родители", a parent drops a child in "Дети". Both lists exist anyway for the code, so this
  adds nothing but a button, and a user of 13 and over acts for themselves. The child's rows
  leave the parent's device with the link, since that device can never sync them again.
- **Shared friends only.** While a managed child is active on the parent's device, the friends'
  overlays show the people the parent and the child share a group with, and nobody else.

---

## 3. The link

### 3.1 Server

Migration `0023_guardians.sql`:

```
create table public.guardian (
    child_id    uuid        not null references auth.users (id) on delete cascade,
    guardian_id uuid        not null references auth.users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    primary key (child_id, guardian_id),
    check (child_id <> guardian_id)
);

create table public.guardian_invite (
    code        text        primary key default public.new_invite_code(),
    guardian_id uuid        not null references auth.users (id) on delete cascade,
    expires_at  timestamptz not null default now() + interval '24 hours'
);
```

The parent shows a code and the child types it, never the other way round: whoever holds a
*parent's* code can only put themselves under that parent's care, and the parent sees the new
child at once and can remove it. A *child's* code would let its holder become the guardian of
someone else's data.

- `offer_guardianship()`, as the parent: deletes the caller's earlier invites, inserts one and
  returns its code.
- `accept_guardian(code)`, as the child: reads the code as `join_group` does (any case,
  surrounding whitespace dropped), refuses an unknown or expired code with `PT404` and the
  caller's own with `P0001`, inserts the `guardian` row, deletes the invite and returns the
  guardian's id. Single use: a second device of the child, or a slip, asks the parent for a new
  code.
- `end_guardianship(child, guardian)`: deletes the row when the caller is either party.
- `my_family()`, security definer: the caller's children and guardians, each with
  `member_display_name`, `member_avatar_photo` and `google_picture`, the functions `group_member`
  already uses, since profiles stay private.
- `guards(other uuid)`, security definer, stable: the caller is a guardian of `other`. Every
  policy of §4.1 calls it.
- `guardian` is readable by both parties (`select` to `authenticated`); `guardian_invite` has no
  grants, since only the functions touch it. Deleting either account cascades through both.

### 3.2 Screens

- **Parent:** Settings → "Дети". Each child from `my_family` with avatar and name and "Убрать",
  which asks first. "Добавить ребёнка" shows the code and, on the web, its link, with
  "Поделиться" as a group invite has, and explains that the child enters it on their own device
  and that Family Link may ask the parent to allow the app once.
- **Child:** Settings → "Родители". Each guardian with "Убрать", which asks first, and "Добавить
  родителя": a field for the code and "Добавить". The screen explains what a guardian can do:
  record the child's visits, machines, photos and plans, and see them, and not the profile, the
  measures or the groups.
- The web's invite link carries the code as `?parent=CODE`, handled as `?join=` is: asked about
  before accepting, kept for whichever account signs in next in that browser.
- Adding and removing need the network, as group changes do: offline the screens say "Нет связи
  с сервером" and change nothing.

---

## 4. Recording on the child's behalf

### 4.1 Server

Migration `0024_guardian_writes.sql`:

- `machine`, `visit`, `workout_set`, `machine_link`, `photo` and `workout_plan`: the `select`
  policies admit `public.guards(user_id)` for live and deleted rows alike, since the parent's
  pull syncs the child's rows as it syncs its own; the `insert` and `update` policies admit
  `(select auth.uid()) = user_id or public.guards(user_id)`, and the checks that a set's visit
  and machine, and a photo's machine or profile, belong to the writer compare against the row's
  `user_id` instead of `auth.uid()`.
- The `photos` bucket: write, read and delete when the first folder of the object's name is the
  caller's id or a child's they guard.
- `break_machine_links` and `repoint_machine_links` accept a caller who guards the machine's
  owner, so a merge or an unlink made for the child from the parent's device works.
- `machine_peaks(owners)` already takes owners and runs under the invoker's policies.

### 4.2 Identity

- `Account` gains `kind`: `Google`, or `Managed` with `guardianId`. A managed entry has a name,
  a picture and an avatar photo from `my_family`, and no e-mail; the menu prints "Ребёнок" under
  the name.
- The account store lists managed entries beside the sessions. `activeId` may name one;
  `sessionOf(child)` is null. `Accounts.switchTo(child)` activates the guardian's session and
  moves the active id; `resumeActiveAccount` on the web does the same at start. A managed entry
  has no sign-out; "Дети" removes it.
- `AccountTokens.tokenFor(child)` answers with the guardian's token. The sync pass covers managed
  owners after the sessions: push under the guardian's token, pull `owned(child)` with the
  child's own watermark (`syncState` gets a row per managed owner). The child's own device
  writes the same rows, and the two converge as two devices of one account do (technical spec
  §4.2: the device's outbox wins, the server otherwise).
- Managed entries follow the link. `my_family` is read for every Google account on the device
  after sign-in, at the start of each sync pass and on entering "Дети"; an entry whose link is
  gone is removed from the device with its rows (`OwnedRowsPurge`), a new one is added, and the
  same child under two parents on one device is one entry.

### 4.3 Screens while a managed child is active

- The avatar menu and the set form's chips list managed children with their avatars; switching
  is as today, and `SetRecorder` works unchanged: it stamps the active owner and mirrors a machine
  per owner, and the mirrored copy is a child-owned machine the policies admit.
- The home card, the visit, the machines, the machine form, the calendar, the statistics and
  the plans work as for any account; the plan form's picker and "Начать" write the child's rows.
- "Друзья" and "Замеры" rows, the profile section of Settings and "Дополнительно" are not shown;
  Settings keeps the device settings. The top bar's avatar is the child's.
- Friends' overlays — calendar dots and cards, the picker's and the machine list's friends'
  sections, friends' results and photos on a machine, "Привязать к…" — show the people the parent
  and the child share a group with. The parent's `GroupsCache` already holds the parent's groups
  with their members, so the shared friends are the members of the parent's groups that contain
  the child; the overlays are read with the parent's token as now and filtered by that set.
  `machineLinks` and photos of those friends come with them, as today.

### 4.4 Tests

- `core`: `tokenFor` of a managed owner returns the guardian's token; the pass pushes a managed
  owner's rows under it and pulls with the owner's watermark; an entry gone from `my_family` is
  removed with its rows; a child under two parents on one device is one entry; store JSON with
  the new fields reads and the old still does.
- `app`: "Дети" shows the code and the list and removes a child; "Родители" accepts a code,
  reports an unknown one and removes a guardian; the menu and chips list a managed child;
  recording and a plan while it is active stamp the child; hidden rows and sections are absent;
  the shared-friends filter keeps only members of groups containing the child.

---

## 5. Specs and documents to update

- `functional_spec.md`: "Sign-in and accounts" gains the family — the parent's code, "Дети" and
  "Родители", what a guardian records and sees, what is hidden, the shared friends rule, and
  that a child signs in on their own device with Google after the parent allows the app once in
  Family Link. The requirements say the app is for users aged 13 and over.
- `technical_spec.md`: §4.3 managed accounts and token resolution; §4.2 the pass over managed
  owners; §5.2 `guardian`, `guardian_invite`, `guards`, the functions, the widened policies and
  the bucket.
- `privacy.html` and `terms.html`, with a new effective date: the app is for users aged 13 and
  over; a guardian link, made by a code the account holder enters, lets the guardian see and
  record the account's workouts, machines, photos and plans and nothing else, and either side
  ends it. The "Children" section is replaced accordingly.
