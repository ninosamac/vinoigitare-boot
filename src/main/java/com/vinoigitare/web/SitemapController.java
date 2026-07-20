package com.vinoigitare.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

/**
 * {@code GET /sitemap.xml} (Phase 4f, SEO): a standard XML sitemap listing
 * the homepage and every song's canonical {@code /akordi/{id}/{slug}} URL,
 * generated fresh from the database on each request (the catalog is small
 * enough that there's no need to cache this).
 *
 * <p>Absolute URLs are built from the incoming request's own scheme/host
 * /port (via {@link ServletUriComponentsBuilder}) rather than a hardcoded
 * base URL, so this works correctly regardless of where the app is
 * actually deployed (localhost during development, whatever hostname it
 * ends up behind in the home lab).
 */
@RestController
public class SitemapController {

    private final SongService songService;

    public SitemapController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(HttpServletRequest request) {
        String baseUrl = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        List<Song> songs = songService.loadAll();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, baseUrl + "/");
        // /about is the only page hosting the original-edition PDF download
        // -- listed explicitly (unlike the song URLs below, it isn't
        // generated from any repository) so it's actually discoverable via
        // the sitemap, not just reachable by nav link.
        appendUrl(xml, baseUrl + "/about");
        // /chord-diagrams is genuinely valuable, evergreen static content
        // (every open-position chord fingering) -- listed explicitly for
        // the same reason /about is: it isn't generated from the song
        // repository below, so nothing else would surface it here. Found
        // missing during an SEO check-up (2026-07-19).
        appendUrl(xml, baseUrl + "/chord-diagrams");
        // Artist pages (SEO: findable for "{artist name} akordi" searches,
        // 2026-07-20) -- unlike song.slug() below, which is already
        // guaranteed ASCII-safe, artist names are raw strings with spaces
        // and diacritics (e.g. "Đorđe Balašević") used directly as the
        // /artists/{artist} @PathVariable, so each one needs real
        // path-segment encoding. UriComponentsBuilder's .encode() does
        // this correctly; naive string concatenation (as the song loop
        // below gets away with) would not.
        for (String artist : songService.loadAllGroupedByArtist().keySet()) {
            String artistUrl = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/artists/{artist}")
                    .buildAndExpand(artist)
                    .encode()
                    .toUriString();
            appendUrl(xml, artistUrl);
        }
        for (Song song : songs) {
            appendUrl(xml, baseUrl + "/akordi/" + song.id() + "/" + song.slug());
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    private static void appendUrl(StringBuilder xml, String location) {
        xml.append("  <url><loc>").append(escapeXml(location)).append("</loc></url>\n");
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
