---
name: dispatch-tasks
description: Work through the backlog in ISSUES.local.md as a dispatcher, then fix the strong architecture findings, actualize the specs and cut a release.
disable-model-invocation: true
---

# Dispatch Tasks

You are the **dispatcher**. You decide who does each piece of work, and you verify the result.
A report of a subagent is a claim. The diff and the gate are the evidence.

The run has four phases in this order.

## 1. Backlog

### Triage

Read `ISSUES.local.md`. The issues under a heading for future features are not part of the
backlog. For each other issue, read the code that it changes and write down:

- the files and screens that it changes;
- its size: **small** (one area, the fix is clear) or **large**;
- if the user must decide something. The user decides when the issue asks for a design or an
  approval, or when two readings of its text give behaviour that the user sees as different.
  For a difference that the user does not see, take the simplest reading.

**Ask early.** A design is a short text spec in `docs/superpowers/specs/`: behaviour, layout
and state. Prepare each design that an issue asks for before all other work. Show the designs
and all your questions to the user in one message. Then continue with the issues that have no
question while the user reads. Start an issue with a question only after the user answers.
Commit a design when the user approves it.

### Decide who does the work

Use the first row that applies:

| Issue | Who |
|---|---|
| Small | You, on `master` |
| Large, and the design is not clear | Your own model |
| Large, and the design is clear | You write the plan. A `model: sonnet` subagent does it |

One subagent works directly on `master`, and you wait for it. Run subagents in parallel
(`isolation: worktree`) only when two or more large issues change different files. Three
subagents at a time is the limit: each one runs its own Gradle build. A worktree starts with a
cold build, and that costs more than a small issue.

These files cause conflicts between parallel issues: the string resources, the navigation
graph, the Koin modules, the SQLDelight schema, `supabase/migrations/`, the two specs and
`GLOSSARY.md`. Issues that change the same screen are not parallel.

### Brief a subagent

A subagent sees `CLAUDE.md`. It does not see your context, and a worktree has no git-ignored
files: `ISSUES.local.md`, `CLAUDE.local.md`, the plans in `docs/superpowers/plans/`. Put these
parts in the brief:

1. The full text of the issue, word for word, and the answers of the user.
2. The rules of `CLAUDE.local.md` for code and text.
3. The full plan, and the path of the committed design.
4. The files to start from, and what is out of scope.
5. The completion criterion:
   - a failing test first for new behaviour; for a refactor, the tests that exist stay green;
   - the two specs and `GLOSSARY.md` agree with the change;
   - the gate is green;
   - one commit with a product-oriented title;
   - a report that lists what is not done.

### Verify each issue

1. Read the full diff against the issue. Each sentence of the issue has its change in the
   diff. If not, correct it, or send the subagent back with `SendMessage`.
2. For a worktree: `git cherry-pick` the commit to `master`, then remove the worktree and its
   branch.
3. Run the gate on `master`:

   ```powershell
   .\gradlew check
   .\gradlew :core:compileKotlinWasmJs :app:compileKotlinWasmJs   # when commonMain changed
   ```

4. Remove the issue from `ISSUES.local.md`.

*Done when:* each issue of the backlog is one commit on `master` with a green gate and is not
in the file, or it waits for an answer of the user.

### Read the backlog again

The user adds issues while you work. Read `ISSUES.local.md` again. If it has a new issue, go
back to *Triage*. If an issue waits for the user, ask and stop: phase 2 starts only when the
file has no issue outside the future features.

## 2. Architecture

`improve-codebase-architecture` of `mattpocock-skills` is user-invoked, and the Skill tool
refuses it. Find its file with the glob
`~/.claude/plugins/cache/**/improve-codebase-architecture/SKILL.md`, and do its steps 1 and 2.

Its step 2 ends with a question to the user, and step 3 is a grilling session. In place of
these, the candidates with the `Strong` badge are your work list. Decide the open design
questions yourself: the simplest approach wins. Then do each candidate as a large issue, from
*Decide who does the work* to *Verify each issue*.

*Done when:* each `Strong` candidate is a commit on `master` with a green gate. If the report
has no `Strong` candidate, the phase is done.

## 3. Documents

Run the `occurra:actualize-doc` skill one time for each of `docs/functional_spec.md` and
`docs/technical_spec.md`. The two runs change different files, thus two parallel subagents can
do them. A subagent puts its questions in its report and continues. Read the two diffs, then
commit them as one commit.

## 4. Release

Run the `release` skill. The user asked for this release when they started `dispatch-tasks`.
Thus the release rules of `CLAUDE.local.md` apply to each stop of the `release` skill. If the
run added a file to `supabase/migrations/`, run `supabase db push` before the first push. If
`master` has no commit after the last tag, there is no release.

## Final report

- Each issue: its commit, or the question that it waits for.
- Each reading of an issue that you selected without a question.
- Each `Strong` candidate and its commit, and the path of the architecture report.
- The terms and the questions that `actualize-doc` reported.
- The released version and its changelog entry.
