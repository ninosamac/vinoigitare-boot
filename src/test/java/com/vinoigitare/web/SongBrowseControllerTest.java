package com.vinoigitare.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("fast")
@WebMvcTest(SongBrowseController.class)
class SongBrowseControllerTest {

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
