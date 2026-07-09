package com.vinoigitare.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.vinoigitare.model.Song;
import com.vinoigitare.pdf.SongPdfRenderer;
import com.vinoigitare.service.SongService;

/**
 * {@code GET /akordi/{id}/pdf} -- the feature the old (never-implemented)
 * {@code Vinoigitare_Utils} {@code SongPDF} stub was meant for: download a
 * PDF of the currently-displayed song.
 *
 * <p>Phase 4a: route moved from {@code /songs/{id}/pdf} to {@code
 * /akordi/{id}/pdf} alongside the song view route's move to {@code
 * /akordi/{id}/{slug}} (see {@code SongBrowseController}); {@code id} is
 * now the numeric database id.
 */
@RestController
public class SongPdfController {

    private final SongService songService;
    private final SongPdfRenderer pdfRenderer;

    public SongPdfController(SongService songService, SongPdfRenderer pdfRenderer) {
        this.songService = songService;
        this.pdfRenderer = pdfRenderer;
    }

    @GetMapping(value = "/akordi/{id}/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> pdf(
            @PathVariable String id,
            // Phase 4b: wired up to SongPdfRenderer/ChordTransposer, so the
            // downloaded PDF matches whatever transposition is showing on
            // screen (see the song page's "Preuzmi PDF" link, which appends
            // this from the current on-screen transpose offset).
            @RequestParam(name = "transpose", required = false, defaultValue = "0") int transposeSemitones) {

        Song song = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));

        byte[] pdf = pdfRenderer.render(song, transposeSemitones);

        return ResponseEntity.ok()
                .header("Content-Disposition", contentDispositionFor(song))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Builds the RFC 6266 section 5 fallback pattern by hand: a plain
     * ASCII-transliterated {@code filename=} for clients that don't
     * understand the extended form, plus the accurate {@code filename*=
     * UTF-8''...} percent-encoded form for clients that do.
     *
     * <p>Deliberately not using Spring's {@code ContentDisposition.filename
     * (String, Charset)} here: with a non-ASCII charset it falls back to
     * RFC 2047 encoded-word syntax ({@code =?UTF-8?Q?...?=}) for the plain
     * {@code filename=} parameter (verified by hitting this endpoint
     * directly), which is a MIME/email-header convention, not what RFC 6266
     * recommends for HTTP, and not the ASCII-transliterated fallback
     * artist/title routinely need here (š/đ/č/ć/ž).
     */
    private static String contentDispositionFor(Song song) {
        String rawFileName = song.artist() + " - " + song.title() + ".pdf";
        return "attachment; filename=\"" + asciiTransliterate(rawFileName) + "\"; filename*=UTF-8''"
                + percentEncode(rawFileName);
    }

    /**
     * NFD-decompose then strip combining marks: this turns š/č/ć/ž into
     * their plain-Latin base letters (they're accented Latin letters with a
     * canonical decomposition into base + combining mark). Serbian đ/Đ are
     * NOT accented letters in Unicode's eyes -- "Latin small/capital letter
     * D with stroke" has no canonical decomposition, so NFD leaves it
     * untouched (verified directly: {@code Normalizer.normalize("đ", NFD)}
     * stays {@code "đ"}) -- hence the explicit substitution before NFD.
     * Anything still outside printable ASCII afterwards (or a double quote,
     * which would break the quoted string) becomes an underscore as a
     * last-resort fallback.
     */
    private static String asciiTransliterate(String text) {
        String withPlainD = text.replace('Đ', 'D').replace('đ', 'd'); // Đ -> D, đ -> d
        String decomposed = Normalizer.normalize(withPlainD, Normalizer.Form.NFD);
        String withoutMarks = decomposed.replaceAll("\\p{M}", "");
        return withoutMarks.replaceAll("[^\\x20-\\x7E]|\"", "_");
    }

    /** RFC 5987 percent-encoding: UTF-8 bytes, percent-escaped, spaces as %20 (not "+"). */
    private static String percentEncode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
