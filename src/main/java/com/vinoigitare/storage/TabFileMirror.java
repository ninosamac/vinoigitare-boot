package com.vinoigitare.storage;

import org.springframework.stereotype.Component;

import com.vinoigitare.model.Song;

/**
 * Keeps each database-backed song's flat-file {@code .tab} mirror in sync
 * whenever it's saved or deleted through {@link
 * com.vinoigitare.service.SongService}, so the on-disk {@code .tab} store
 * stays a genuine, current backup of the database (the migration plan's own
 * words: "{@code .tab} import/export retained as backup format") rather
 * than a one-time snapshot of whatever existed at first import.
 *
 * <p><b>Nino's framing (2026-07-12):</b> the {@code .tab} files are the
 * actual collection; the database is a rebuildable cache over them ({@link
 * SongImporter} already only imports when the database is empty, so
 * deleting the DB file and restarting fully reconstructs it from these
 * files). This class is what keeps that true for writes and deletes both.
 * Pushing these files to their dedicated GitHub mirror is a separate,
 * manual/periodic script rather than app code -- see {@code
 * ~/knowledge/projects/vinoigitare/dev-cheatsheet.md} -- so that a slow
 * network or GitHub outage can never affect an admin save/delete request.
 *
 * <p><b>Deliberately NOT hooked into {@link DatabaseSongRepository#save}
 * itself.</b> {@link SongImporter} calls that directly, bypassing {@code
 * SongService}, to seed the database <i>from</i> existing {@code .tab}
 * files on first startup. If this mirroring lived at the repository layer
 * instead, importing a song would immediately write the very file it was
 * just read from straight back to itself -- pointless churn at best, and
 * exactly the import/export cycle this needs to avoid at worst (every
 * startup re-touching every fixture file for no reason). Living at the
 * service layer instead means only real writes -- today, just the admin
 * create/edit forms -- ever trigger a mirror write; {@code SongImporter}'s
 * one-time bootstrap read is structurally incapable of looping back into
 * it.
 */
@Component
public class TabFileMirror {

    private final TextFileSongRepository fileRepository;

    public TabFileMirror(TextFileSongRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /**
     * Writes {@code saved}'s current chords to its {@code .tab} file
     * (named after its artist/title, the legacy convention {@link
     * TextFileSongRepository} still uses -- never {@code saved.id()}
     * itself, which for a database-backed song is a numeric id, not a
     * filename). If {@code previous} is given and its artist/title differ
     * from {@code saved}'s, the stale file under the old name is deleted
     * first, so renaming a song doesn't leave an orphaned duplicate behind.
     *
     * @param previous the song's state before this save, or {@code null}
     *                 for a brand-new song (nothing to rename away from)
     */
    public void mirror(Song saved, Song previous) {
        if (previous != null
                && (!previous.artist().equals(saved.artist()) || !previous.title().equals(saved.title()))) {
            fileRepository.delete(previous.artist() + " - " + previous.title());
        }
        fileRepository.save(new Song(saved.artist(), saved.title(), saved.chords()));
    }

    /**
     * Deletes {@code song}'s {@code .tab} file. Companion to {@link
     * #mirror}, for the other half of keeping the flat-file store a
     * genuine mirror of the database: a song removed via the admin panel
     * should really be gone, not leave an orphaned file behind that a
     * future {@link SongImporter} run would just reimport.
     */
    public void remove(Song song) {
        fileRepository.delete(song.artist() + " - " + song.title());
    }
}
