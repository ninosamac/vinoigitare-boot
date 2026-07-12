package com.vinoigitare.web;

import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.model.Song;
import com.vinoigitare.security.SecurityConfig;
import com.vinoigitare.service.SongService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin-auth plan (~/knowledge/projects/vinoigitare/admin-auth-plan.md),
 * step 4: confirms {@code /admin/**} actually requires the configured
 * credential -- the thing every other {@code @WebMvcTest} in this app
 * needed {@link SecurityConfig} imported to verify they *don't* require
 * (see e.g. {@code SearchControllerTest}'s comment).
 */
@Tag("fast")
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongService songService;

    @Test
    void listRedirectsToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    @WithMockUser
    void listSucceedsWhenAuthenticated() throws Exception {
        // No song list on this page anymore (2026-07-12): editing/deleting
        // now happens from the song page itself, so /admin no longer needs
        // songService.loadAll() at all -- confirm it's never even called.
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk());

        then(songService).should(never()).loadAll();
    }

    @Test
    void createRedirectsToLoginWhenNotAuthenticatedAndNeverCallsTheService() throws Exception {
        mockMvc.perform(post("/admin/new").with(csrf())
                        .param("artist", "New Artist").param("title", "New Song").param("chords", "C G"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));

        then(songService).should(never()).store(any());
    }

    @Test
    @WithMockUser
    void createWithoutCsrfTokenIsRejectedEvenWhenAuthenticated() throws Exception {
        // Confirms CSRF protection is still actually enforced for a real
        // authenticated session, not just bypassed -- a missing token here
        // must 403, not silently succeed.
        mockMvc.perform(post("/admin/new")
                        .param("artist", "New Artist").param("title", "New Song").param("chords", "C G"))
                .andExpect(status().isForbidden());

        then(songService).should(never()).store(any());
    }

    @Test
    @WithMockUser
    void createSucceedsWhenAuthenticatedWithCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/new").with(csrf())
                        .param("artist", "New Artist").param("title", "New Song").param("chords", "C G"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        then(songService).should().store(any());
    }

    @Test
    void deleteRedirectsToLoginWhenNotAuthenticatedAndNeverCallsTheService() throws Exception {
        mockMvc.perform(post("/admin/delete/{id}", "1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));

        then(songService).should(never()).remove(any());
    }

    @Test
    @WithMockUser
    void deleteSucceedsWhenAuthenticatedWithCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/delete/{id}", "1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        then(songService).should().remove("1");
    }

    @Test
    @WithMockUser
    void editFormShowsExistingSongWhenAuthenticated() throws Exception {
        Song song = new Song("1", "Marko Markovic", "Probna pesma", "probna-pesma", "C G", null, 0L);
        given(songService.load("1")).willReturn(Optional.of(song));

        mockMvc.perform(get("/admin/edit/{id}", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Marko Markovic")));
    }

    @Test
    void editFormRedirectsToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/edit/{id}", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }
}
