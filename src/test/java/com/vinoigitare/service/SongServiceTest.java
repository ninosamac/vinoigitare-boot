package com.vinoigitare.service;

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
}
