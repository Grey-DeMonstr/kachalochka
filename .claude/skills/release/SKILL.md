---
name: release
description: Use when cutting a release of Kachalochka - the user asks to release, cut a version, ship a new version, or tag and push a release.
---

# Release

## Overview

A release is one commit that adds a `changelog.txt` entry, one annotated `vX.Y.Z`
tag, and a push. The version lives in the tag alone - there is no version field
to bump. Pushing the tag is what builds and publishes the app, so it happens
last and only with the user's explicit approval.

Development is Windows-native: use **PowerShell**, never Bash.

## Workflow

Create a todo for each step and work through them in order.

### 1. Check the ground

```powershell
git status --short                # must be clean
git rev-parse --abbrev-ref HEAD   # must be master
git describe --tags --abbrev=0    # the previous release tag
```

A dirty tree or a branch other than `master` stops the release - tell the user
and wait.

### 2. Ask for the version

List what has landed since the previous tag:

```powershell
git log --no-merges --format='%h %s%n%b' <previous-tag>..HEAD
```

Then **ask the user which version to cut**, with a suggestion: patch (`0.2.1` ->
`0.2.2`) when everything since the last tag is a fix, minor (`0.2.1` -> `0.3.0`)
when there is anything a user would call a new feature. Never pick the number
silently.

### 3. Draft the changelog entry

Add a new section at the **top** of `changelog.txt`, under the title, in the
existing shape:

```
X.Y.Z

- One user-visible change, in plain language.
- Fixed: one user-visible fix.
```

Most bullets fit on one line. A second line is the exception, not the shape to
aim for.

Blank line between the version number and its bullets, two blank lines between
sections, wrap at 99 characters with a two-space continuation indent, plain
ASCII - no markdown.

**Writing rules:**

- Write what a user of the app would notice, not what the code does. If nobody
  using the app could tell the difference, it does not belong in the file.
- Say it once for both platforms. Name Android or the web only when the change
  reaches one of them and not the other.
- Skip commits entirely: CI, tests, refactors, dependency bumps, migrations,
  spec and doc edits.
- One user-facing change is one bullet even when it took five commits. Five
  unrelated changes in one commit are five bullets.
- No file names, class names, package names, commit prefixes (`feat:`, `fix:`),
  commit hashes, or issue numbers.
- Prefix fixes with `Fixed:`. Everything else is a plain statement of what the
  app now does.
- **Never write a real e-mail address, Supabase project URL or filesystem
  path.** These are personal data.
- **One short sentence per bullet.** Name the change and where to find it, then
  stop. A second sentence needs a reason to exist.
- Say *what* changed, never *how* it works or *why* it was done. No mechanism,
  no rationale, no "instead of", no list of the cases covered. The commit
  message already explains itself to whoever needs that.
- A fix must be recognisable to someone who hit it. If you cannot describe the
  symptom in a few words a user would use, the fix is internal - leave it out.

Good:

```
- Exercise history. Every set you have logged for an exercise, on its own page.
- The workout screen is completely redesigned.
- Fixed: a workout logged offline no longer disappears after signing in.
```

Too long - mechanism, scope and rationale a user did not ask for:

```
- You can now see the history of an exercise. A History row opens a page that reads every set
  recorded for that exercise from the local database rather than from the server, so it works
  offline. Results arrive newest first and can be filtered by date.
```

### 4. Show it and wait

Print the drafted entry to the user and **stop for approval**. Apply any edits
they ask for and show it again. Nothing is committed before they approve.

### 5. Verify

```powershell
.\gradlew check
```

Must be clean before committing.

### 6. Commit and tag

```powershell
git add changelog.txt
git commit -m "Add the changelog for X.Y.Z"
git tag -a vX.Y.Z -m "vX.Y.Z"
```

The tag is local at this point and can still be deleted (`git tag -d vX.Y.Z`).

### 7. Ask, then push

**Ask the user to approve the push.** Once approved:

```powershell
git push origin master
git push origin vX.Y.Z
```

**Order matters.** The tagged commit must be on `origin/master` before the tag
arrives, or the release is cut from a commit nobody can see. Pushing the tag
starts the signed build and creates the GitHub Release - both irreversible.

## Quick reference

| | |
|---|---|
| Changelog file | `changelog.txt`, repo root, newest version at the top |
| Version source of truth | the `vX.Y.Z` tag; the build takes `versionName` from it |
| Release notes | the version's section of `changelog.txt`, read by `release.yml` |
| Quality gate | `.\gradlew check` |
| Tag format | `vX.Y.Z`, annotated |
| Push order | `master` first, then the tag |

## Common mistakes

- **Tagging without a changelog section.** `release.yml` reads the release notes
  out of `changelog.txt` and fails a version that has none.
- **Pushing the tag before master.** Push `master` first.
- **Committing before the user has seen the changelog.** Draft, show, wait.
- **Copying commit subjects into the changelog.** GitHub already generates its
  release notes from commit subjects; `changelog.txt` exists to say the same
  thing in a user's language.
- **Listing chores.** A CI fix, a migration or a spec update is not a change to
  the app.
- **Using Bash.** `gradlew` needs Windows paths and a Windows JDK.
