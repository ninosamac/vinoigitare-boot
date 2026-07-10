package com.vinoigitare.web;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.model.Genre;
import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("fast")
@WebMvcTest(GenreBrowseController.class)
class GenreBrowseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongService songService;

    @Test
    void genreIndexListsAllThreeGenresWithCounts() throws Exception {
        given(songService.loadByGenre(Genre.POP_ROCK)).willReturn(List.of(
                new Song("Marko Markovic", "Probna pesma", "chords")));
        given(songService.loadByGenre(Genre.NARODNO)).willReturn(List.of());
        given(songService.loadByGenre(Genre.STRANO)).willReturn(List.of());

        mockMvc.perform(get("/genres"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pop/Rock")))
                .andExpect(content().string(containsString("Folk")))
                .andExpect(content().string(containsString("Foreign")));
    }

    @Test
    void lettersPageListsDistinctFirstLettersForGenre() throws Exception {
        given(songService.loadByGenre(Genre.POP_ROCK)).willReturn(List.of(
                new Song("Marko Markovic", "Probna pesma", "chords"),
                new Song("Đorđe Đokić", "Šašava priča", "chords")));

        mockMvc.perform(get("/genres/{slug}", Genre.POP_ROCK.slug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/genres/pop-rock/M")))
                .andExpect(content().string(containsString("/genres/pop-rock/%C4%90")));
    }

    @Test
    void lettersPageReturns404ForUnknownGenreSlug() throws Exception {
        mockMvc.perform(get("/genres/{slug}", "not-a-real-genre"))
                .andExpect(status().isNotFound());
    }

    @Test
    void artistsForLetterListsMatchingArtistsWithSongCounts() throws Exception {
        given(songService.loadByGenre(Genre.POP_ROCK)).willReturn(List.of(
                new Song("Marko Markovic", "Probna pesma", "chords"),
                new Song("Marko Markovic", "Druga pesma", "chords"),
                new Song("Ana Anic", "Treca pesma", "chords")));

        mockMvc.perform(get("/genres/{slug}/{letter}", Genre.POP_ROCK.slug(), "M"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marko Markovic")))
                .andExpect(content().string(containsString("2")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Ana Anic"))));
    }
}
