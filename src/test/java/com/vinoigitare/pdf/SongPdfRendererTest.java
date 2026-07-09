package com.vinoigitare.pdf;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.vinoigitare.AbstractSpringBootTest;
import com.vinoigitare.model.Song;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies actual PDF *content*, not just that bytes came back: extracts
 * text with PDFBox (already on the classpath transitively via
 * openhtmltopdf-pdfbox) and asserts the embedded DejaVu Sans Mono font
 * really did carry the diacritics through, rather than dropping or
 * substituting them.
 */
@Tag("io")
@SpringBootTest
class SongPdfRendererTest extends AbstractSpringBootTest {

    @Autowired
    private SongPdfRenderer renderer;

    @Test
    void rendersNonEmptyValidPdf() throws IOException {
        Song song = new Song("Marko Markovic", "Probna pesma", "G D Em C\nTest lyrics");

        byte[] pdf = renderer.render(song, 0);

        assertThat(pdf).isNotEmpty();
        // A parseable PDDocument is itself proof the bytes are a valid PDF.
        try (PDDocument document = PDDocument.load(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void diacriticsSurviveIntoExtractedPdfText() throws IOException {
        Song song = new Song("Đorđe Đokić", "Šašava priča", "š đ č ć ž tekst pesme");

        byte[] pdf = renderer.render(song, 0);

        String extractedText;
        try (PDDocument document = PDDocument.load(pdf)) {
            extractedText = new PDFTextStripper().getText(document);
        }

        assertThat(extractedText)
                .contains("Đorđe Đokić")
                .contains("Šašava priča")
                .contains("š")
                .contains("đ")
                .contains("č")
                .contains("ć")
                .contains("ž");
    }
}
