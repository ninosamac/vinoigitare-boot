package com.vinoigitare.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vinoigitare.model.Genre;
import com.vinoigitare.model.Song;
import com.vinoigitare.storage.SongRepository;
import com.vinoigitare.storage.TabFileMirror;
import com.vinoigitare.storage.TextFileSongRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class SongServiceTest {

    // A real TextFileSongRepository/TabFileMirror against a throwaway
    // @TempDir, shared by every test below -- none of them exercise
    // store()'s .tab-mirroring behavior except the dedicated tests further
    // down, so this just needs to be a valid, harmless collaborator.
    @TempDir
    private Path tempSongsDir;

    private TabFileMirror mirror() {
        return new TabFileMirror(new TextFileSongRepository(tempSongsDir));
    }

    private static class InMemorySongRepository implements SongRepository {
        private final Map<String, Song> songs = new LinkedHashMap<>();

        @Override
        public Optional<Song> findById(String id) {
            return Optional.ofNullable(songs.get(id));
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
            Song song = songs.get(id);
            if (song != null) {
                songs.put(id, new Song(song.id(), song.artist(), song.title(), song.slug(), song.genre(),
                        song.chords(), song.createdAt(), song.views() + 1));
            }
        }
    }

    @Test
    void loadByGenreFiltersAndSortsByArtistThenTitle() {
        InMemorySongRepository repository = new InMemorySongRepository();
        repository.save(new Song(null, "B Artist", "Title", null, "Pop/Rock", "chords", null, 0L));
        repository.save(new Song(null, "A Artist", "Z Title", null, "Pop/Rock", "chords", null, 0L));
        repository.save(new Song(null, "A Artist", "A Title", null, "Pop/Rock", "chords", null, 0L));
        repository.save(new Song(null, "C Artist", "Title", null, "Narodno", "chords", null, 0L));
        repository.save(new Song(null, "D Artist", "Title", null, null, "chords", null, 0L));

        SongService service = new SongService(repository, mirror());
        List<Song> popRock = service.loadByGenre(Genre.POP_ROCK);

        assertThat(popRock).extracting(Song::artist).containsExactly("A Artist", "A Artist", "B Artist");
        assertThat(popRock).extracting(Song::title).containsExactly("A Title", "Z Title", "Title");
    }

    @Test
    void loadByGenreExcludesSongsWithNoGenreAssigned() {
        InMemorySongRepository repository = new InMemorySongRepository();
        repository.save(new Song("Artist", "Title", "chords")); // genre defaults to null

        SongService service = new SongService(repository, mirror());

        assertThat(service.loadByGenre(Genre.POP_ROCK)).isEmpty();
    }

    @Test
    void loadByGenreAlsoMatchesTheOriginalSerbianLabelTextFromBeforeTheI18nSwitch() {
        // Real bug found in testing: SongImporter assigned genres using
        // Genre.label() at import time, which was Serbian text ("Strano")
        // before the site-wide English i18n switch -- a song imported back
        // then still has that literal text stored, while genre.label() is
        // "Foreign" now. Both must count as Genre.STRANO (see Genre.resolve).
        InMemorySongRepository repository = new InMemorySongRepository();
        repository.save(new Song(null, "Old Artist", "Old Song", null, "Strano", "chords", null, 0L));
        repository.save(new Song(null, "New Artist", "New Song", null, "Foreign", "chords", null, 0L));

        SongService service = new SongService(repository, mirror());

        assertThat(service.loadByGenre(Genre.STRANO)).extracting(Song::artist)
                .containsExactlyInAnyOrder("Old Artist", "New Artist");
    }

    @Test
    void loadNewestOrdersByCreatedAtDescendingAndExcludesNullCreatedAt() {
        InMemorySongRepository repository = new InMemorySongRepository();
        Instant now = Instant.now();
        repository.save(new Song("1", "Artist", "Oldest", "oldest", null, "chords", now.minusSeconds(300), 0L));
        repository.save(new Song("2", "Artist", "Newest", "newest", null, "chords", now, 0L));
        repository.save(new Song("3", "Artist", "Middle", "middle", null, "chords", now.minusSeconds(100), 0L));
        repository.save(new Song("Artist", "No Timestamp", "chords")); // createdAt null via 3-arg ctor

        SongService service = new SongService(repository, mirror());
        List<Song> newest = service.loadNewest(10);

        assertThat(newest).extracting(Song::title).containsExactly("Newest", "Middle", "Oldest");
    }

    @Test
    void loadNewestRespectsLimit() {
        InMemorySongRepository repository = new InMemorySongRepository();
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            repository.save(new Song(String.valueOf(i), "Artist", "Title" + i, "slug" + i, null, "chords",
                    now.minusSeconds(i), 0L));
        }

        SongService service = new SongService(repository, mirror());

        assertThat(service.loadNewest(2)).hasSize(2);
    }

    @Test
    void loadMostViewedOrdersByViewsDescending() {
        InMemorySongRepository repository = new InMemorySongRepository();
        repository.save(new Song("1", "Artist", "Low", "low", null, "chords", null, 2L));
        repository.save(new Song("2", "Artist", "High", "high", null, "chords", null, 10L));
        repository.save(new Song("3", "Artist", "Mid", "mid", null, "chords", null, 5L));

        SongService service = new SongService(repository, mirror());
        List<Song> popular = service.loadMostViewed(10);

        assertThat(popular).extracting(Song::title).containsExactly("High", "Mid", "Low");
    }

    @Test
    void recordViewDelegatesToRepositoryIncrementViews() {
        InMemorySongRepository repository = new InMemorySongRepository();
        Song saved = repository.save(new Song("1", "Artist", "Title", "slug", null, "chords", null, 0L));
        SongService service = new SongService(repository, mirror());

        service.recordView(saved.id());
        service.recordView(saved.id());

        assertThat(repository.findById(saved.id()).orElseThrow().views()).isEqualTo(2L);
    }

    @Test
    void storingABrandNewSongCreatesItsTabFile() {
        InMemorySongRepository repository = new InMemorySongRepository();
        SongService service = new SongService(repository, mirror());

        // Mirrors AdminController.create(): id is null, so Song's compact
        // constructor derives the legacy "artist - title" form -- never a
        // real database row id, which is exactly what tells store() this
        // is a new song, not an edit (see its Javadoc).
        service.store(new Song(null, "Test Artist", "Test Title", null, null, "C G\nSome lyrics", null, 0L));

        assertThat(tabFileContent("Test Artist - Test Title.tab")).isEqualTo("C G\nSome lyrics");
    }

    @Test
    void storingAnEditedSongOverwritesTheSameTabFile() {
        InMemorySongRepository repository = new InMemorySongRepository();
        Song original = repository.save(
                new Song("1", "Test Artist", "Test Title", "test-artist--test-title", null, "old chords", null, 0L));
        SongService service = new SongService(repository, mirror());

        service.store(new Song(original.id(), original.artist(), original.title(), original.slug(), null,
                "new chords", null, 0L));

        assertThat(tabFileContent("Test Artist - Test Title.tab")).isEqualTo("new chords");
    }

    @Test
    void storingASongWithAChangedArtistOrTitleMovesItsTabFile() {
        InMemorySongRepository repository = new InMemorySongRepository();
        Song original = repository.save(
                new Song("1", "Old Artist", "Old Title", "old-artist--old-title", null, "chords", null, 0L));
        SongService service = new SongService(repository, mirror());

        service.store(
                new Song(original.id(), "New Artist", "New Title", null, null, original.chords(), null, 0L));

        assertThat(tempSongsDir.resolve("Old Artist - Old Title.tab")).doesNotExist();
        assertThat(tabFileContent("New Artist - New Title.tab")).isEqualTo("chords");
    }

    private String tabFileContent(String fileName) {
        try {
            return Files.readString(tempSongsDir.resolve(fileName), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
