package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.security.SecurityConfig;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig needs importing explicitly, same as every other
// public-route @WebMvcTest (see SearchControllerTest's comment) --
// without it this slice falls back to "authenticate everything" and 401s.
@Tag("fast")
@WebMvcTest(AboutController.class)
@Import(SecurityConfig.class)
class AboutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageIsPubliclyReachableAndListsAllFourSections() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Why this exists")))
                .andExpect(content().string(containsString("Privacy")))
                .andExpect(content().string(containsString("Support")))
                .andExpect(content().string(containsString("Buy me a coffee")))
                // Real Ko-fi link (plain link, not the floating overlay
                // widget -- Nino's choice), opening in a new tab with
                // rel="noopener noreferrer" so the opened page can't reach
                // back into this one via window.opener.
                .andExpect(content().string(containsString("href=\"https://ko-fi.com/ninosamac\"")))
                .andExpect(content().string(containsString("rel=\"noopener noreferrer\"")))
                // Original 2000 edition: a direct PDF download now, not a
                // quoted foreword (see about.html's comment on this).
                .andExpect(content().string(containsString("Original 2000 edition")))
                .andExpect(content().string(containsString("href=\"/downloads/Vino_i_gitare.pdf\"")));
    }
}
