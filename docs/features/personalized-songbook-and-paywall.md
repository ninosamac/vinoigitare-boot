# Personalized songbook PDF & paywall

## What it does

A visitor can build their own selection of songs from across the whole
catalog (in any order, from any artists), give it a custom title,
choose whether to include the chord-diagrams reference section, and
generate one combined PDF: a cover page (the same wine-and-guitar photo
used on the About page, not a logo), a table of contents with real page
numbers, each selected song at whatever transposition they'd set it to,
and (optionally) the chord-diagram reference pages. Every page's footer
carries a small, unobtrusive `vinoigitare.com` credit next to the page
number.

- **Selection** happens by clicking "Add to my songbook" on any song
  page. It's free and requires no login. Also mentioned on the About
  page (2026-07-19) with a direct link to `/songbook` -- previously only
  discoverable from inside a song page.
- **Building/reviewing the selection** happens on `/user` → "My
  Songbook" (`/songbook`): see what's in it, remove songs, reorder them
  with ↑/↓ buttons per row, set a book title, toggle chord diagrams.
  The order shown here is exactly the order the finished PDF (and its
  table of contents) comes out in -- not re-sorted alphabetically
  (issue #9, 2026-07-19; previously it always was, a real mismatch
  between what the builder showed and what got generated).
- **Generating the actual PDF is a real, paid purchase** for the
  general public — priced by how many pages the finished book comes out
  to:

  | Page count | Price |
  |---|---|
  | Under 20 | €3 |
  | 20–49 | €5 |
  | 50–99 | €7 |
  | 100 or more | Not sold — rejected outright |

  (Raised from $2/$3/$5 to $3/$5/$7 on 2026-07-19, same tier
  boundaries — see `SongbookPricing`'s Javadoc. Switched from USD to EUR
  on 2026-07-22, Nino's own call for an EU-serving site — same numeric
  amounts, just relabeled, since USD/EUR trade near parity.)

  Payment is a real Stripe Checkout session, **live mode** since
  2026-07-19 (GitHub issue #1) — real cards are charged. After paying,
  the visitor lands on a status page with a download link, valid for
  **7 days**, unlimited downloads within that window.
- **The site admin** (logged in) gets a separate, free, instant
  "Generate PDF" button instead of the paywall — a leftover convenience
  from before the paywall existed, kept deliberately rather than making
  the admin pay to test their own site.

## How it works

### Selection (client-side, no accounts)

The selection lives entirely in the browser's `localStorage`
(`static/js/songbook.js`), as a JSON array of `{id, transpose}` pairs —
same pattern as every other locally-persisted preference in this app
(theme, language, font size). Nothing is sent to the server until
generation/checkout is actually triggered. This also means:

- A selection doesn't follow a visitor across devices or browsers —
  accepted tradeoff, not an oversight.
- There's no way for one visitor's selection to ever be confused with
  another's, since nothing about it lives server-side until that one
  checkout attempt.

`GET /songbook/details?ids=1,2,3` resolves ids back to artist/title for
display (localStorage only has ids, not names) — unknown/deleted ids are
silently dropped rather than erroring.

### Rendering (`SongbookPdfRenderer`)

Cover page (site branding, custom title, generation date) → table of
contents (CSS `target-counter`, resolved by openhtmltopdf in a single
render pass — no manual page-number bookkeeping) → each song
(transposed per-item, reusing the same `fragments :: songContent`
fragment the single-song PDF uses) → the chord-diagram reference
section (reused from `ChordDiagramCatalog`/`ChordDiagramRenderer`, same
as the standalone [chord-diagrams page](chord-diagrams.md)).

### The paywall flow (`SongbookCheckoutController`)

1. `POST /songbook/checkout` — **renders the PDF right here, before any
   payment**, purely to find out its real page count. Page count
   depends on far more than song count (song length, transposition,
   whether diagrams are included), so there's no way to price it
   without actually rendering it first.
   - If it's 100+ pages, the checkout is rejected immediately
     (`redirect:/songbook?tooManyPages`) — no database row, no Stripe
     session.
   - Otherwise, a `songbook_request` row is created: an opaque,
     cryptographically random 256-bit id (this id *is* the entire
     access control for the eventual download — never sequential or
     guessable), the selection, the computed page count and price, and
     **the actual rendered PDF bytes**, `paid = false`.
   - A Stripe Checkout Session is created with that price as inline
     `price_data` (no Stripe Dashboard price configuration needed at
     all) and the request id as `client_reference_id`. The visitor is
     redirected to Stripe's hosted page.
2. `POST /songbook/stripe-webhook` — Stripe calls this server-to-server
   once payment actually completes. The signature is verified via the
   Stripe SDK (not just trusted because the request arrived); only then
   is the `client_reference_id` used to mark that row `paid = true`.
   This webhook, not the success-page redirect, is the actual source of
   truth — a redirect can be interrupted or replayed, a
   signature-verified webhook can't be spoofed.
3. `GET /songbook/view/{requestId}` — the status page a visitor lands on
   after paying (or if they revisit the link later): not found, still
   pending, expired, or a download button.
4. `GET /songbook/view/{requestId}/pdf` — serves the **bytes already
   stored on that row from step 1**, not a fresh render. Gated on
   `paid = true` and the 7-day window (403/410 otherwise). Not
   re-rendering here is deliberate: it avoids rendering the same book
   twice, and guarantees the file a visitor downloads is exactly the one
   that was priced and paid for, even if the underlying song data
   changes in between.

### Why `/songbook/view/**`, not a flat `/songbook/{requestId}`

The public paywall routes (`/songbook`, `/songbook/details`,
`/songbook/checkout`, `/songbook/stripe-webhook`, `/songbook/view/**`)
are on Spring Security's public allowlist; the admin-only
`/songbook/generate` deliberately is not. A flat `/songbook/{requestId}`
route would collide with a bare `/songbook/*` allowlist wildcard,
silently making the admin-only route public too — the `/view/` prefix
avoids that collision entirely rather than relying on rule ordering.

### Pricing (`SongbookPricing`)

A tiny, dependency-free utility: `amountCentsFor(pageCount)` and
`exceedsMaxPages(pageCount)`. All tier boundaries and the page cap live
here as plain constants — no Stripe Dashboard configuration to keep in
sync with the code.

### No email involved at all

Delivery is entirely via the web link Stripe's own success redirect
provides — this feature has no mail dependency of its own (unlike the
[visitor feedback form](visitor-feedback.md), which does).
