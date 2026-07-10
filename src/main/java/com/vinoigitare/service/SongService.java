package com.vinoigitare.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vinoigitare.model.Genre;
import com.vinoigitare.model.Song;
import com.vinoigitare.storage.SongRepository;
import com.vinoigitare.storage.TabFileMirror;

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
    private final TabFileMirror tabFileMirror;

    public SongService(SongRepository repository, TabFileMirror tabFileMirror) {
        this.repository = repository;
        this.tabFileMirror = tabFileMirror;
    }

    public Optional<Song> load(String id) {
        return repository.findById(id);
    }

    public List<Song> loadAll() {
        return repository.findAll();
    }

    /**
     * Saves the song and mirrors it to its {@code .tab} file (see {@link
     * TabFileMirror}). The lookup by {@code song.id()} before saving is
     * what lets the mirror detect a rename: for a brand-new song, {@code
     * id()} is the legacy {@code "artist - title"} string (never a real
     * database row id), so this finds nothing and {@code previous} is
     * {@code null}; for an edit, it's the song's actual numeric id, so
     * this finds the pre-edit row.
     */
    public Song store(Song song) {
        Song previous = repository.findById(song.id()).orElse(null);
        Song saved = repository.save(song);
        tabFileMirror.mirror(saved, previous);
        return saved;
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
     * Songs in the given genre (Phase 4c). Matched via {@link
     * Genre#resolve(String)}, not raw string equality against {@link
     * Song#genre()} -- that field can hold the current slug, the current
     * label, or (for songs untouched since before the English i18n
     * switch) the original Serbian label text, and all three need to
     * count as the same genre. Songs with no genre assigned never match
     * any genre.
     */
    public List<Song> loadByGenre(Genre genre) {
        return repository.findAll().stream()
                .filter(song -> Genre.resolve(song.genre()).filter(genre::equals).isPresent())
                .sorted(Comparator.comparing(Song::artist, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Song::title, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /**
     * Records a view of the given song (Phase 4e) -- called once per
     * on-screen song-page load (see {@code
     * com.vinoigitare.web.SongBrowseController#song}), deliberately NOT
     * from the PDF download or admin edit/view paths, which shouldn't
     * count as a "view" of the song.
     */
    public void recordView(String id) {
        repository.incrementViews(id);
    }

    /**
     * The {@code limit} most recently created songs (Phase 4e), newest
     * first. Songs with no {@link Song#createdAt()} (only possible for
     * file-backed songs that were never routed through the database) are
     * excluded, since there's no meaningful "newest" ordering for them.
     */
    public List<Song> loadNewest(int limit) {
        return repository.findAll().stream()
                .filter(song -> song.createdAt() != null)
                .sorted(Comparator.comparing(Song::createdAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** The {@code limit} most-viewed songs (Phase 4e), highest view count first. */
    public List<Song> loadMostViewed(int limit) {
        return repository.findAll().stream()
                .sorted(Comparator.comparingLong(Song::views).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
