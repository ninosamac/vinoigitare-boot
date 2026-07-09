package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import com.vinoigitare.AbstractSpringBootTest;
import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check for the encoding fix described in the migration plan
 * (section 1): a song with Serbian/Croatian diacritics must come back out
 * of storage *and* the rendered HTML with those characters intact -- no
 * U+FFFD replacement characters, no mangling.
 *
 * <p>Phase 4a: this now goes through {@code SongService} (backed by the
 * database repository) rather than writing directly to a {@code .tab}
 * file, and fetches the result via the {@code /akordi/{id}/{slug}} route
 * rather than the retired {@code /songs/{id}} one -- ids are database-
 * assigned now, so the test uses whatever id storing the song returns
 * rather than assuming one. The equivalent file-storage round trip is
 * still covered by {@code TextFileSongRepositoryTest}.
 */
@Tag("io")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SongUtf8RenderingTest extends AbstractSpringBootTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SongService songService;

    @Test
    void diacriticsSurviveStorageAndHtmlRendering() {
        Song stored = songService.store(new Song("Đorđe Đokić", "Šašava priča", "š đ č ć ž tekst pesme"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/akordi/{id}/{slug}", String.class, stored.id(), stored.slug());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("Đorđe Đokić")
                .contains("Šašava priča")
                .contains("š đ č ć ž tekst pesme");
    }
}
