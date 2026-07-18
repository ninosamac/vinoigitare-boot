# Application logging

## What it does

The app keeps a real, persistent log file (not just whatever
`journalctl`/systemd happens to capture) recording: every HTTP request,
every admin login attempt (success or failure), every admin song
create/edit/delete, every visitor-feedback submission (sent,
rate-limited, or honeypot-caught), and any unhandled exception.

## How it works

- **Logback**, Spring Boot's default — not Log4j2. Log4j2 was the
  original request, but Spring Boot's own Logback integration already
  supports a rolling file appender with a real retention policy purely
  through `application.yml` properties, no separate config file needed.
  Log4j2's real advantages (async/garbage-free logging, higher
  throughput) matter for high-volume services, not a low-traffic solo
  hobby site — so Logback stays, for meaningfully less complexity.
- **File location**: `${VINOIGITARE_LOG_DIR:./logs}/vinoigitare.log` —
  overridable the same way `VINOIGITARE_HOME`/`VINOIGITARE_DB` already
  are. **Rotation**: 10MB per file, 30 files kept, 200MB total disk cap.
  Console output (still visible via `journalctl`) is unchanged — the
  file is an addition, not a replacement.
- **`RequestLoggingFilter`**: method, path, status, duration, and remote
  IP for every request, excluding static assets to keep the
  signal-to-noise ratio high. Registered explicitly via a
  `FilterRegistrationBean` with `Ordered.HIGHEST_PRECEDENCE` — a plain
  `@Component`-annotated filter gets auto-registered by Spring Boot
  *after* Spring Security's own filter chain by default, which meant any
  request Security redirected itself (e.g. an unauthenticated hit on
  `/admin`) never reached the logger at all until this was found and
  fixed.
- **`SecurityAuditListener`**: listens for the `AuthenticationSuccessEvent`/
  `AbstractAuthenticationFailureEvent` events Spring Security already
  publishes automatically — no changes to `SecurityConfig`'s filter
  chain needed.
- **`ExceptionLoggingResolver`**: a `HandlerExceptionResolver` at
  highest precedence that logs every exception reaching Spring MVC, then
  returns `null` so the next resolver in the chain still runs exactly as
  before — purely observational, never changes what a visitor actually
  sees. `ResponseStatusException`s used for expected client errors (like
  a 404 for an unknown song id) log at WARN, not ERROR, since they're
  not application bugs.
- **A real, pre-existing bug this surfaced**: `/error` was never on
  `SecurityConfig`'s public allowlist, so any thrown error on a public
  route triggered Spring MVC's internal forward to `/error`, which
  Spring Security re-challenged and redirected to `/login` instead of
  ever showing the actual 404/403/etc. — confirmed against an
  already-shipped public route (an unknown song page), not something
  the songbook paywall's own error handling introduced. Fixed by adding
  `/error` to the allowlist.
