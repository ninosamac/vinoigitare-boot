package com.vinoigitare.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vinoigitare.VinoigitareProperties;
import com.vinoigitare.model.Song;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("io")
class TextFileSongRepositoryTest {

    private SongRepository repositoryIn(Path dir) {
        return new TextFileSongRepository(new VinoigitareProperties(dir.toString()));
    }

    @Test
    void savedSongCanBeLoadedBackById(@TempDir Path tempDir) {
        SongRepository repository = repositoryIn(tempDir);
        Song song = new Song("Marko Markovic", "Probna pesma", "C G Am F\nTest lyrics");

        repository.save(song);

        Optional<Song> loaded = repository.findById(song.id());
        assertThat(loaded).contains(song);
    }

    @Test
    void findAllListsAllStoredSongsSortedById(@TempDir Path tempDir) {
        SongRepository repository = repositoryIn(tempDir);
        repository.save(new Song("B Artist", "Title", "chords"));
        repository.save(new Song("A Artist", "Title", "chords"));

        List<Song> all = repository.findAll();

        assertThat(all).extracting(Song::artist).containsExactly("A Artist", "B Artist");
    }

    @Test
    void deleteRemovesSongFile(@TempDir Path tempDir) {
        SongRepository repository = repositoryIn(tempDir);
        Song song = new Song("Artist", "Title", "chords");
        repository.save(song);

        repository.delete(song.id());

        assertThat(repository.existsById(song.id())).isFalse();
        assertThat(repository.findById(song.id())).isEmpty();
    }

    @Test
    void existsByIdReflectsPresenceOnDisk(@TempDir Path tempDir) {
        SongRepository repository = repositoryIn(tempDir);
        Song song = new Song("Artist", "Title", "chords");

        assertThat(repository.existsById(song.id())).isFalse();
        repository.save(song);
        assertThat(repository.existsById(song.id())).isTrue();
    }

    @Test
    void diacriticsRoundTripThroughStorageAsUtf8(@TempDir Path tempDir) {
        SongRepository repository = repositoryIn(tempDir);
        Song song = new Song("Đorđe Đokić", "Šašava priča", "š đ č ć ž tekst pesme");

        repository.save(song);
        Optional<Song> loaded = repository.findById(song.id());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().artist()).isEqualTo("Đorđe Đokić");
        assertThat(loaded.get().title()).isEqualTo("Šašava priča");
        assertThat(loaded.get().chords()).isEqualTo("š đ č ć ž tekst pesme");
    }
}
