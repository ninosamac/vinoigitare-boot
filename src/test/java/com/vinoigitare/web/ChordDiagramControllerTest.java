package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.vinoigitare.chords.ChordDiagramRenderer;
import com.vinoigitare.security.SecurityConfig;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ChordDiagramController takes ChordDiagramRenderer as a constructor
// dependency, and @WebMvcTest's trimmed-down context doesn't scan plain
// @Component beans on its own -- same nested @TestConfiguration pattern
// as SongBrowseControllerTest, though for a different reason now that
// rendering happens in the controller rather than being called from the
// template (see ChordDiagramRenderer's Javadoc for why that changed).
// SecurityConfig also needs importing explicitly, same as every other
// public-route @WebMvcTest (see SearchControllerTest's comment) -- without
// it this slice falls back to "authenticate everything" and 401s.
@Tag("fast")
@WebMvcTest(ChordDiagramController.class)
@Import({ChordDiagramControllerTest.ExtraBeans.class, SecurityConfig.class})
class ChordDiagramControllerTest {

    @TestConfiguration
    static class ExtraBeans {
        @Bean
        ChordDiagramRenderer chordDiagramRenderer() {
            return new ChordDiagramRenderer();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageListsChordsFromEverySectionAsSvgDiagrams() throws Exception {
        mockMvc.perform(get("/chord-diagrams"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chord Diagrams")))
                .andExpect(content().string(containsString("Major 7th")))
                .andExpect(content().string(containsString(">C<")))
                .andExpect(content().string(containsString(">Hm<")))
                .andExpect(content().string(containsString(">C#<")))
                .andExpect(content().string(containsString(">G7<")))
                .andExpect(content().string(containsString(">Cdim<")))
                .andExpect(content().string(containsString("<svg")));
    }
}
