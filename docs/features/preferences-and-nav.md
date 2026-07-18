# Preferences & navigation

## What it does

The navbar has four items — **Chords**, **About**, **Admin**, **User**
— plus a small gear-icon dropdown for **Theme** (Light/Dark/High
Contrast), **Large Text**, and **Language**. All three are reachable
from every page with one click; **User** is a real page (like Admin),
currently just linking through to "My Songbook."

## How it works

- **Theme switch** (`static/js/theme-toggle.js`): three options —
  Light, Dark, High Contrast — stored in `localStorage`, applied via an
  inline `<script>` in every page's own `<head>` (before first paint,
  not at the end of `<body>`) specifically to avoid a flash of the
  wrong theme while the rest of the page's scripts are still loading.
  High Contrast is a standalone third theme, not combinable with
  Light/Dark — picking it means giving up whichever of the other two
  you were on. Shown as three always-visible text buttons (matching the
  Language switcher's own shape below), not a single icon that cycles
  through states on repeated clicks — decided during scoping for
  discoverability (see
  `~/knowledge/projects/vinoigitare/accessibility-preferences-plan.md`).
  `song-live.html`'s own minimal chrome bar has the same three buttons
  inline (no Preferences dropdown there at all).
- **Large Text toggle** (`static/js/font-size-toggle.js`): a single
  on/off switch, independent of Theme, that scales the whole site's
  base font size (125%) via one CSS rule
  (`:root[data-font-size="large"]`) — effective site-wide because
  nearly every `font-size` in `app.css` is already `rem`-based, not
  `px`. Restored pre-paint the same way Theme is. Deliberately **not**
  available on `song-live.html`: that page already has its own bigger
  base font size and its own zoom stepper, and layering the site-wide
  multiplier on top would compound both rather than match either's
  intent.
- **Language switcher**: see
  [internationalization.md](internationalization.md).
- **The Preferences dropdown** (`static/js/preferences-menu.js`)
  consolidates Theme + Language behind one icon button and a small
  anchored panel — not a full-screen modal — so the navbar itself only
  gains one extra item instead of two separate always-visible controls.
  The JS here only owns open/close/outside-click/escape-key handling;
  the actual Theme/Language controls inside are untouched by it.
- **Naming history, briefly**: this dropdown was renamed from
  "Preferences" to a plain "User" text label for about a day, before a
  separate real **User tab** (`UserController`, `GET /user`) was added
  right next to it — at which point the dropdown reverted to
  "Preferences" (back to its original gear icon too) to avoid two
  different nav items both saying "User." The User *tab* behaves like
  Admin (a real page you navigate to); the Preferences *dropdown*
  behaves like a quick-access control reachable from anywhere without
  navigating away.
- **`/user`** (`UserController`) is deliberately minimal today — just a
  link through to the [personalized songbook feature](personalized-songbook-and-paywall.md).
  There's room for genuinely account-ish content here later, if this
  app ever grows any (it has no visitor-accounts system today, by
  design).
