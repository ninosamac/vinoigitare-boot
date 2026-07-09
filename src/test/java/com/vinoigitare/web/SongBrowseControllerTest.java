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

import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.containsString;
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

        mockMvc.perform(get("/songs/{id}", song.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<pre")))
                .andExpect(content().string(containsString("line one")));
    }

    @Test
    void songViewReturns404WhenSongIsMissing() throws Exception {
        given(songService.load("Unknown - Song")).willReturn(Optional.empty());

        mockMvc.perform(get("/songs/{id}", "Unknown - Song"))
                .andExpect(status().isNotFound());
    }
}
