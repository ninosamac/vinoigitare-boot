package com.vinoigitare.web;

import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.model.Song;
import com.vinoigitare.pdf.SongPdfRenderer;
import com.vinoigitare.service.SongService;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("fast")
@WebMvcTest(SongPdfController.class)
class SongPdfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongService songService;

    @MockitoBean
    private SongPdfRenderer pdfRenderer;

    @Test
    void pdfResponseHasCorrectContentTypeAndRfc6266FilenameFallback() throws Exception {
        Song song = new Song("Đorđe Đokić", "Šašava priča", "chords");
        given(songService.load(song.id())).willReturn(Optional.of(song));
        given(pdfRenderer.render(eq(song), anyInt())).willReturn(new byte[] {'%', 'P', 'D', 'F'});

        mockMvc.perform(get("/akordi/{id}/pdf", song.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                // RFC 6266 fallback pattern: a plain ASCII-transliterated
                // filename= (diacritics stripped to their base letters, NOT
                // RFC 2047 encoded-word gibberish) plus the accurate
                // filename*=UTF-8''<percent-encoded> form.
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Dorde Dokic - Sasava prica.pdf\"; "
                                + "filename*=UTF-8''%C4%90or%C4%91e%20%C4%90oki%C4%87%20-%20"
                                + "%C5%A0a%C5%A1ava%20pri%C4%8Da.pdf"));
    }

    @Test
    void returns404WhenSongIsMissing() throws Exception {
        given(songService.load("42")).willReturn(Optional.empty());

        mockMvc.perform(get("/akordi/{id}/pdf", "42"))
                .andExpect(status().isNotFound());
    }
}
