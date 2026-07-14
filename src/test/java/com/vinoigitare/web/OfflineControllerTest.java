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

@Tag("fast")
@WebMvcTest(OfflineController.class)
@Import(SecurityConfig.class)
class OfflineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/offline"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You&#39;re offline")));
    }
}
