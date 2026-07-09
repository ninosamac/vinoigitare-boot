package com.vinoigitare.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check for the encoding fix described in the migration plan
 * (section 1): a song with Serbian/Croatian diacritics, written to a real
 * {@code .tab} file as UTF-8, must come back out of both the repository
 * *and* the rendered HTML with those characters intact -- no U+FFFD
 * replacement characters, no mangling.
 */
@Tag("io")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SongUtf8RenderingTest {

    @TempDir
    static Path songsDir;

    @DynamicPropertySource
    static void songsDirProperty(DynamicPropertyRegistry registry) {
        registry.add("vinoigitare.songs-dir", () -> songsDir.toString());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void diacriticsSurviveStorageAndHtmlRendering() throws Exception {
        String id = "Đorđe Đokić - Šašava priča";
        Path file = songsDir.resolve(id + ".tab");
        Files.writeString(file, "š đ č ć ž tekst pesme", StandardCharsets.UTF_8);

        ResponseEntity<String> response = restTemplate.getForEntity("/songs/{id}", String.class, id);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("Đorđe Đokić")
                .contains("Šašava priča")
                .contains("š đ č ć ž tekst pesme");
    }
}
