package com.vinoigitare.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vinoigitare.VinoigitareProperties;
import com.vinoigitare.model.Song;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level test of {@link SongImporter}'s own reconciliation logic
 * (add/update/delete, plus the deletion safety net), using an in-memory
 * fake {@link SongRepository} rather than a real database --
 * {@link DatabaseSongRepositoryTest} and the full-context tests already
 * cover the real-database path; this isolates just the importer's
 * decision logic.
 */
@Tag("fast")
class SongImporterTest {

    private static class InMemorySongRepository implements SongRepository {
        private final java.util.Map<String, Song> songs = new java.util.LinkedHashMap<>();

        @Override
        public java.util.Optional<Song> findById(String id) {
            return java.util.Optional.ofNullable(songs.get(id));
        }

        @Override
        public List<Song> findAll() {
            return List.copyOf(songs.values());
        }

        @Override
        public Song save(Song song) {
            songs.put(song.id(), song);
            return song;
        }

        @Override
        public void delete(String id) {
            songs.remove(id);
        }

        @Override
        public boolean existsById(String id) {
            return songs.containsKey(id);
        }

        @Override
        public void incrementViews(String id) {
            // Not exercised by SongImporterTest -- see SongServiceTest for
            // an in-memory fake that actually implements this.
        }
    }

    @Test
    void importsEveryTabFileWhenDatabaseIsEmpty(@TempDir Path songsDir) throws IOException {
        writeFixture(songsDir, "Marko Markovic - Probna pesma.tab", "chords one");
        writeFixture(songsDir, "Ana Anic - Druga pesma.tab", "chords two");

        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.run(null);

        assertThat(database.findAll()).hasSize(2);
        assertThat(database.findAll()).extracting(Song::artist)
                .containsExactlyInAnyOrder("Marko Markovic", "Ana Anic");
    }

    @Test
    void importedSongsGetDatabaseSlugsPreservedFromFileContent() throws IOException {
        Path songsDir = Files.createTempDirectory("song-importer-test");
        writeFixture(songsDir, "Đorđe Đokić - Šašava priča.tab", "š đ č ć ž tekst pesme");

        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.run(null);

        Song imported = database.findAll().get(0);
        assertThat(imported.artist()).isEqualTo("Đorđe Đokić");
        assertThat(imported.title()).isEqualTo("Šašava priča");
        assertThat(imported.chords()).isEqualTo("š đ č ć ž tekst pesme");
        assertThat(imported.slug()).isEqualTo("dorde-dokic--sasava-prica");
    }

    @Test
    void newTabFilesAreImportedEvenWhenTheDatabaseAlreadyHasOtherSongs(@TempDir Path songsDir) throws IOException {
        // "Already Here" has its own matching .tab file too -- a database
        // row with no backing file at all only happens when it's actually
        // been deleted elsewhere (see databaseRowIsDeletedWhenItsTabFileNoLongerExists),
        // which isn't what this test is about.
        writeFixture(songsDir, "Marko Markovic - Probna pesma.tab", "chords one");
        writeFixture(songsDir, "Already Here - Some Song.tab", "x");

        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        Song alreadyHere = new Song("1", "Already Here", "Some Song", "already-here--some-song", "x",
                Instant.parse("2026-01-01T00:00:00Z"), 42L);
        database.save(alreadyHere);
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.run(null);

        // Both the pre-existing row (untouched) and the new file-only song.
        assertThat(database.findAll()).hasSize(2);
        assertThat(database.findAll()).extracting(Song::artist)
                .containsExactlyInAnyOrder("Already Here", "Marko Markovic");
        assertThat(database.findById("1")).contains(alreadyHere);
    }

    @Test
    void existingSongsAreNotReimportedWhenNothingChanged(@TempDir Path songsDir) throws IOException {
        writeFixture(songsDir, "Marko Markovic - Probna pesma.tab", "C G");

        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        Song existing = new Song("5", "Marko Markovic", "Probna pesma", "marko-markovic--probna-pesma", "C G",
                Instant.parse("2026-01-01T00:00:00Z"), 42L);
        database.save(existing);
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.run(null);

        // No duplicate, and the row itself is untouched -- same id, slug,
        // createdAt, and view count as before.
        assertThat(database.findAll()).hasSize(1);
        assertThat(database.findById("5")).contains(existing);
    }

    @Test
    void editedTabFileContentUpdatesTheExistingRowInPlace(@TempDir Path songsDir) throws IOException {
        writeFixture(songsDir, "Marko Markovic - Probna pesma.tab", "C G Am F");

        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        database.save(new Song("5", "Marko Markovic", "Probna pesma", "marko-markovic--probna-pesma", "C G",
                createdAt, 42L));
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.run(null);

        // Chords updated from the file, but id/slug/createdAt/views all
        // survive the edit -- the song's URL and view count don't reset
        // just because its lyrics/chords changed on another instance.
        assertThat(database.findAll()).hasSize(1);
        Song updated = database.findById("5").orElseThrow();
        assertThat(updated.chords()).isEqualTo("C G Am F");
        assertThat(updated.slug()).isEqualTo("marko-markovic--probna-pesma");
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.views()).isEqualTo(42L);
    }

    @Test
    void databaseRowIsDeletedWhenItsTabFileNoLongerExists(@TempDir Path songsDir) throws IOException {
        // One song still has a .tab file; the other's was removed
        // elsewhere (e.g. deleted via another instance's admin panel and
        // synced here) -- file count (1) is at least half the database
        // count (2), so the safety net allows the deletion.
        writeFixture(songsDir, "Marko Markovic - Probna pesma.tab", "C G");

        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        database.save(new Song("5", "Marko Markovic", "Probna pesma", "marko-markovic--probna-pesma", "C G",
                Instant.parse("2026-01-01T00:00:00Z"), 42L));
        database.save(new Song("9", "Deleted Elsewhere", "Gone Song", "deleted-elsewhere--gone-song", "x",
                Instant.parse("2026-01-01T00:00:00Z"), 0L));
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.run(null);

        assertThat(database.findAll()).extracting(Song::id).containsExactly("5");
    }

    @Test
    void deletionIsSkippedWhenFileCountLooksSuspiciouslyLowComparedToDatabase(@TempDir Path songsDir)
            throws IOException {
        // Empty songsDir: every one of these 4 database rows would look
        // "deleted" by a naive file-vs-database diff. The safety net
        // should refuse to touch any of them rather than wipe the
        // collection over what could just be an incomplete sync.
        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        for (int i = 1; i <= 4; i++) {
            database.save(new Song(String.valueOf(i), "Artist " + i, "Title " + i, null, "x",
                    Instant.parse("2026-01-01T00:00:00Z"), 0L));
        }
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.run(null);

        assertThat(database.findAll()).hasSize(4);
    }

    @Test
    void scheduledReconciliationBehavesLikeStartupReconciliation(@TempDir Path songsDir) throws IOException {
        writeFixture(songsDir, "Marko Markovic - Probna pesma.tab", "chords one");

        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.reconcileOnSchedule();

        assertThat(database.findAll()).extracting(Song::artist).containsExactly("Marko Markovic");
    }

    private static void writeFixture(Path dir, String fileName, String content) throws IOException {
        Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
    }
}
