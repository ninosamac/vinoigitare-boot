package com.vinoigitare.search;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vinoigitare.VinoigitareProperties;
import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;
import com.vinoigitare.storage.SongRepository;
import com.vinoigitare.storage.TabFileMirror;
import com.vinoigitare.storage.TextFileSongRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class SearchServiceTest {

    private SearchService searchServiceWithFixtures(Path dir, Song... songs) {
        SongRepository repository = new TextFileSongRepository(new VinoigitareProperties(dir.toString()));
        for (Song song : songs) {
            repository.save(song);
        }
        return new SearchService(new SongService(repository, new TabFileMirror(new TextFileSongRepository(dir))));
    }

    @Test
    void asciiQueryFindsSongWithDiacritics(@TempDir Path tempDir) {
        SearchService search = searchServiceWithFixtures(tempDir,
                new Song("Čačak Bend", "Neka pesma", "G D Em C\nTekst"));

        List<Song> results = search.search("cacak");

        assertThat(results).extracting(Song::artist).containsExactly("Čačak Bend");
    }

    @Test
    void diacriticQueryFindsPlainAsciiSong(@TempDir Path tempDir) {
        SearchService search = searchServiceWithFixtures(tempDir,
                new Song("Cacak Bend", "Neka pesma", "G D Em C\nTekst"));

        List<Song> results = search.search("čačak");

        assertThat(results).extracting(Song::artist).containsExactly("Cacak Bend");
    }

    @Test
    void multiTermQueryIsAndedAcrossFields(@TempDir Path tempDir) {
        SearchService search = searchServiceWithFixtures(tempDir,
                new Song("Marko Markovic", "Probna pesma", "G D Em C\nakordi iznad teksta"),
                new Song("Ana Anic", "Druga pesma", "Am F C G\ndrugi tekst"));

        // "marko" only matches the artist of the first song; "akordi" only
        // matches its chords -- both terms must match the SAME song.
        List<Song> both = search.search("marko akordi");
        assertThat(both).extracting(Song::artist).containsExactly("Marko Markovic");

        // A third, made-up term that matches nothing excludes everything.
        List<Song> none = search.search("marko akordi nepostojeciterm");
        assertThat(none).isEmpty();
    }

    @Test
    void resultsOrderDiacriticArtistsByRealAlphabetPositionNotCodePoint(@TempDir Path tempDir) {
        // Real bug found 2026-07-19 (Nino): String.CASE_INSENSITIVE_ORDER
        // is raw code-point comparison, which sorted every č/ć/đ/š/ž artist
        // after every plain-Z one instead of where the real
        // Croatian/Serbian alphabet places them. See CroatianCollator's
        // own Javadoc.
        SearchService search = searchServiceWithFixtures(tempDir,
                new Song("Zana", "Neka pesma", "chords"),
                new Song("Čola", "Neka pesma", "chords"),
                new Song("Sarajevo", "Neka pesma", "chords"));

        List<Song> results = search.search("pesma");

        assertThat(results).extracting(Song::artist).containsExactly("Čola", "Sarajevo", "Zana");
    }

    @Test
    void blankOrNullQueryReturnsNoResults(@TempDir Path tempDir) {
        SearchService search = searchServiceWithFixtures(tempDir,
                new Song("Marko Markovic", "Probna pesma", "chords"));

        assertThat(search.search("")).isEmpty();
        assertThat(search.search("   ")).isEmpty();
        assertThat(search.search(null)).isEmpty();
    }
}
