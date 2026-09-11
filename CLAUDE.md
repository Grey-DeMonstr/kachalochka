# Kachalochka

An Android / Web app to save sport results, share them with friends and collect statistics. The
functional spec lives in `docs/functional_spec.md`.

## Agent skills

### Issue tracker

Issues and specs live as local markdown files under `.scratch/<feature-slug>/`.
See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, each label string equal to its name (`needs-triage`, `needs-info`,
`ready-for-agent`, `ready-for-human`, `wontfix`), recorded as a `Status:` line in each issue file.
See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

# Generic code conventions

These apply to **every file in the repository**, whatever the language, as well commit messages and
pull request descriptions.

- Wrap at **99 columns**: comment text, like code, and Markdown documents and pull request
  descriptions too. clang-format shortens a long comment line but never widens a short one, so a
  comment wrapped at 80 stays wrong forever.
- Keep comments **as short and clear as possible**. Avoid long texts. If a comment needs a
  paragraph, the code usually needs a better name instead.
- **Never write about what was not done or what does not exist** — no absences ("deliberately not
  listed here", "left at its default"), no changes ("as it used to", ticket keys, commit hashes),
  no rejected alternatives. See *What deserves a comment* section below.

## What deserves a comment

- **Comment why, never what.** If the comment can be derived by reading the line below it, delete
    it. A declaration whose name already says what it is needs no comment at all - silence is the
    correct amount of documentation for an obvious thing.
- **Do not document what the code does not do.** No "X is deliberately not listed here", "there is
    no matching Y", "Z is left at its default", "no DEPENDS on W because...". An absence cannot be
    misread, because there is nothing there to read; explaining it only creates a comment that will
    rot. The rare exception is a default whose value something else depends on - then state the
    dependency in one line, not the reasoning behind mentioning it.
- **Comment the code, not the change.** No "as it used to", "now that", "previously", "this used to
    live in". No issue keys, commit hashes, or implementation-plan step numbers - that history goes
    in the commit message, where it stays accurate.
- **Length is a signal.** More than ~6 lines of comment above one declaration means the rationale
    belongs in a design doc under `docs/`, or the code needs a clearer name. Do not repeat the same
    rationale in two places; put it where the reader will be standing when it matters.
