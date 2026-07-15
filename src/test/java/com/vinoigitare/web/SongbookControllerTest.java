package com.vinoigitare.web;

import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.model.Song;
import com.vinoigitare.pdf.SongbookPdfRenderer;
import com.vinoigitare.pdf.SongbookPdfRenderer.SongbookItem;
import com.vinoigitare.security.SecurityConfig;
import com.vinoigitare.service.SongService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Personalized songbook PDF, Phase A -- confirms every {@code /songbook/**}
 * route actually requires login (these are deliberately absent from {@code
 * SecurityConfig}'s allowlist, so they should fall under the existing
 * {@code .anyRequest().authenticated()} catch-all, same as {@code
 * /admin/**} -- see {@code AdminControllerTest}'s own comment on why {@link
 * SecurityConfig} needs importing explicitly here) and that the generate
 * endpoint behaves correctly once authenticated.
 * See {@code ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md}.
 */
@Tag("fast")
@WebMvcTest(SongbookController.class)
@Import(SecurityConfig.class)
class SongbookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongService songService;

    @MockitoBean
    private SongbookPdfRenderer pdfRenderer;

    @Test
    void songbookPageRedirectsToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/songbook"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @WithMockUser
    void songbookPageSucceedsWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/songbook"))
                .andExpect(status().isOk());
    }

    @Test
    void detailsRedirectsToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/songbook/details").param("ids", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @WithMockUser
    void detailsReturnsKnownSongsAndSilentlyDropsUnknownIds() throws Exception {
        Song song = new Song("1", "Marko Markovic", "Probna pesma", "probna-pesma", "C G", null, 0L);
        given(songService.load("1")).willReturn(Optional.of(song));
        given(songService.load("999")).willReturn(Optional.empty());

        mockMvc.perform(get("/songbook/details").param("ids", "1,999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marko Markovic")))
                .andExpect(content().string(not(containsString("999"))));
    }

    @Test
    void generateRedirectsToLoginWhenNotAuthenticatedAndNeverCallsRenderer() throws Exception {
        mockMvc.perform(post("/songbook/generate").with(csrf()).param("selection", "[]"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));

        then(pdfRenderer).should(never()).render(any());
    }

    @Test
    @WithMockUser
    void generateWithoutCsrfTokenIsRejectedEvenWhenAuthenticated() throws Exception {
        mockMvc.perform(post("/songbook/generate").param("selection", "[]"))
                .andExpect(status().isForbidden());

        then(pdfRenderer).should(never()).render(any());
    }

    @Test
    @WithMockUser
    void generateRejectsEmptySelectionWithoutCallingRenderer() throws Exception {
        mockMvc.perform(post("/songbook/generate").with(csrf()).param("selection", "[]"))
                .andExpect(status().isBadRequest());

        then(pdfRenderer).should(never()).render(any());
    }

    @Test
    @WithMockUser
    void generateRejectsMalformedSelectionWithoutCallingRenderer() throws Exception {
        mockMvc.perform(post("/songbook/generate").with(csrf()).param("selection", "not json"))
                .andExpect(status().isBadRequest());

        then(pdfRenderer).should(never()).render(any());
    }

    @Test
    @WithMockUser
    void generateReturnsPdfBytesForAValidSelection() throws Exception {
        byte[] fakePdf = { 1, 2, 3 };
        given(pdfRenderer.render(any())).willReturn(fakePdf);

        mockMvc.perform(post("/songbook/generate").with(csrf())
                        .param("selection", "[{\"id\":\"1\",\"transpose\":2},{\"id\":\"2\",\"transpose\":0}]"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(fakePdf))
                .andExpect(header().string("Content-Disposition", containsString("Vino i gitare.pdf")));

        then(pdfRenderer).should().render(eq(java.util.List.of(new SongbookItem("1", 2), new SongbookItem("2", 0))));
    }
}
