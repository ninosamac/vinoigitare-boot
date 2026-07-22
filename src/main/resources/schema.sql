-- Phase 4a: the song table backing com.vinoigitare.storage.DatabaseSongRepository.
-- Run automatically at startup (spring.sql.init.mode=always in application.yml)
-- against whichever datasource is configured -- the real H2 file in
-- production, an isolated in-memory H2 instance in tests.
CREATE TABLE IF NOT EXISTS song (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    artist VARCHAR(500) NOT NULL,
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(600) NOT NULL,
    chords CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    views BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_song_artist ON song (artist);
CREATE INDEX IF NOT EXISTS idx_song_slug ON song (slug);

-- Personalized songbook PDF, Phase B (~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md,
-- SS2/S7): a real DB row per pending/paid purchase, not an in-memory cache
-- with a TTL -- this box gets redeployed/restarted for every code change,
-- and losing a pending purchase mid-checkout because the app happened to
-- restart would be a bad visitor experience for a paid feature. id is an
-- opaque, high-entropy random string generated in code (SecureRandom-backed,
-- see SongbookRequestRepository) -- it IS the entire access control for a
-- paid download, so it's the primary key directly rather than a separate
-- auto-increment id plus a lookup column.
-- page_count/pdf_bytes added 2026-07-18 (personalized-songbook-pdf-plan.md
-- §1c): pricing switched from song count to actual rendered page count,
-- which can only be known by rendering the PDF -- so checkout now renders
-- it before payment (see SongbookCheckoutController#checkout) and stores
-- the resulting bytes here, rather than re-rendering at download time.
-- This also guarantees the downloaded PDF is exactly what was priced and
-- paid for, even if the underlying song data changes in between.
CREATE TABLE IF NOT EXISTS songbook_request (
    id VARCHAR(64) PRIMARY KEY,
    selection CLOB NOT NULL,
    book_title VARCHAR(200),
    include_chord_diagrams BOOLEAN NOT NULL,
    song_count INT NOT NULL,
    page_count INT NOT NULL,
    amount_cents INT NOT NULL,
    pdf_bytes BLOB NOT NULL,
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP
);

-- Analytics, Part 3 (analytics-plan.md): day-granularity aggregates built
-- by com.vinoigitare.analytics.LogAnalyticsAggregator from the request log
-- RequestLoggingFilter already writes, not raw-log parsing at read time --
-- journald/the rotated log file itself aren't permanent, these tables are.
-- Composite primary key doubles as the upsert key the aggregator increments
-- against.
CREATE TABLE IF NOT EXISTS daily_page_hit (
    hit_date DATE NOT NULL,
    path VARCHAR(500) NOT NULL,
    hits BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (hit_date, path)
);

-- referrer_host is the referring URL's host only (e.g. "google.com"), not
-- the full URL -- see LogAnalyticsAggregator for why, and for the "direct"
-- sentinel value used when a request has no Referer header at all.
CREATE TABLE IF NOT EXISTS daily_referrer_hit (
    hit_date DATE NOT NULL,
    referrer_host VARCHAR(255) NOT NULL,
    hits BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (hit_date, referrer_host)
);

-- Single-row cursor (id is always 1) so a restart resumes aggregation from
-- where it left off instead of re-counting the whole log file every run.
CREATE TABLE IF NOT EXISTS log_analytics_state (
    id INT PRIMARY KEY,
    last_processed_at TIMESTAMP NOT NULL
);
