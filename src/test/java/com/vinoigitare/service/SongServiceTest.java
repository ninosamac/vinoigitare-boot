package com.vinoigitare.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void loadAllGroupedByArtistOrdersArtistsCaseInsensitivelyAndSongsByTitle() {
        // Real bug found while building the homepage artist tree: a plain
        // TreeMap::new (natural String order) sorts by raw character code,
        // so any uppercase-starting name lands before every lowercase one
        // regardless of actual alphabetical order -- "ana anic" would sort
        // after "Zarko Z", not next to "Ana Anic" where a reader expects
        // it. See SongService#loadAllGroupedByArtist's Javadoc.
        InMemorySongRepository repository = new InMemorySongRepository();
        repository.save(new Song(null, "ana anic", "Song", null, null, "chords", null, 0L));
        repository.save(new Song(null, "Zarko Z", "Song", null, null, "chords", null, 0L));
        repository.save(new Song(null, "Marko Markovic", "Z Title", null, null, "chords", null, 0L));
        repository.save(new Song(null, "Marko Markovic", "A Title", null, null, "chords", null, 0L));

        SongService service = new SongService(repository, mirror());
        Map<String, List<Song>> grouped = service.loadAllGroupedByArtist();

        assertThat(grouped.keySet()).containsExactly("ana anic", "Marko Markovic", "Zarko Z");
        assertThat(grouped.get("Marko Markovic")).extracting(Song::title).containsExactly("A Title", "Z Title");
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
