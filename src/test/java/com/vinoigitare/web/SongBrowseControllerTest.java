package com.vinoigitare.web;

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

import com.vinoigitare.chords.ChordLineHighlighter;
import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

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
@Tag("fast")
@WebMvcTest(SongBrowseController.class)
@Import(SongBrowseControllerTest.ExtraBeans.class)
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
    void artistPageListsSongTitles() throws Exception {
        Song song = new Song("Marko Markovic", "Probna pesma", "chords");
        given(songService.loadByArtist("Marko Markovic")).willReturn(List.of(song));

        mockMvc.perform(get("/artists/{artist}", "Marko Markovic"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Probna pesma")));
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
    void indexShowsNewestAndPopularWhenPresent() throws Exception {
        Song newest = new Song("New Artist", "New Song", "chords");
        Song popular = new Song("Pop Artist", "Popular Song", "chords");
        given(songService.loadAllGroupedByArtist()).willReturn(Map.of());
        given(songService.loadNewest(5)).willReturn(List.of(newest));
        given(songService.loadMostViewed(5)).willReturn(List.of(popular));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New Song")))
                .andExpect(content().string(containsString("Popular Song")));
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
