package com.vinoigitare.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vinoigitare.VinoigitareProperties;
import com.vinoigitare.model.Song;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level test of {@link SongImporter}'s own logic (import-when-empty,
 * skip-when-populated), using an in-memory fake {@link SongRepository}
 * rather than a real database -- {@link DatabaseSongRepositoryTest} and the
 * full-context tests already cover the real-database path; this isolates
 * just the importer's decision logic.
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
    void skipsImportWhenDatabaseAlreadyHasSongs(@TempDir Path songsDir) throws IOException {
        writeFixture(songsDir, "Marko Markovic - Probna pesma.tab", "chords one");

        TextFileSongRepository fileRepository = new TextFileSongRepository(new VinoigitareProperties(songsDir.toString()));
        InMemorySongRepository database = new InMemorySongRepository();
        database.save(new Song("1", "Already Here", "Some Song", null, null, "x", null, 0L));
        SongImporter importer = new SongImporter(fileRepository, database);

        importer.run(null);

        // Only the pre-existing row -- the .tab fixture was NOT imported,
        // because the database wasn't empty when the importer ran.
        assertThat(database.findAll()).hasSize(1);
        assertThat(database.findAll()).extracting(Song::artist).containsExactly("Already Here");
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

    private static void writeFixture(Path dir, String fileName, String content) throws IOException {
        Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
    }
}
