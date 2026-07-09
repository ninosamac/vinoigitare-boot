package com.vinoigitare.storage;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.vinoigitare.model.Genre;
import com.vinoigitare.model.Song;

/**
 * One-time importer: on startup, if the database is empty, reads every
 * song out of the flat-file {@code .tab} store (via {@link
 * TextFileSongRepository}, the Phase 1-3 backing store) and inserts them
 * into the database (via {@link SongRepository}, which resolves to the
 * {@code @Primary} {@link DatabaseSongRepository} from Phase 4a onward).
 * Each imported song gets a fresh numeric id and slug assigned by the
 * database repository.
 *
 * <p>Idempotent by construction: it only runs when {@code
 * SongRepository.findAll()} is empty, so restarting the app doesn't
 * re-import or duplicate rows.
 *
 * <p><b>Encoding note (see the migration plan, section 1 and risk list):</b>
 * {@link TextFileSongRepository} already reads every {@code .tab} file as
 * explicit UTF-8, so no conversion step is needed for the fixture data this
 * app ships with -- it was written as UTF-8 in Phase 1. A real imported
 * corpus (i.e. the old app's actual song files, if they're ever migrated)
 * would need its source encoding audited and, if it's not UTF-8, converted
 * *before* being handed to this importer -- reading arbitrary legacy
 * `.tab` files as UTF-8 without first verifying that would silently
 * corrupt any non-ASCII text that wasn't actually UTF-8 to begin with.
 * This importer does not attempt that detection; it trusts its input is
 * already valid UTF-8, same as {@link TextFileSongRepository} always has.
 *
 * <p><b>Genre assignment (Phase 4c):</b> {@code .tab} files carry no genre
 * metadata (they're just a raw chords/lyrics blob), and there's no real
 * corpus to derive genres from anyway. Imported songs are assigned one of
 * the three {@link Genre} categories round-robin, in file order -- purely
 * functional (so every category has at least one song to browse) rather
 * than musically meaningful. A real import would instead carry genre data
 * from wherever the source `.tab` files' metadata lives, if any.
 */
@Component
public class SongImporter implements ApplicationRunner {

    private static final Log log = LogFactory.getLog(SongImporter.class.getName());

    private final TextFileSongRepository fileRepository;
    private final SongRepository repository;

    public SongImporter(TextFileSongRepository fileRepository, SongRepository repository) {
        this.fileRepository = fileRepository;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!repository.findAll().isEmpty()) {
            log.info("Song database already populated; skipping .tab import.");
            return;
        }

        List<Song> fileSongs = fileRepository.findAll();
        if (fileSongs.isEmpty()) {
            log.info("No .tab files found to import.");
            return;
        }

        log.info("Importing " + fileSongs.size() + " song(s) from .tab files into the database.");
        Genre[] genres = Genre.values();
        for (int i = 0; i < fileSongs.size(); i++) {
            Song song = fileSongs.get(i);
            Genre genre = genres[i % genres.length];
            Song withGenre = new Song(song.id(), song.artist(), song.title(), song.slug(), genre.label(),
                    song.chords(), song.createdAt(), song.views());
            repository.save(withGenre);
        }
    }
}
