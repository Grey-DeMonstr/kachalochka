# Privacy Policy and Terms of Service — design

**Date:** 2026-09-28

Google's OAuth consent screen and the Google Play listing both ask for a privacy policy URL, and
the consent screen for a terms of service URL, on the app's own domain. Kachalochka publishes
both as separate static pages beside the web app on GitHub Pages, and the repository takes the
licence `opds_browser` uses, GPL v3.

---

## 1. Scope

Built:

- `privacy.html` and `terms.html`, English only, served beside the web app.
- A footer on the web app's entry page linking to both, readable without running the app.
- `LICENSE`: the GNU GPL v3 text, verbatim.
- A host test keeping the pages, their sections and the footer links in place.
- A "Privacy and terms" note in both specs.

Not built: links inside the Android or web UI, a Russian translation, self-service account
deletion, a governing-law clause.

---

## 2. Decisions

- **Hand-written HTML in `app/src/wasmJsMain/resources/`.** The Wasm distribution copies that
  directory as it is, so the pages reach Pages with no build change and are served at
  `<web app address>/privacy.html` and `/terms.html`. Markdown rendered at build time would add a
  dependency and a task for two pages that rarely change.
- **Each page is self-contained: inline CSS, no script, nothing loaded from another origin.** A
  reviewer or crawler reads it without the app, and it cannot break with the app's bundle.
- **Light and dark follow `prefers-color-scheme`.** The pages have no theme switch to read the
  app's stored choice from.
- **The entry page gets a plain-HTML footer with both links and a one-line description of the
  app.** The app draws on a canvas, so a crawler checking the OAuth homepage sees nothing else.
  Compose mounts into its own `<div id="app">`, which takes the height the footer leaves, so the
  footer never covers the app's controls.
- **The provider is Sergei Ivanov, an individual developer.** Google's consent screen names the
  developer anyway.
- **Contact and deletion requests go through the repository's GitHub issues, handled within 30
  days.** The developer's e-mail is kept out of the repository. The pages tell the requester to
  put no personal data in the issue; the developer confirms the account with them privately
  before deleting it.
- **The documents describe what is built.** Photos are not available, so the policy does not
  mention them; each document is updated, with a new effective date, when a feature changes
  what it says.
- **No governing-law clause.** A free hobby app; the terms stay an as-is and acceptable-use
  agreement.
- **The terms cover the hosted service, not the code.** The GPL governs the source; a
  self-hosted build against another backend is outside the terms.

---

## 3. Privacy Policy

Header: "Kachalochka Privacy Policy", the app id `monster.greyde.kachalochka`, the provider, the
effective date. Sections:

1. **Summary.** Workouts are stored to sync them between devices and to share them with groups
   the user joins. No ads, no analytics, no crash reporting, no selling or sharing for marketing.
2. **What we collect.**
   - From Google sign-in: e-mail address, name and Google account identifier.
   - What the user records: machines (name, setup note, weight settings, unit), visits (their
     day) and sets (weight, repetitions, time recorded).
   - Groups: group names, invite codes, membership, and the display name taken from the Google
     name.
   - Technical: sign-in tokens; request logs the hosting providers keep.
3. **Where it is stored.**
   - Android: on the device. Without an account nothing leaves it; with one, the data syncs to
     the backend. Several accounts on one device each keep their own data.
   - Web: in the backend only; the browser keeps the sign-in session and the theme choice.
4. **Who can see it.** The user, and members of groups the user joins: they see the display
   name, visits and machines, read-only. Leaving a group stops it. No public profile.
5. **Service providers.** Supabase (database and authentication), Google (sign-in), GitHub
   (hosting of the web app and the APK). Each processes data under its own privacy policy,
   linked. Use of information received from Google APIs adheres to the Google API Services User
   Data Policy, including its Limited Use requirements.
6. **Retention and deletion.**
   - Data is kept while the account exists.
   - Deleting a visit or set in the app hides it; the row stays on the server until the account
     is deleted.
   - Signing out removes the account from the device, not from the server.
   - To delete the account and all data tied to it, open a GitHub issue asking for deletion;
     it is completed within 30 days.
   - On Android, data recorded without an account is removed by uninstalling the app or clearing
     its data.
7. **Security.** HTTPS for every request; row-level security in the database lets an account
   read only its own rows and those of its groups.
8. **Permissions.** Internet access.
9. **Children.** The app is not directed at children under 13. A child's use, including a child's
   account signed in beside a parent's, is set up and supervised by a parent or guardian.
10. **Changes.** Published on this page with a new effective date; the history is public in the
    repository.
11. **Contact.** The repository's GitHub issues.

---

## 4. Terms of Service

Header: "Kachalochka Terms of Service", the provider, the effective date, a link to the Privacy
Policy. Sections:

1. **Agreement.** Using the app or signing in accepts these terms.
2. **The service.** A free personal tool to record workouts, sync them between Android and the
   web and share them with groups, provided by an individual developer.
3. **Your account.** Sign-in is through Google; the user is responsible for their Google account.
   A parent or guardian is responsible for a child's use.
4. **Your content.** The user owns what they record and grants only the permission needed to
   store it, sync it and show it to the groups they join. They are responsible for what they
   write in names and notes, and for whom they invite.
5. **Acceptable use.** No disrupting or overloading the service, no attempts to reach others'
   data, no unlawful or abusive content in group names, machine names or notes, no automated
   access beyond the app's own.
6. **Groups.** The owner controls the invite code and can delete the group; members can leave at
   any time.
7. **Health.** The app records numbers and gives no medical or training advice; exercise is at
   the user's own risk.
8. **No warranty.** Provided "as is" and "as available"; the service may change, pause or end,
   and data may be lost.
9. **Limitation of liability.** To the extent the law allows, the developer is not liable for
   indirect or consequential loss or for loss of data; the app is free.
10. **Ending use.** The user can stop at any time and request deletion as the Privacy Policy
    describes. The developer may suspend an account that breaks these terms, or end the service.
11. **Changes.** Published on this page with a new effective date; continued use accepts them.
12. **Source code.** The source is available under the GNU GPL v3. These terms cover the hosted
    service — the web app, the backend and accounts — and do not limit the GPL's rights to the
    code; a copy run against another backend is outside them.
13. **Contact.** The repository's GitHub issues.

---

## 5. Entry page

`index.html` gains a `<meta name="description">`, a `<div id="app">` and a footer after it:

```html
<div id="app"></div>
<footer class="legal">
    Kachalochka — a gym workout log.
    <a href="privacy.html">Privacy Policy</a> · <a href="terms.html">Terms of Service</a>
</footer>
```

The body is a flex column: `#app` takes the remaining height and the footer is one slim line of
small text under it. `Main.kt` mounts `ComposeViewport` into `#app` instead of the body. Links
are relative, so they resolve under the Pages path.

---

## 6. Tests

`LegalPagesTest` in `app/src/jvmTest` reads the files from `src/wasmJsMain/resources`, the test's
working directory being the module:

- `privacy.html` has each section heading of §3 and an effective date.
- `terms.html` has each section heading of §4, an effective date and a link to `privacy.html`.
- `index.html` links to `privacy.html` and `terms.html` by relative path.
- No page references `http://`, loads a `<script>` or an external stylesheet, or contains `TODO`
  or `TBD`.

`app`'s `jvmTest` task declares the resources directory as an input, so an edited page reruns the
test rather than reusing a cached pass. A manual check runs `:app:wasmJsBrowserDistribution` and
opens the three pages from the distribution in a browser, in light and dark.

---

## 7. Spec text to apply

### `docs/functional_spec.md`

Append:

> ## Privacy and terms
>
> The Privacy Policy and the Terms of Service are separate pages published beside the web app
> and linked from its entry page, readable without signing in. Account deletion is requested
> through the repository's issues.

### `docs/technical_spec.md`

§8, append:

> - `privacy.html` and `terms.html` are static pages in the web resources and ship in the Pages
>   bundle; their addresses are what the OAuth consent screen and the Play listing point to. The
>   entry page links to both in plain HTML, since the app itself draws on a canvas.
> - The repository is licensed under the GNU GPL v3, in `LICENSE`.

---

## 8. After merging

The pages go live with the next push to `master`. Then, by hand: the privacy and terms URLs and
the homepage go into the Google Cloud OAuth consent screen, and the privacy URL into the Play
Console. The backlog gains "Delete my account from inside the app".
