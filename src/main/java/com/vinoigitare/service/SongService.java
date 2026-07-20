package com.vinoigitare.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vinoigitare.model.CroatianCollator;
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

    /**
     * Deletes the song from the database and its {@code .tab} file both --
     * see {@link TabFileMirror#remove}'s Javadoc for why the file goes too,
     * now that the {@code .tab} files are the actual collection and the
     * database is just a rebuildable cache over them. Pushing that change
     * to the collection's GitHub mirror is a separate, manual/periodic
     * step (a script, not app code) -- see
     * {@code ~/knowledge/projects/vinoigitare/dev-cheatsheet.md}.
     */
    public void remove(String id) {
        repository.findById(id).ifPresent(tabFileMirror::remove);
        repository.delete(id);
    }

    public boolean contains(String id) {
        return repository.existsById(id);
    }

    /**
     * All songs grouped by artist, artists in real alphabetical order (per
     * {@link CroatianCollator}, not raw character code) and each artist's
     * songs sorted by title. Replaces the old {@code SongTree}'s
     * {@code TreeMap<String, TreeSet<Song>>}.
     *
     * <p>Locale-aware collation on purpose, not just case-insensitive: a
     * plain {@code TreeMap::new} (natural {@code String} ordering) sorts by
     * raw character code, so any inconsistently-cased artist name would
     * land in the wrong place relative to its neighbors (all-uppercase
     * names sort before any lowercase one, even alphabetically later) --
     * exactly the kind of thing that only becomes visible once there are
     * enough artists for it to matter, per the homepage artist-tree
     * redesign this feeds. {@code String.CASE_INSENSITIVE_ORDER} fixed
     * that but introduced a second, real bug of its own: it's still raw
     * code-point comparison, which sorts every {@code č ć đ š ž} name after
     * every plain-Z one instead of where the real alphabet places them --
     * see {@link CroatianCollator}'s own Javadoc.
     */
    public Map<String, List<Song>> loadAllGroupedByArtist() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Song::title, CroatianCollator.stringComparator()))
                .collect(Collectors.groupingBy(Song::artist, () -> new TreeMap<>(CroatianCollator.stringComparator()),
                        Collectors.toList()));
    }

    public List<Song> loadByArtist(String artist) {
        return repository.findAll().stream()
                .filter(song -> song.artist().equals(artist))
                .sorted(Comparator.comparing(Song::title, CroatianCollator.stringComparator()))
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
}
