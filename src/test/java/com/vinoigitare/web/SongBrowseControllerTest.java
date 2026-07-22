package com.vinoigitare.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.vinoigitare.chords.ChordLineHighlighter;
import com.vinoigitare.model.Song;
import com.vinoigitare.security.SecurityConfig;
import com.vinoigitare.service.SongService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest only scans web-layer beans (controllers and related
// infrastructure) by default -- ChordLineHighlighter is a plain
// @Component, not part of that slice, but song.html's template now
// references it directly (via @chordLineHighlighter.render(...)), so it
// has to be explicitly provided to this trimmed-down context. A nested
// @TestConfiguration @Bean method registers it reliably (a bare
// @Import(ChordLineHighlighter.class) here did not make it resolvable to
// Thymeleaf's @beanName SpEL resolver in this slice, for reasons not
// fully tracked down -- this is the more standard pattern anyway).
// SecurityConfig also needs importing explicitly, same as every other
// public-route @WebMvcTest (see SearchControllerTest's comment) -- without
// it this slice falls back to "authenticate everything" and 401s.
@Tag("fast")
@WebMvcTest(SongBrowseController.class)
@Import({SongBrowseControllerTest.ExtraBeans.class, SecurityConfig.class})
class SongBrowseControllerTest {

    @TestConfiguration
    static class ExtraBeans {
        @Bean
        ChordLineHighlighter chordLineHighlighter() {
            return new ChordLineHighlighter();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongService songService;

    @Test
    void indexListsArtistsGroupedFromService() throws Exception {
        Song song = new Song("Marko Markovic", "Probna pesma", "chords");
        given(songService.loadAllGroupedByArtist()).willReturn(Map.of("Marko Markovic", List.of(song)));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marko Markovic")));
    }

    @Test
    void indexGroupsArtistsByFirstLetterAndReportsTotals() throws Exception {
        Map<String, List<Song>> byArtist = new LinkedHashMap<>();
        byArtist.put("Ana Anic", List.of(new Song("Ana Anic", "Solo Song", "chords")));
        byArtist.put("Marko Markovic",
                List.of(new Song("Marko Markovic", "First Song", "chords"),
                        new Song("Marko Markovic", "Second Song", "chords")));
        given(songService.loadAllGroupedByArtist()).willReturn(byArtist);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // One letter-heading per distinct first letter, not one per artist.
                .andExpect(content().string(containsString("letter-heading\">A<")))
                .andExpect(content().string(containsString("letter-heading\">M<")))
                .andExpect(content().string(containsString("2 artists")))
                .andExpect(content().string(containsString("3 songs")));
    }

    @Test
    void indexOrdersDiacriticLetterHeadingsByRealAlphabetPositionNotCodePoint() throws Exception {
        // Real bug found 2026-07-19 (Nino): TreeMap's default Character
        // ordering is raw code-point comparison, which put Č/Đ/Š/Ž letter
        // headings all after Z (their code points are in the Latin
        // Extended-A block, well past 'z') instead of where the real
        // alphabet places them -- Č right after C, Đ right after D, Š
        // right after S; only Ž genuinely belongs at the very end. See
        // CroatianCollator's own Javadoc.
        Map<String, List<Song>> byArtist = new LinkedHashMap<>();
        byArtist.put("Cune", List.of(new Song("Cune", "Song", "chords")));
        byArtist.put("Čola", List.of(new Song("Čola", "Song", "chords")));
        byArtist.put("Sarajevo", List.of(new Song("Sarajevo", "Song", "chords")));
        byArtist.put("Šaban Šaulić", List.of(new Song("Šaban Šaulić", "Song", "chords")));
        byArtist.put("Zana", List.of(new Song("Zana", "Song", "chords")));
        byArtist.put("Žan", List.of(new Song("Žan", "Song", "chords")));
        given(songService.loadAllGroupedByArtist()).willReturn(byArtist);

        MvcResult result = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();

        int cHeading = html.indexOf("letter-heading\">C<");
        int cWithCaronHeading = html.indexOf("letter-heading\">Č<");
        int sHeading = html.indexOf("letter-heading\">S<");
        int sWithCaronHeading = html.indexOf("letter-heading\">Š<");
        int zHeading = html.indexOf("letter-heading\">Z<");
        int zWithCaronHeading = html.indexOf("letter-heading\">Ž<");

        assertThat(cHeading).isGreaterThan(-1).isLessThan(cWithCaronHeading);
        assertThat(cWithCaronHeading).isLessThan(sHeading);
        assertThat(sHeading).isLessThan(sWithCaronHeading);
        assertThat(sWithCaronHeading).isLessThan(zHeading);
        assertThat(zHeading).isLessThan(zWithCaronHeading);
    }

    @Test
    void artistPageListsSongTitles() throws Exception {
        Song song = new Song("Marko Markovic", "Probna pesma", "chords");
        given(songService.loadByArtist("Marko Markovic")).willReturn(List.of(song));

        mockMvc.perform(get("/artists/{artist}", "Marko Markovic"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Probna pesma")));
    }

    @Test
    void artistPageIncludesMetaDescriptionAndCanonicalLink() throws Exception {
        // SEO (2026-07-20, "{artist name} akordi" searches): a real
        // diacritic-heavy artist name, not just an ASCII fixture -- this
        // exercises Thymeleaf's own @{...} URL-encoding of the canonical
        // link, same reasoning as this project's other diacritic tests.
        Song song1 = new Song("Đorđe Balašević", "Prva pesma", "chords");
        Song song2 = new Song("Đorđe Balašević", "Druga pesma", "chords");
        given(songService.loadByArtist("Đorđe Balašević")).willReturn(List.of(song1, song2));

        mockMvc.perform(get("/artists/{artist}", "Đorđe Balašević"))
                .andExpect(status().isOk())
                // CTR fix (2026-07-22, issue #11): title now includes the
                // keyword ("Guitar Chords & Lyrics"), not just the bare
                // artist name.
                .andExpect(content().string(containsString(
                        "<title>Đorđe Balašević - Guitar Chords &amp; Lyrics - Vino i gitare</title>")))
                .andExpect(content().string(containsString("<meta name=\"description\"")))
                // The song count (2) is the concrete fact filling this
                // description, same role the per-song version's first
                // lyric line plays.
                .andExpect(content().string(containsString("2 songs")))
                .andExpect(content().string(containsString("<link rel=\"canonical\"")))
                .andExpect(content().string(containsString("/artists/")));
    }

    @Test
    void songViewRendersChordsInPreformattedBlock() throws Exception {
        Song song = new Song("Marko Markovic", "Probna pesma", "C G\nline one");
        given(songService.load(song.id())).willReturn(Optional.of(song));

        mockMvc.perform(get("/akordi/{id}/{slug}", song.id(), song.slug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<pre")))
                .andExpect(content().string(containsString("line one")));
    }

    @Test
    void songViewListsChordsUsedWithPlayButtonsMatchedAgainstTheCatalog() throws Exception {
        // Chord playback (2026-07-22, issue #13): C is -1,3,2,0,1,0 in the
        // curated catalog (see ChordDiagramCatalog#majors) -- confirms the
        // song's own chords reach the page with real fret data attached.
        Song song = new Song("Marko Markovic", "Probna pesma", "C       G\nline one");
        given(songService.load(song.id())).willReturn(Optional.of(song));

        mockMvc.perform(get("/akordi/{id}/{slug}", song.id(), song.slug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-song-chords")))
                .andExpect(content().string(containsString("data-original-name=\"C\"")))
                .andExpect(content().string(containsString("data-frets=\"-1,3,2,0,1,0\"")))
                .andExpect(content().string(containsString("data-original-name=\"G\"")));
    }

    @Test
    void songViewOmitsChordsTheCatalogDoesNotCover() throws Exception {
        // "no fake data" (same principle as issue #10): C11 is a
        // grammar-valid chord token ChordTransposer would happily detect,
        // but the curated catalog has no verified fingering for it -- must
        // be silently dropped, not shown with a broken Play button.
        Song song = new Song("Marko Markovic", "Egzoticna pesma", "C11\nline one");
        given(songService.load(song.id())).willReturn(Optional.of(song));

        mockMvc.perform(get("/akordi/{id}/{slug}", song.id(), song.slug()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-song-chord-row"))));
    }

    @Test
    void songViewHidesChordsUsedSectionWhenNoChordsAreDetected() throws Exception {
        Song song = new Song("Marko Markovic", "Samo tekst", "Ovo je tekst pesme bez akorda");
        given(songService.load(song.id())).willReturn(Optional.of(song));

        mockMvc.perform(get("/akordi/{id}/{slug}", song.id(), song.slug()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-song-chords"))));
    }

    @Test
    void songViewIncludesMoreByArtistSectionListingOtherSongsNotItself() throws Exception {
        // Internal linking (2026-07-22, issue #10): the current song must
        // not list itself as "more by the artist".
        Song current = new Song("Marko Markovic", "Trenutna pesma", "chords");
        Song other1 = new Song("Marko Markovic", "Prva druga pesma", "chords");
        Song other2 = new Song("Marko Markovic", "Druga druga pesma", "chords");
        given(songService.load(current.id())).willReturn(Optional.of(current));
        given(songService.loadByArtist("Marko Markovic")).willReturn(List.of(current, other1, other2));

        mockMvc.perform(get("/akordi/{id}/{slug}", current.id(), current.slug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marko Markovic")))
                .andExpect(content().string(containsString("Prva druga pesma")))
                .andExpect(content().string(containsString("Druga druga pesma")))
                // Only 2 others exist (below the cap of 8) -- no "see all" link needed.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("See all"))));
    }

    @Test
    void songViewCapsMoreByArtistListAndShowsSeeAllLinkWhenOverTheCap() throws Exception {
        // Internal linking (2026-07-22, issue #10): capped at 8 -- some
        // artists have 50+ songs (Đorđe Balašević alone), and dumping the
        // whole list on every one of their song pages would bury the
        // actual chords content the page exists for.
        Song current = new Song("Marko Markovic", "Trenutna pesma", "chords");
        List<Song> others = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            others.add(new Song("Marko Markovic", "Pesma broj " + i, "chords"));
        }
        List<Song> all = new ArrayList<>();
        all.add(current);
        all.addAll(others);
        given(songService.load(current.id())).willReturn(Optional.of(current));
        given(songService.loadByArtist("Marko Markovic")).willReturn(all);

        mockMvc.perform(get("/akordi/{id}/{slug}", current.id(), current.slug()))
                .andExpect(status().isOk())
                // First 8 others shown...
                .andExpect(content().string(containsString("Pesma broj 1")))
                .andExpect(content().string(containsString("Pesma broj 8")))
                // ...the 9th (beyond the cap) is not.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Pesma broj 9"))))
                // "See all" promises the artist's real total (10 = current + 9 others).
                .andExpect(content().string(containsString("See all 10 songs by Marko Markovic")));
    }

    @Test
    void songViewHidesMoreByArtistSectionWhenArtistHasNoOtherSongs() throws Exception {
        Song song = new Song("Marko Markovic", "Jedina pesma", "chords");
        given(songService.load(song.id())).willReturn(Optional.of(song));
        given(songService.loadByArtist("Marko Markovic")).willReturn(List.of(song));

        mockMvc.perform(get("/akordi/{id}/{slug}", song.id(), song.slug()))
                .andExpect(status().isOk())
                // Checks the actual interpolated heading text, not the
                // bare phrase "More by" -- this file's own explanatory
                // comment on the section happens to contain that phrase
                // too, which would otherwise false-match here.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("More by Marko Markovic"))));
    }

    @Test
    void songViewRecordsAViewOnSuccessButNotOnMiss() throws Exception {
        Song song = new Song("Marko Markovic", "Probna pesma", "chords");
        given(songService.load(song.id())).willReturn(Optional.of(song));
        given(songService.load("999")).willReturn(Optional.empty());

        mockMvc.perform(get("/akordi/{id}/{slug}", song.id(), song.slug())).andExpect(status().isOk());
        then(songService).should().recordView(song.id());

        mockMvc.perform(get("/akordi/{id}/{slug}", "999", "unknown")).andExpect(status().isNotFound());
        then(songService).should(org.mockito.Mockito.never()).recordView("999");
    }

    @Test
    void songViewReturns404WhenSongIsMissing() throws Exception {
        given(songService.load("42")).willReturn(Optional.empty());

        mockMvc.perform(get("/akordi/{id}/{slug}", "42", "unknown-song"))
                .andExpect(status().isNotFound());
    }

    @Test
    void liveViewRendersChordsAndHasNoNavbar() throws Exception {
        Song song = new Song("Marko Markovic", "Probna pesma", "C G\nline one");
        given(songService.load(song.id())).willReturn(Optional.of(song));

        mockMvc.perform(get("/akordi/{id}/live", song.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<pre")))
                .andExpect(content().string(containsString("line one")))
                .andExpect(content().string(containsString("data-fullscreen-toggle")))
                // No shared nav fragment on this page -- it's deliberately
                // chrome-free (see the class Javadoc on the controller method).
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("navbar-brand"))));
    }

    @Test
    void liveViewRecordsAViewOnSuccessAndReturns404WhenSongIsMissing() throws Exception {
        Song song = new Song("Marko Markovic", "Probna pesma", "chords");
        given(songService.load(song.id())).willReturn(Optional.of(song));
        given(songService.load("999")).willReturn(Optional.empty());

        mockMvc.perform(get("/akordi/{id}/live", song.id())).andExpect(status().isOk());
        then(songService).should().recordView(song.id());

        mockMvc.perform(get("/akordi/{id}/live", "999")).andExpect(status().isNotFound());
    }

    @Test
    void songViewIncludesMetaDescriptionAndCanonicalLink() throws Exception {
        Song song = new Song("Marko Markovic", "Probna pesma", "C G\nOvo je stvarni tekst pesme.");
        given(songService.load(song.id())).willReturn(Optional.of(song));

        mockMvc.perform(get("/akordi/{id}/{slug}", song.id(), song.slug()))
                .andExpect(status().isOk())
                // CTR fix (2026-07-22, issue #11): title now includes the
                // keyword ("Chords & Lyrics"), not just artist/title.
                .andExpect(content().string(containsString(
                        "<title>Marko Markovic - Probna pesma: Chords &amp; Lyrics - Vino i gitare</title>")))
                .andExpect(content().string(containsString("<meta name=\"description\"")))
                .andExpect(content().string(containsString("Marko Markovic - Probna pesma")))
                // The chord-only first line ("C G") must not leak into the
                // description; the first real lyric line should.
                .andExpect(content().string(containsString("Ovo je stvarni tekst pesme.")))
                .andExpect(content().string(containsString("<link rel=\"canonical\"")))
                // song.id() here is the legacy "artist - title" string (this
                // test never goes through the real database), which
                // Thymeleaf correctly URL-encodes (spaces -> %20) -- assert
                // on the slug suffix, which has no such characters, rather
                // than hand-encoding the id in the assertion too.
                .andExpect(content().string(containsString("/" + song.slug() + "\"")));
    }
}
