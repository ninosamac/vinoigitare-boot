package com.vinoigitare.web;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.model.Song;
import com.vinoigitare.search.SearchService;
import com.vinoigitare.security.SecurityConfig;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest doesn't pick up the real SecurityConfig bean automatically --
// without importing it, the slice falls back to "authenticate everything",
// 401ing this route even though it's meant to be public. Importing the
// actual config (not a test-only stand-in) means this test verifies the
// real permitAll rule, not an assumption about it.
@Tag("fast")
@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @Test
    void searchRendersMatchingResults() throws Exception {
        Song song = new Song("Čačak Bend", "Neka pesma", "chords");
        given(searchService.search("cacak")).willReturn(List.of(song));

        mockMvc.perform(get("/search").param("q", "cacak"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Čačak Bend")));
    }

    @Test
    void blankQueryShowsEmptyStatePrompt() throws Exception {
        mockMvc.perform(get("/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a search term")));
    }

    @Test
    void missingQueryParamShowsEmptyStatePrompt() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a search term")));
    }

    @Test
    void queryWithNoMatchesShowsNoResultsMessage() throws Exception {
        given(searchService.search("nepostojeci")).willReturn(List.of());

        mockMvc.perform(get("/search").param("q", "nepostojeci"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No results for")));
    }
}
