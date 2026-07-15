package com.vinoigitare.web;

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

        String rawFileName = song.artist() + " - " + song.title() + ".pdf";
        return ResponseEntity.ok()
                .header("Content-Disposition", PdfDownloadFilenames.contentDispositionFor(rawFileName))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
