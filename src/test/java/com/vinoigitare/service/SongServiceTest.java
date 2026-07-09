package com.vinoigitare.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.vinoigitare.model.Song;
import com.vinoigitare.storage.SongRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class SongServiceTest {

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

        SongService service = new SongService(repository);
        List<Song> popRock = service.loadByGenre("Pop/Rock");

        assertThat(popRock).extracting(Song::artist).containsExactly("A Artist", "A Artist", "B Artist");
        assertThat(popRock).extracting(Song::title).containsExactly("A Title", "Z Title", "Title");
    }

    @Test
    void loadByGenreExcludesSongsWithNoGenreAssigned() {
        InMemorySongRepository repository = new InMemorySongRepository();
        repository.save(new Song("Artist", "Title", "chords")); // genre defaults to null

        SongService service = new SongService(repository);

        assertThat(service.loadByGenre("Pop/Rock")).isEmpty();
    }

    @Test
    void loadNewestOrdersByCreatedAtDescendingAndExcludesNullCreatedAt() {
        InMemorySongRepository repository = new InMemorySongRepository();
        Instant now = Instant.now();
        repository.save(new Song("1", "Artist", "Oldest", "oldest", null, "chords", now.minusSeconds(300), 0L));
        repository.save(new Song("2", "Artist", "Newest", "newest", null, "chords", now, 0L));
        repository.save(new Song("3", "Artist", "Middle", "middle", null, "chords", now.minusSeconds(100), 0L));
        repository.save(new Song("Artist", "No Timestamp", "chords")); // createdAt null via 3-arg ctor

        SongService service = new SongService(repository);
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

        SongService service = new SongService(repository);

        assertThat(service.loadNewest(2)).hasSize(2);
    }

    @Test
    void loadMostViewedOrdersByViewsDescending() {
        InMemorySongRepository repository = new InMemorySongRepository();
        repository.save(new Song("1", "Artist", "Low", "low", null, "chords", null, 2L));
        repository.save(new Song("2", "Artist", "High", "high", null, "chords", null, 10L));
        repository.save(new Song("3", "Artist", "Mid", "mid", null, "chords", null, 5L));

        SongService service = new SongService(repository);
        List<Song> popular = service.loadMostViewed(10);

        assertThat(popular).extracting(Song::title).containsExactly("High", "Mid", "Low");
    }

    @Test
    void recordViewDelegatesToRepositoryIncrementViews() {
        InMemorySongRepository repository = new InMemorySongRepository();
        Song saved = repository.save(new Song("1", "Artist", "Title", "slug", null, "chords", null, 0L));
        SongService service = new SongService(repository);

        service.recordView(saved.id());
        service.recordView(saved.id());

        assertThat(repository.findById(saved.id()).orElseThrow().views()).isEqualTo(2L);
    }
}
