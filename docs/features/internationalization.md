# Internationalization (EN/HR/SR)

## What it does

The whole site is available in English, Croatian, and Serbian. English
is the default for a first-time visitor; switching (via the Preferences
dropdown) persists the choice for a year, so a returning visitor doesn't
need to pick it again.

## How it works

- **`LocaleConfig`** wires a cookie-backed `CookieLocaleResolver`
  (`vinoigitare.locale` cookie, 365-day max-age — not the HTTP session,
  since a returning visitor a week later should still see their last
  choice) and a `LocaleChangeInterceptor` keyed off a `?lang=` query
  parameter (`en`/`hr`/`sr`).
- **Deliberately not auto-detected from the browser's Accept-Language
  header**, even though Spring could do that automatically — a
  visitor's browser locale is a weak, often-wrong signal (shared/work
  computers, an OS installed in a different language than the person
  actually reads), and Google's own crawler sends no meaningful
  Accept-Language at all, so it should consistently index the same
  (English) version of every page.
- **Message bundles**: `messages.properties` (English, default),
  `messages_hr.properties`, `messages_sr.properties` — one key per piece
  of UI text, referenced via `#{key}` in every Thymeleaf template.
  Croatian and Serbian are genuinely separate, hand-written translations
  (not a mechanical find-replace over each other), including real
  vocabulary differences between the two languages (e.g. *kava* vs.
  *kafa* for "coffee").
- **The language links themselves** are plain `?lang=xx` anchors, not
  JS — deliberately a bare query-only href (not a Thymeleaf `@{...}`
  URL), so switching language resolves against whatever page you're
  currently on and you stay right where you were, rather than being
  bounced to some fixed URL.
