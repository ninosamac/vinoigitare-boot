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
