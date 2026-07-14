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
import com.vinoigitare.service.SongService;
import com.vinoigitare.security.SecurityConfig;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// See SearchControllerTest's comment: @WebMvcTest needs the real
// SecurityConfig imported explicitly, or it falls back to
// "authenticate everything" and 401s these otherwise-public routes.
@Tag("fast")
@WebMvcTest(SitemapController.class)
@Import(SecurityConfig.class)
class SitemapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongService songService;

    @Test
    void sitemapListsHomepageAboutPageAndEverySongUrl() throws Exception {
        Song song1 = new Song("1", "Marko Markovic", "Probna pesma", "marko-markovic--probna-pesma",
                "chords", null, 0L);
        Song song2 = new Song("2", "Đorđe Đokić", "Šašava priča", "dorde-dokic--sasava-prica",
                "chords", null, 0L);
        given(songService.loadAll()).willReturn(List.of(song1, song2));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("<loc>http://localhost/</loc>")))
                // /about hosts the original-edition PDF download and isn't
                // generated from any repository, so it needs its own
                // explicit sitemap entry -- see SitemapController's comment.
                .andExpect(content().string(containsString("<loc>http://localhost/about</loc>")))
                .andExpect(content().string(containsString("/akordi/1/marko-markovic--probna-pesma")))
                .andExpect(content().string(containsString("/akordi/2/dorde-dokic--sasava-prica")));
    }

    @Test
    void sitemapIsWellFormedXmlWithNoSongs() throws Exception {
        given(songService.loadAll()).willReturn(List.of());

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("</urlset>")));
    }
}
