package com.vinoigitare.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.vinoigitare.VinoigitareProperties;
import com.vinoigitare.model.Song;

/**
 * Flat-file {@link SongRepository}: one file per song under
 * {@code vinoigitare.songs-dir}, filename = {@code song.id() + ".tab"},
 * content = the raw chords/lyrics blob. Ported from the old
 * {@code Vinoigitare_TextFileStorage} module's {@code SongTextFileStorage}
 * (there {@code FolderUtil} did the file I/O; here it's inlined with
 * {@code java.nio.file}, which makes the old helper unnecessary).
 *
 * <p><b>Encoding fix (see the migration plan, section 1):</b> the old
 * {@code Song.getSearchableText()} patched around broken diacritics
 * (literal U+FFFD replacement-character substitutions) instead of fixing
 * the underlying encoding. Here, all reads and writes are explicit UTF-8
 * ({@link StandardCharsets#UTF_8}), never the platform default charset, so
 * that problem cannot recur for data written by this app. Pre-existing
 * {@code .tab} files from the old app still need a one-time encoding audit
 * and conversion before import (Phase 4 in the plan) -- their actual source
 * encoding has not been verified.
 */
@Repository
public class TextFileSongRepository implements SongRepository {

    private static final String FILE_EXTENSION = "tab";
    private static final String ID_SEPARATOR = " - ";

    private final Path songsDir;

    @Autowired
    public TextFileSongRepository(VinoigitareProperties properties) {
        this(Paths.get(properties.songsDir()));
    }

    // Package-private, test-only: lets tests construct against a @TempDir
    // Path directly without going through VinoigitareProperties.
    TextFileSongRepository(Path songsDir) {
        this.songsDir = Objects.requireNonNull(songsDir, "songsDir must not be null");
        try {
            Files.createDirectories(songsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create songs directory: " + songsDir, e);
        }
    }

    @Override
    public Optional<Song> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Path file = fileFor(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(readSong(file));
    }

    @Override
    public List<Song> findAll() {
        try (Stream<Path> files = Files.list(songsDir)) {
            return files
                    .filter(TextFileSongRepository::isSongFile)
                    .map(this::readSong)
                    .sorted(Comparator.comparing(Song::id))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list songs in " + songsDir, e);
        }
    }

    @Override
    public Song save(Song song) {
        Objects.requireNonNull(song, "song must not be null");
        Path file = fileFor(song.id());
        try {
            Files.writeString(file, song.chords(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write song file: " + file, e);
        }
        return song;
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Path file = fileFor(id);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete song file: " + file, e);
        }
    }

    @Override
    public boolean existsById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Files.exists(fileFor(id));
    }

    /**
     * No-op: flat {@code .tab} files have nowhere to persist a view
     * counter (the file content *is* the chords blob, nothing else), and
     * this repository isn't the active one from Phase 4a onward anyway
     * (see the class Javadoc) -- {@link DatabaseSongRepository} is what
     * actually implements view counting.
     */
    @Override
    public void incrementViews(String id) {
        Objects.requireNonNull(id, "id must not be null");
    }

    private static boolean isSongFile(Path path) {
        return path.getFileName().toString().endsWith("." + FILE_EXTENSION);
    }

    private Path fileFor(String id) {
        return songsDir.resolve(id + "." + FILE_EXTENSION);
    }

    private Song readSong(Path file) {
        String fileName = file.getFileName().toString();
        String id = fileName.substring(0, fileName.length() - (FILE_EXTENSION.length() + 1));

        // Best-effort split on the first " - ": matches the old app's
        // behavior for the common case (artist and title don't themselves
        // contain " - "). Unlike the old SongTextFileStorage, which used
        // String.split and silently dropped anything past the second token,
        // this keeps the rest of the id as part of the title.
        int separatorIndex = id.indexOf(ID_SEPARATOR);
        String artist = separatorIndex >= 0 ? id.substring(0, separatorIndex) : id;
        String title = separatorIndex >= 0 ? id.substring(separatorIndex + ID_SEPARATOR.length()) : "";

        String chords;
        try {
            chords = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read song file: " + file, e);
        }
        return new Song(artist, title, chords);
    }
}
