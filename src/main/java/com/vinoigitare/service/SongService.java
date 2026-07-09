package com.vinoigitare.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vinoigitare.model.Song;
import com.vinoigitare.storage.SongRepository;

/**
 * Thin service layer over {@link SongRepository}, mirroring the role of the
 * old {@code SongService}/{@code TextFileSongService} pair from the
 * {@code Vinoigitare} module.
 *
 * <p>Dropped from the old implementation: the {@code SongServiceCache}
 * (an in-memory {@code Map} cache -- premature for a flat-file store this
 * size; revisit only if profiling ever shows it's needed), the custom
 * {@code EventBus} publish calls (no cross-component pub-sub needed in a
 * request/response web app), and the {@code Criteria}-based {@code load}
 * overload (replaced by simple stream filtering here and by
 * {@code com.vinoigitare.search} in Phase 2).
 */
@Service
public class SongService {

    private final SongRepository repository;

    public SongService(SongRepository repository) {
        this.repository = repository;
    }

    public Optional<Song> load(String id) {
        return repository.findById(id);
    }

    public List<Song> loadAll() {
        return repository.findAll();
    }

    public Song store(Song song) {
        return repository.save(song);
    }

    public void remove(String id) {
        repository.delete(id);
    }

    public boolean contains(String id) {
        return repository.existsById(id);
    }

    /**
     * All songs grouped by artist, artists in natural (TreeMap) order and
     * each artist's songs sorted by title. Replaces the old
     * {@code SongTree}'s {@code TreeMap<String, TreeSet<Song>>}.
     */
    public Map<String, List<Song>> loadAllGroupedByArtist() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Song::title, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.groupingBy(Song::artist, TreeMap::new, Collectors.toList()));
    }

    public List<Song> loadByArtist(String artist) {
        return repository.findAll().stream()
                .filter(song -> song.artist().equals(artist))
                .sorted(Comparator.comparing(Song::title, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /**
     * Songs in the given genre (Phase 4c), matched against {@link
     * Song#genre()}'s display label (e.g. {@code "Pop/Rock"} -- see {@link
     * com.vinoigitare.model.Genre}). Songs with no genre assigned never
     * match any genre.
     */
    public List<Song> loadByGenre(String genreLabel) {
        return repository.findAll().stream()
                .filter(song -> genreLabel.equals(song.genre()))
                .sorted(Comparator.comparing(Song::artist, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Song::title, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }
}
