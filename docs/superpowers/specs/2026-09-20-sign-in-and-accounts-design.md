# Sign-in, multiple accounts and the sync pass — design

**Date:** 2026-09-20

Google sign-in, several accounts live on one device, and the Android sync pass that finally
drains the outbox. Drawn from the Claude Design project "Gym visit screen options" (Nocturne),
turn 5:

| Frame | Screen |
|---|---|
| 5c | Android first launch — no account, sign-in at the bottom, Друзья locked |
| 5e | Web — sign-in is mandatory, one centred button |
| 5f | Меню аватара — accounts, Добавить аккаунт, Настройки, Выйти |
| 5g | Быстрый переключатель — person chips inside the set sheet |

Google is the only way in. Android runs without an account; the web does not. Both 5f and 5g are
built, and both drive the same active account.

The same pass removes visit start time and duration from the app, and replaces the settings gear
in the top bar with the avatar on every screen.

---

## 1. Scope

Built:

- Google sign-in on both targets. Optional on Android, required on web.
- Several accounts signed in at once on one device, with one active at a time.
- Switching from the avatar menu (5f) and from the set sheet (5g).
- Sign-out per account; the last sign-out returns Android to anonymous use.
- The avatar replacing the gear in the top bar, with Настройки inside its menu.
- The Android sync pass: push the outbox, pull by watermark, for every added account.
- Removal of visit start time and duration.

Not built: the group newsfeed and Друзья, which stay locked; photo sync (§4.4 of the technical
spec), since photos are not built; account avatars as images — the design draws monograms.

---

## 2. What the design decides

Four questions the frames leave open, settled before implementation:

1. **Both 5f and 5g are built**, and both call the same switch. The sheet chips are a one-tap
   shortcut for what the avatar menu does in two.
2. **Several sessions stay live; one is active.** A switch changes which, and needs neither the
   network nor the Google account picker. Re-authenticating on every chip tap would be unusable
   between two sets.
3. **Each account has its own visit.** A set saved as Миша lands in Миша's visit, created then
   if she has none. Every row keeps exactly one owner, so row-level security, sync and opening
   the data on the other person's own phone all work unchanged. The alternative — one shared
   visit holding sets owned by different people — leaves each guest's set pointing at a parent
   they cannot read.
4. **The first account to sign in claims the ownerless rows.** Accounts added afterwards start
   empty. This is technical spec §4.3 extended by the one sentence several accounts need.

The web build behaves exactly as Android does, multiple accounts included. The avatar menu and
the sheet chips are shared Compose code; keeping behaviour uniform avoids a platform branch
through the middle of the UI. Only the Google flow itself differs.

---

## 3. Identity

In `core/data/identity`, not `core/domain` — the store exposes a `StateFlow`, and `domain/` may
not import kotlinx.coroutines (technical spec §2).

```
Account       userId, email, displayName
AccountStore  accounts: StateFlow<List<Account>>
              activeId: StateFlow<UserId?>
              add(session) / switch(UserId) / remove(UserId)
```

`switch` persists the new active id and calls `auth.importSession`. There is one `SupabaseClient`
for the UI and one live session on it, swapped on every switch. `Auth` is installed with
`autoLoadFromStorage` and `autoSaveToStorage` off: supabase-kt's own store holds a single session
and would fight this one for the same slot.

`CurrentUser` collapses to one `commonMain` implementation reading `AccountStore.activeId`.
`LocalCurrentUser` and `SupabaseCurrentUser` both go away, and §4.3's "null until sign-in" becomes
true on both targets for the same reason instead of two.

The store persists per platform behind one interface, as the theme mode does (§10): DataStore on
Android, `localStorage` on web, in memory on the JVM for tests. A `UserSession` is
`@Serializable`, so an account is one JSON string.

### 3.1 Signing in

`GoogleSignIn` is an interface in `core/data/identity`, bound per target and faked in tests.

- **Android** obtains a Google ID token through Credential Manager and exchanges it with
  `auth.signInWith(IDToken) { provider = Google }`. Credential Manager needs an **Activity** for
  its sheet, not the Application, so the implementation takes a context supplier the Activity
  installs. This is the one seam where identity reaches into the Android lifecycle.
- **Web** calls `auth.signInWith(Google)`, which leaves the page. Adding an account therefore has
  to record that an add is in flight *before* redirecting, and on return exchange the code for a
  session and file it as a new account. A sign-in that survives a full page reload is the
  difference between the web flow working and looping.

**Supabase is often unconfigured**, and that is normal (§5.4). Today nothing on Android touches
the client, so a checkout without credentials still runs. Once Android can sign in, the client
must stay lazily resolved and the sign-in button hidden when credentials are absent — otherwise
the anonymous app of 5c cannot launch on a fresh clone.

---

## 4. Scoping reads by owner

One local SQLite file now holds rows for several `user_id`s. Every repository read therefore takes
the owner explicitly:

```
VisitRepository.active(owner: UserId?)
MachineRepository.recent(owner: UserId?)
```

Set reads stay scoped by their visit, which is already owned; a machine read is scoped by owner
because machines belong to whoever created them.

The owner is a parameter rather than something the repository reads from `CurrentUser` itself.
A hidden dependency would let a test that forgets to set the active account return an empty list
instead of failing, and it would scope twice on web, where row-level security already filters.
Both implementations scope identically, which is what technical spec §3 asks of the seam.

`active(owner)` is the load-bearing one: it is what makes each account's visit its own.

**A switch never creates a visit.** Saving a set as Миша creates hers if she has none. Creating
one on the switch itself would leave an empty visit behind every time a chip is tapped by
mistake.

### 4.1 Machines are mirrored per account

Machines are owned too, so switching inside the set sheet lands on a machine the new account does
not have. Saving resolves the active account's own machine of that name, creating it from the
open machine's settings when absent. Each person keeps their own machine list and their own
statistics, and the copy is made once and reused afterwards.

Sharing one machine row across accounts instead would leave each guest's set pointing at a
machine row that row-level security hides from them — the orphan case §2 rejects for visits, and
it is worse here, because the machine carries the name the set is displayed under.

---

## 5. Screens

The gear in the top bar becomes the avatar, on every screen. `Screen(...)` loses
`onOpenSettings`, since Настройки now lives inside the avatar menu.

- **Avatar menu (5f)** — a shared composable reading `AccountStore` through `koinInject()` and
  `collectAsState()`, the pattern `RestTimer` already uses. Accounts under «Пишем подходы в»,
  then Добавить аккаунт, Настройки, and Выйти из аккаунта «имя».
- **Sheet chips (5g)** — the same accounts, one tap, plus a Добавить аккаунт chip. The previous
  results shown above the steppers and the Сохранить button both follow the selected person.
- **Android with no account (5c)** — Друзья locked, Войти через Google at the bottom of the home
  screen.
- **Web with no account (5e)** — the centred sign-in screen stands in place of the `NavHost`; no
  route is reachable until a session exists.

Avatars are letter monograms, as drawn. No image loading is introduced.

---

## 6. Visit time

Visit start time and duration are not recorded, displayed or stored.

`Visit.startedAt` becomes `recordedAt`, renamed in `Visit.sq` and in a Postgres migration in the
same commit (§5.1). It keeps the visit ordered, grouped by day and available to statistics, and
nothing renders it. `endedAt` stays: it is what makes a visit active.

The top bar reads `Визит`, the home card loses its elapsed time and its ticker, and
`formatElapsed` goes with them.

---

## 7. The sync pass

Android only. The web target writes Supabase directly and has no outbox.

The algorithm is technical spec §4.2 — push the outbox, then pull by watermark, `machine`,
`visit` and `profile` before `workout_set`, last-write-wins on `updated_at`, failures left
enqueued. `profile` syncs alongside the gym tables: an account's display data is just another
row an outbox entry can name. Several owners change three things.

### 7.1 Every account syncs, not only the active one

A pass iterates every added account. Pushing only the active one would leave a guest's sets in
the outbox until somebody happened to switch back to them, which in the case 5g exists for could
be days.

### 7.2 Two clients, so sync never disturbs the UI

```
UI client     auth session = the active account        never touched by sync
sync client   accessToken = { the token of the account this pass is on }
```

The sync client installs no `Auth`; its `accessToken` resolver is handed a token by the store. The
active account lends the UI client's own token instead of refreshing a second one for itself.
Tokens for accounts that are not active are kept fresh with `auth.refreshSession(refreshToken)`
on the UI client — an API call that returns a new session and leaves the current one alone.

This is what lets a background pass push Миша's sets while Иван is on screen without swapping
Иван's session out from under him. Importing each session in turn on the one client would race
every write the UI makes meanwhile.

### 7.3 A watermark per account

`syncState` holds one `lastPullAt` today. It becomes keyed by `user_id`, so each account's pull
asks for rows newer than its own watermark; row-level security scopes the result by the token
used. The outbox needs no owner column — every row it names already carries one, and a row with
no owner never enters it (§4.3).

### 7.4 Triggers

App start, connectivity gain and the end of a visit, as §4.2 states. The pass is a WorkManager
job with a network constraint, so connectivity gain is the constraint firing rather than a
listener the app maintains.

---

## 8. Tests

Host tests only; nothing here needs a device, an emulator or a browser. `GoogleSignIn` and the
account store are faked, and `runScreenTest` gains an account fixture.

In `:core:jvmTest`:

- The first sign-in claims every ownerless row; a second account added afterwards owns nothing.
- Each account resolves its own active visit; a switch followed by a save records the set under
  the account that was switched to.
- Signing out keeps that account's local rows; the last sign-out leaves Android anonymous.
- Push order holds across owners, a failed push stays enqueued, watermarks advance per account
  independently, and a pull skips rows with a pending outbox entry.

In `:app:jvmTest`:

- The avatar menu lists the accounts and marks the active one; Настройки opens from it.
- The sheet chips switch the account the Сохранить button saves as.
- Android with no account shows the sign-in button and a locked Друзья; web with no account
  shows the sign-in screen instead of any route.
- No screen renders an elapsed time.

---

## 9. Order of work

One spec, because sync cannot be designed without knowing who owns what. Two phases, because one
commit changing identity, every repository read, every screen's top bar and the sync engine at
once is not reviewable:

1. Identity, scoped reads, the screens, and the visit-time removal. `./gradlew check` passes.
2. The sync pass on top.
