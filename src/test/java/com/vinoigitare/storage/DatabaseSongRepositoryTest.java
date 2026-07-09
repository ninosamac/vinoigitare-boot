package com.vinoigitare.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.vinoigitare.AbstractSpringBootTest;
import com.vinoigitare.model.Song;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DatabaseSongRepository} against a real (isolated,
 * in-memory) H2 instance -- schema.sql included, same as production --
 * rather than mocking JdbcTemplate, since the whole point is verifying the
 * SQL/id-parsing logic actually works.
 *
 * <p>Points {@code vinoigitare.songs-dir} at an empty temp directory so
 * {@code SongImporter} (an {@code ApplicationRunner}, so it always runs on
 * this test's full {@code @SpringBootTest} context too) has nothing to
 * import -- otherwise every test here would start with the real {@code
 * ./data/songs} fixtures already in the database, which would break the
 * exact-row-count assertions below.
 */
@Tag("io")
@SpringBootTest
class DatabaseSongRepositoryTest extends AbstractSpringBootTest {

    @TempDir
    static Path emptySongsDir;

    @DynamicPropertySource
    static void noFixturesToImport(DynamicPropertyRegistry registry) {
        registry.add("vinoigitare.songs-dir", () -> emptySongsDir.toString());
    }

    @Autowired
    private DataSource dataSource;

    // The Spring context (and therefore this in-memory database) is shared
    // across every @Test method in this class -- Spring caches the context
    // per test class, it doesn't recreate it per method. Clear the table
    // before each test so they don't see each other's rows.
    @BeforeEach
    void clearSongTable() {
        new JdbcTemplate(dataSource).update("DELETE FROM song");
    }

    private DatabaseSongRepository newRepository() {
        return new DatabaseSongRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void saveAssignsNumericIdAndFindByIdReturnsIt() {
        DatabaseSongRepository repository = newRepository();
        Song song = new Song("Marko Markovic", "Probna pesma", "G D Em C\nlyrics");

        Song saved = repository.save(song);

        assertThat(saved.id()).matches("\\d+");
        Optional<Song> loaded = repository.findById(saved.id());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().artist()).isEqualTo("Marko Markovic");
        assertThat(loaded.get().title()).isEqualTo("Probna pesma");
        assertThat(loaded.get().slug()).isEqualTo("marko-markovic--probna-pesma");
        assertThat(loaded.get().createdAt()).isNotNull();
    }

    @Test
    void savingASongConstructedViaThreeArgCtorInsertsRatherThanErrors() {
        // The 3-arg constructor's default id ("Marko Markovic - Probna
        // pesma") is never a valid row id -- save() must treat this as a
        // fresh insert, not attempt (and fail) an update.
        DatabaseSongRepository repository = newRepository();
        Song song = new Song("Marko Markovic", "Probna pesma", "chords");

        Song saved = repository.save(song);

        assertThat(saved.id()).isNotEqualTo(song.id());
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void updateOverwritesExistingRowInPlace() {
        DatabaseSongRepository repository = newRepository();
        Song saved = repository.save(new Song("Marko Markovic", "Probna pesma", "old chords"));

        Song updated = new Song(saved.id(), saved.artist(), saved.title(), saved.slug(), saved.genre(),
                "new chords", saved.createdAt(), saved.views());
        repository.save(updated);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(saved.id()).orElseThrow().chords()).isEqualTo("new chords");
    }

    @Test
    void deleteRemovesRowAndExistsByIdReflectsIt() {
        DatabaseSongRepository repository = newRepository();
        Song saved = repository.save(new Song("Artist", "Title", "chords"));

        assertThat(repository.existsById(saved.id())).isTrue();
        repository.delete(saved.id());
        assertThat(repository.existsById(saved.id())).isFalse();
        assertThat(repository.findById(saved.id())).isEmpty();
    }

    @Test
    void findByIdWithNonNumericOrUnknownIdReturnsEmptyRatherThanThrowing() {
        DatabaseSongRepository repository = newRepository();

        assertThat(repository.findById("not-a-number")).isEmpty();
        assertThat(repository.findById("999999")).isEmpty();
        assertThat(repository.existsById("not-a-number")).isFalse();
    }

    @Test
    void findAllOrdersByArtistThenTitle() {
        DatabaseSongRepository repository = newRepository();
        repository.save(new Song("B Artist", "Title", "chords"));
        repository.save(new Song("A Artist", "Title", "chords"));

        List<Song> all = repository.findAll();

        assertThat(all).extracting(Song::artist).containsExactly("A Artist", "B Artist");
    }

    @Test
    void diacriticsRoundTripThroughTheDatabase() {
        DatabaseSongRepository repository = newRepository();
        Song saved = repository.save(new Song("Đorđe Đokić", "Šašava priča", "š đ č ć ž tekst pesme"));

        Song loaded = repository.findById(saved.id()).orElseThrow();

        assertThat(loaded.artist()).isEqualTo("Đorđe Đokić");
        assertThat(loaded.title()).isEqualTo("Šašava priča");
        assertThat(loaded.chords()).isEqualTo("š đ č ć ž tekst pesme");
        assertThat(loaded.slug()).isEqualTo("dorde-dokic--sasava-prica");
    }
}
