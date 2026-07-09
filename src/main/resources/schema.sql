-- Phase 4a: the song table backing com.vinoigitare.storage.DatabaseSongRepository.
-- Run automatically at startup (spring.sql.init.mode=always in application.yml)
-- against whichever datasource is configured -- the real H2 file in
-- production, an isolated in-memory H2 instance in tests.
CREATE TABLE IF NOT EXISTS song (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    artist VARCHAR(500) NOT NULL,
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(600) NOT NULL,
    genre VARCHAR(50),
    chords CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    views BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_song_artist ON song (artist);
CREATE INDEX IF NOT EXISTS idx_song_slug ON song (slug);
CREATE INDEX IF NOT EXISTS idx_song_genre ON song (genre);
