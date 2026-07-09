package com.vinoigitare.storage;

import java.util.List;
import java.util.Optional;

import com.vinoigitare.model.Song;

/**
 * Persistence abstraction for {@link Song}s.
 *
 * <p>Replaces the old generic {@code Storage<T extends Storable>} interface
 * from {@code Vinoigitare_ServicesAPI}. That generality bought nothing here
 * -- there was only ever one implementation and one entity -- so this is
 * specific to {@code Song} rather than a re-genericized port.
 */
public interface SongRepository {

    Optional<Song> findById(String id);

    List<Song> findAll();

    Song save(Song song);

    void delete(String id);

    boolean existsById(String id);

    /**
     * Atomically increments the view counter for the song with the given
     * id (Phase 4e), silently doing nothing if no such song exists. A
     * dedicated atomic operation rather than "load, add 1 in Java, {@link
     * #save(Song)} the result" deliberately -- the latter has a
     * read-then-write race: two concurrent page loads could both read the
     * same starting count and both write back the same incremented value,
     * losing one view.
     */
    void incrementViews(String id);
}
