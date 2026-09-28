# Release 1.1.0 — design

Three changes: machine photos from the camera or the gallery, the app version on the home screen,
and deleting an account from inside the app (backlog issue 22). Each is implemented and committed
on its own, in the order of the sections below, and updates `functional_spec.md` and
`technical_spec.md` in the same commit as the code. The release follows the `release` skill.

## 1. Machine photos

A machine has any number of photos. Both platforms add them from the camera or from the gallery
or files, and show them; friends see the photos of machines in their groups.

### Behaviour

- The machine form's photo tile becomes a row: the photos as 100 dp thumbnails, oldest first,
  then the tile. The tile reads "Добавить фото" and opens a menu with "Снять фото" and "Из
  галереи". On Android they are the system camera and photo picker. On the web both open a file
  input; "Снять фото" adds `capture`, so a phone's browser opens its camera.
- The machine list and the picker show each machine's cover in the row's image slot: its own
  first photo, else the first photo of a machine linked with it, so a friend's photo stands for
  a machine the account never photographed.
- Photos taken in the form belong to its edits: they are written with "Сохранить тренажёр",
  for a new machine as for a saved one, and dropped with the form.
- Tapping a thumbnail opens the photo over the whole screen. The form's viewer has "Удалить";
  removal is an edit too and is applied on save.
- "Тренажёр друга" shows the friend's photos under the owner line, read-only, and opens them the
  same way.
- Merging two own machines moves the removed machine's photos to the one that stays.

### Data

- `photo` is a new synced table in both schemas: `id`, `user_id`, `machine_id`, `taken_at`,
  `updated_at`, `deleted`. `machine_id` is not a foreign key, as on `machine_link`. Photos of a
  machine sort by `(taken_at, id)`. Group mates read live photos through `shares_group_with`, as
  machines.
- `10.sqm` creates the table and resets `lastPullAt`, so a device updated after another one
  already uploaded photos still pulls them.
- The bytes are a JPEG in the private `photos` Storage bucket at `<user_id>/<photo_id>`. Storage
  policies let the owner write, read and delete their own folder, and group mates read it.
- Every photo is shrunk before it is stored: the long edge to at most 1600 px, upright, JPEG
  quality 85. Android reads the EXIF orientation; the web draws on a canvas, which applies it.

### Code

- `domain/gym/Photo.kt`: `PhotoId`, `Photo` and `PhotoRepository` (`add(photo, jpeg)`,
  `upsert(photo)`, `forMachine(machine)`).
- `PhotoFiles` (`core/data/gym`) keeps the bytes on the device: Android under
  `filesDir/photos/<id>.jpg`, the test JVM in memory, the web nowhere.
- `LocalPhotoRepository` writes the file and the row, enqueueing the row; a deleted photo's file
  is removed. `RemotePhotoRepository` uploads, then upserts the row.
- `PhotoImages` answers the bytes of any photo, own or a friend's: the local file when there is
  one, else a download from Storage as the active account. On Android a downloaded own photo is
  kept as its file.
- The sync pass pushes `photo` after `machine_link` and before `workout_set`: a live row uploads
  its file first, when the device has it; a deleted row removes the Storage object first. A
  pulled deleted row removes the local file. The sync client installs Storage.
- `FriendsRepository.photos(machine)` reads a friend's live photos of one machine, and
  `groupPhotos(viewer)` every group mate's in one read for the list and the picker.
  `PhotoRepository.all(owner)` reads the account's own; the pure `coverPhoto(machine, photos,
  clusters)` picks each row's photo.
- The app displays photos with Coil 3 through a fetcher for `Photo` that asks `PhotoImages`,
  keyed by the photo id for the memory cache.
- `PhotoCapture` (app) is bound per platform: Android's registers `TakePicture` and
  `PickVisualMedia` launchers in the composition and hands back the shrunk JPEG; the web's opens
  a file input and shrinks on a canvas; the test JVM delivers a fixed picture. The Android camera
  writes into the cache through a `FileProvider` declared by `androidApp`. The manifest must not
  declare the camera permission, or the system camera would require it.

### Tests

Domain and DAO tests for `Photo` and `LocalPhotoRepository`; a migration test for `10.sqm`; sync
tests for push order, upload before the row, Storage removal for a deleted row and the local
file removed on a pulled deletion; `MachineFormViewModel` and screen tests for adding, removing
and saving photos, and for a form that drops them; a friend-machine screen test for its photos;
a merge test for moved photos.

## 2. Version on the home screen

The home screen ends with a muted "Версия X.Y.Z" line, on both platforms.

- `app` generates `AppVersion.NAME` at build time: the `versionName` Gradle property when the
  release build passes one, else the newest version heading in `changelog.txt`. The web deploys
  from every push to `master` and has no tag, so the changelog is the one source both platforms
  share; a release commit puts its version at the top before the tag is pushed.

Tests: a home screen test finds the line with `AppVersion.NAME`.

## 3. Delete the account (issue 22)

Settings, for a signed-in account, ends with "Удалить аккаунт".

- It asks "Удалить аккаунт?": everything the account recorded is deleted from the server and from
  this device, and it cannot be undone. Confirming deletes; without a network the screen says
  "Нет связи с сервером" and nothing changes.
- The deletion removes the account's photos from Storage, then calls the security-definer
  function `delete_my_account()`, which deletes the caller from `auth.users`; every owned table
  cascades from it, groups the account owns go with their members. Then, on Android, the
  account's rows, photo files and pull watermark are deleted from the device. Finally the account
  is signed out, as "Выйти" does.
- `AccountDeletion` (`core/data/identity`) does the three steps against a client that acts as the
  active account; `OwnedRowsPurge` removes the local rows: SQL on Android, nothing on the web.
- The privacy policy describes deletion from the app, replacing the issue-based request.

Tests: view-model and screen tests for the confirmation, success and offline paths; a DAO test
for the purge leaving other owners' rows alone.
