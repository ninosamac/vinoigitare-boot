package com.vinoigitare.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vinoigitare.model.Song;

/**
 * Keeps the database in sync with whatever .tab files exist on disk --
 * additions, edits, and deletions -- in the one direction TabFileMirror
 * doesn't already cover: files that changed without going through the
 * admin panel on THIS instance. Runs once at startup (via
 * ApplicationRunner, e.g. an empty database's very first boot) and then
 * periodically (via Scheduled) for as long as the app keeps running.
 *
 * <p><b>Why periodic reconciliation, not a one-time import (2026-07-12):</b>
 * the original version of this class only ever imported anything if the
 * database was completely empty -- fine for bootstrapping a fresh
 * deployment, but it meant a song added via the admin panel on one
 * machine (which writes that machine's own database, then mirrors a .tab
 * file that a separate, periodic script syncs to GitHub -- see
 * ~/knowledge/projects/vinoigitare/production-deployment.md, section 6)
 * would never appear on a different, already-running instance -- e.g.
 * production -- even after its own sync script pulled the new file,
 * because that instance's database was no longer empty and nothing ever
 * re-read the file store again. A real report of exactly this ("saved a
 * song locally, still not on the domain an hour later") is what surfaced
 * it: the git-level sync was working fine, but nothing connected "a new
 * file landed on disk" to "the running app's database learns about it."
 *
 * <p>Considered dropping the database entirely instead (each instance's
 * own database is what makes this a sync problem in the first place),
 * but it's still the cheapest way to get fast repeated listing
 * (search/homepage/sitemap all call SongService.loadAll()), the stable
 * numeric ids the existing SEO work depends on, and view-count
 * persistence -- so it stays, and this class does the reconciling
 * instead.
 *
 * <p>Matched by artist+title (the only link between a database row and a
 * .tab file, since the file is literally named "Artist - Title.tab"):
 * <ul>
 *   <li>a .tab file with no matching database row gets inserted;
 *   <li>a .tab file matching a database row whose chords text differs
 *       gets that row's chords updated in place -- id/slug/createdAt/
 *       views are preserved, so the song's URL and view count survive an
 *       edit made elsewhere;
 *   <li>a database row with no matching .tab file gets deleted, unless
 *       the file listing looks suspiciously small next to the database
 *       (see {@link #safeToDeleteMissingRows}) -- a circuit breaker
 *       against an incomplete/torn sync (a failed git pull, a
 *       momentarily unmounted directory) being misread as "everything
 *       was deleted."
 * </ul>
 *
 * <p><b>Known, accepted limitation: renames.</b> A rename isn't
 * recognized as "the same song, renamed" on any instance other than the
 * one it happened on -- TabFileMirror deletes the old filename and
 * writes a new one, so reconciliation elsewhere sees an unrelated delete
 * + add, and that song gets a new database id/slug and a reset view
 * count on every other instance. Fixing this would mean giving each song
 * a stable id that travels with the file's content, not its filename --
 * out of scope for now.
 *
 * <p><b>Encoding note (see the migration plan, section 1 and risk list):</b>
 * TextFileSongRepository already reads every .tab file as explicit UTF-8,
 * so no conversion step is needed for the fixture data this app ships
 * with -- it was written as UTF-8 in Phase 1. A real imported corpus
 * (i.e. the old app's actual song files, if they're ever migrated) would
 * need its source encoding audited and, if it's not UTF-8, converted
 * before being handed to this importer -- reading arbitrary legacy .tab
 * files as UTF-8 without first verifying that would silently corrupt any
 * non-ASCII text that wasn't actually UTF-8 to begin with. This importer
 * does not attempt that detection; it trusts its input is already valid
 * UTF-8, same as TextFileSongRepository always has.
 *
 * <p><b>No more genre (removed entirely 2026-07-12):</b> this importer
 * briefly assigned one of three genre categories round-robin at import
 * time, purely so the (now-removed) public genre-browsing tab had
 * something in every category -- never a real, human-curated value. Song
 * no longer has a genre field at all -- see
 * ~/knowledge/projects/vinoigitare/progress.md for the full story.
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
        reconcile();
    }

    /**
     * Re-scans .tab files every 15 minutes for the rest of this instance's
     * uptime -- cheap (a directory listing plus a database read, both
     * already this size in production) and idempotent, so there's no need
     * to coordinate this against the separate sync script's own hourly
     * cron cadence; whatever's on disk gets picked up within, at most, one
     * more cycle of this.
     */
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    void reconcileOnSchedule() {
        reconcile();
    }

    // synchronized: Spring's default @Scheduled behavior fires its first
    // tick immediately at startup, essentially concurrently with the
    // ApplicationRunner call above on a separate thread -- confirmed
    // while testing this (both logged "1 added" for the same restored
    // .tab file at the same timestamp, and it was inserted twice, as two
    // separate rows, before either thread's own insert was visible to
    // the other's read of the database). One reconciliation pass at a
    // time removes that race; a pass only ever takes a directory listing
    // plus a handful of database calls, so serializing them costs
    // nothing that matters.
    private synchronized void reconcile() {
        List<Song> dbSongs = repository.findAll();
        Map<String, Song> dbByKey = new HashMap<>();
        for (Song dbSong : dbSongs) {
            dbByKey.put(key(dbSong), dbSong);
        }

        List<Song> fileSongs = fileRepository.findAll();
        Set<String> fileKeys = new HashSet<>();
        int added = 0;
        int updated = 0;

        for (Song fileSong : fileSongs) {
            fileKeys.add(key(fileSong));
            Song existing = dbByKey.get(key(fileSong));
            if (existing == null) {
                repository.save(fileSong);
                added++;
            } else if (!existing.chords().equals(fileSong.chords())) {
                // Preserve id/slug/createdAt/views -- only the chords
                // text changes, so the song's URL and view count survive
                // an edit made on a different instance.
                repository.save(new Song(existing.id(), existing.artist(), existing.title(),
                        existing.slug(), fileSong.chords(), existing.createdAt(), existing.views()));
                updated++;
            }
        }

        int removed = 0;
        if (safeToDeleteMissingRows(fileSongs.size(), dbSongs.size())) {
            for (Song dbSong : dbSongs) {
                if (!fileKeys.contains(key(dbSong))) {
                    repository.delete(dbSong.id());
                    removed++;
                }
            }
        } else if (!dbSongs.isEmpty()) {
            log.warn(".tab file count (" + fileSongs.size() + ") looks too low next to the database ("
                    + dbSongs.size() + " rows) -- skipping deletion this cycle in case the sync is incomplete.");
        }

        log.info("Reconciled .tab files with the database: " + added + " added, " + updated + " updated, "
                + removed + " removed.");
    }

    /**
     * Circuit breaker against a torn or incomplete sync (a failed git
     * pull, a momentarily unmounted directory) being misread as "every
     * song was deleted": refuses to delete anything if the file listing
     * comes back at less than half the database's row count. An empty
     * database has nothing to accidentally wipe, so that case is always
     * safe.
     */
    private static boolean safeToDeleteMissingRows(int fileSongCount, int dbSongCount) {
        if (dbSongCount == 0) {
            return true;
        }
        return fileSongCount >= dbSongCount / 2;
    }

    // Same "artist - title" convention Song's own compact constructor
    // already uses to derive a file-backed song's id -- reused here so
    // both a database row and a .tab file for the same song produce an
    // identical key, without introducing a second, different composite-key
    // format into the codebase.
    private static String key(Song song) {
        return song.artist() + " - " + song.title();
    }
}
