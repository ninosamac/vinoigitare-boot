# Visitor feedback form

## What it does

A plain comment form on the About page (name and email both optional):
type a message, hit send, it lands directly in an email inbox. Nothing
is stored or displayed anywhere on the site — this is a "send feedback"
form, not a public comment section.

## How it works

- **`FeedbackController`** (`POST /feedback`) — this app's first
  genuinely public, unauthenticated POST route. Validates the comment
  (required, max 2000 chars) and optional name/email (`@Email`,
  `@Size`), then sends the email and redirects back to
  `/about?feedbackSent` or `/about?feedbackError` — the same
  redirect-with-query-param pattern the login page already used for
  `?error`/`?logout`, not a new flash-message mechanism.
- **Anti-abuse, deliberately lightweight** (no CAPTCHA, no email
  verification — escalate only if spam actually becomes a real
  problem):
  - **Honeypot**: an extra field (`website`), hidden off-screen via CSS
    (not `display:none`/`type=hidden`, which some bots specifically
    check for) and removed from the accessibility tree. Any non-blank
    value silently drops the submission — but still shows the normal
    success message, so a bot is never tipped off about what caught it.
  - **Per-IP rate limiting** (`FeedbackRateLimiter`): a small in-memory
    `ConcurrentHashMap<IP, Deque<Instant>>`, capped at 3 submissions per
    10-minute rolling window. Losing this state on a restart is an
    accepted, minor gap — it's spam mitigation, not a real security
    control.
- **Email delivery** (`ResendEmailClient`): sends via the
  [Resend](https://resend.com) HTTP API, **not SMTP**. The original
  design used Gmail SMTP (`spring-boot-starter-mail`), which worked in
  local development but failed the moment it was actually deployed —
  DigitalOcean blocks outbound SMTP ports (25/465/587) by default on
  Droplets. An HTTP API over port 443 (already open for everything else
  this app does) sidesteps that entirely.
- **CSRF still applies**: being on the public allowlist only affects
  *authorization*, not CSRF — that's a separate concern, and this form's
  `th:action` still gets a real token auto-injected like every other
  form in the app.
