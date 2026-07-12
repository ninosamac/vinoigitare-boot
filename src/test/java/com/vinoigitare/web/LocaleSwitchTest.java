package com.vinoigitare.web;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.vinoigitare.AbstractSpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the real language switcher (2026-07-12, see {@link
 * LocaleConfig}) -- a {@code @WebMvcTest} slice wouldn't pick up
 * {@code LocaleConfig}'s beans unless explicitly imported, so this goes
 * through the full app context and a real HTTP round trip instead, the
 * same convention {@code SongUtf8RenderingTest} already established for
 * exactly this kind of "needs the whole request pipeline" behavior.
 *
 * <p>Uses {@code about.supportButton} as the distinguishing string across
 * all three locales specifically because it differs in all three ("Buy
 * me a coffee" / "Časti me kavom" / "Časti me kafom" -- the last two
 * differ by exactly one letter, kava vs. kafa, one of the real
 * Croatian/Serbian vocabulary differences {@code messages_hr.properties}
 * was written to get right rather than just copy from
 * {@code messages_sr.properties}).
 */
@Tag("io")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocaleSwitchTest extends AbstractSpringBootTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void defaultsToEnglishWithNoCookieOrParam() {
        ResponseEntity<String> response = restTemplate.getForEntity("/about", String.class);

        assertThat(response.getBody()).contains("Buy me a coffee");
    }

    @Test
    void queryParamSwitchesToCroatianAndSetsAPersistentCookie() {
        ResponseEntity<String> response = restTemplate.getForEntity("/about?lang=hr", String.class);

        assertThat(response.getBody()).contains("Časti me kavom");
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        assertThat(setCookies).anyMatch(cookie -> cookie.startsWith("vinoigitare.locale=hr")
                // CookieLocaleResolver's 365-day max-age -- confirms this is a
                // real persistent cookie, not a session-only one that would
                // vanish the moment the browser closes.
                && cookie.toLowerCase().contains("max-age="));
    }

    @Test
    void queryParamSwitchesToSerbian() {
        ResponseEntity<String> response = restTemplate.getForEntity("/about?lang=sr", String.class);

        assertThat(response.getBody()).contains("Časti me kafom");
    }

    @Test
    void cookieAloneReselectsTheLocaleOnALaterRequestWithNoQueryParam() {
        ResponseEntity<String> firstResponse = restTemplate.getForEntity("/about?lang=hr", String.class);
        String setCookieHeader = firstResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        String cookieValue = setCookieHeader.split(";", 2)[0];

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieValue);
        ResponseEntity<String> secondResponse = restTemplate.exchange(
                "/about", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(secondResponse.getBody()).contains("Časti me kavom");
    }
}
