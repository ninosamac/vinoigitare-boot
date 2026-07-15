package com.vinoigitare.web;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.vinoigitare.model.Song;
import com.vinoigitare.pdf.SongbookPdfRenderer;
import com.vinoigitare.pdf.SongbookPdfRenderer.SongbookItem;
import com.vinoigitare.service.SongService;

/**
 * Personalized songbook PDF, Phase A -- admin-gated only via
 * {@code SecurityConfig}'s allowlist (these routes are deliberately
 * absent from it, so they fall under the existing {@code
 * .anyRequest().authenticated()} catch-all, same as {@code /admin/**}).
 * See {@code ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md}.
 *
 * <p>The actual song selection lives client-side only (localStorage, see
 * {@code static/js/songbook.js}) -- this controller never sees or stores
 * it except for the one-shot id-to-summary lookup below, which exists
 * only because localStorage has no artist/title to display, just ids.
 */
@Controller
public class SongbookController {

    private final SongService songService;
    private final SongbookPdfRenderer pdfRenderer;
    private final ObjectMapper objectMapper;

    public SongbookController(SongService songService, SongbookPdfRenderer pdfRenderer, ObjectMapper objectMapper) {
        this.songService = songService;
        this.pdfRenderer = pdfRenderer;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/songbook")
    public String songbook() {
        return "songbook";
    }

    /**
     * Resolves a comma-separated list of song ids to display summaries.
     * Unknown ids are silently dropped rather than erroring -- a stale
     * localStorage entry for a since-deleted song shouldn't break the
     * whole page, it should just not show up.
     */
    @GetMapping("/songbook/details")
    @ResponseBody
    public List<SongSummary> details(@RequestParam List<String> ids) {
        return ids.stream()
                .map(songService::load)
                .flatMap(Optional::stream)
                .map(SongSummary::from)
                .toList();
    }

    /**
     * Renders the visitor's current selection to one combined PDF --
     * {@code selection} is the exact JSON shape {@code static/js/
     * songbook.js} already stores in localStorage
     * ({@code [{id, transpose}, ...]}), submitted as a single form field
     * by {@code songbook.html} rather than a query string, since a large
     * selection could realistically exceed a comfortable URL length.
     *
     * <p>An empty or unparseable selection is a 400, not a rendered
     * empty-songbook PDF -- there's nothing useful to generate.
     */
    @PostMapping(value = "/songbook/generate", produces = "application/pdf")
    public ResponseEntity<byte[]> generate(@RequestParam String selection) {
        List<SelectionEntry> entries = parseSelection(selection);
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selection is empty");
        }
        List<SongbookItem> items = entries.stream()
                .map(entry -> new SongbookItem(entry.id(), entry.transpose()))
                .toList();
        byte[] pdf = pdfRenderer.render(items);
        return ResponseEntity.ok()
                .header("Content-Disposition", PdfDownloadFilenames.contentDispositionFor("Vino i gitare.pdf"))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private List<SelectionEntry> parseSelection(String selection) {
        try {
            return List.of(objectMapper.readValue(selection, SelectionEntry[].class));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed selection", e);
        }
    }

    public record SongSummary(String id, String artist, String title) {
        static SongSummary from(Song song) {
            return new SongSummary(song.id(), song.artist(), song.title());
        }
    }

    /** Matches static/js/songbook.js's localStorage entry shape exactly. */
    public record SelectionEntry(String id, int transpose) {
    }
}
