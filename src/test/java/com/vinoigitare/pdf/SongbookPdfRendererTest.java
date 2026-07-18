package com.vinoigitare.pdf;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.vinoigitare.AbstractSpringBootTest;
import com.vinoigitare.model.Song;
import com.vinoigitare.pdf.SongbookPdfRenderer.SongbookItem;
import com.vinoigitare.service.SongService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Personalized songbook PDF, Phase A. Same real-PDF-content verification
 * style as {@link SongPdfRendererTest} (extracts text via PDFBox rather
 * than just checking bytes came back).
 * See {@code ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md}.
 */
@Tag("io")
@SpringBootTest
class SongbookPdfRendererTest extends AbstractSpringBootTest {

    @Autowired
    private SongbookPdfRenderer renderer;

    @Autowired
    private SongService songService;

    @Test
    void preservesSubmissionOrderTransposesPerItemAndDropsUnknownIds() throws IOException {
        // Deliberately requested out of alphabetical order -- this must
        // NOT get re-sorted (songbook-reorder-plan.md, issue #9): the
        // order a visitor arranges their selection into on /songbook is
        // the order that has to end up in the PDF.
        Song zSong = songService.store(new Song("Zebra Band", "Zeta", "C\nZ lyrics"));
        Song aSong = songService.store(new Song("Aerodrom", "Alpha", "C\nA lyrics"));

        byte[] pdf = renderer.render(List.of(
                new SongbookItem(zSong.id(), 0),
                new SongbookItem(aSong.id(), 2),
                new SongbookItem("no-such-id", 0)), null, true);

        assertThat(pdf).isNotEmpty();
        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            // Cover + TOC + 2 songs + at least one chord-diagram page.
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(5);
            text = new PDFTextStripper().getText(document);
        }

        assertThat(text).contains("Aerodrom").contains("Zebra Band");
        // Zebra Band was requested first, Aerodrom second -- that exact
        // order has to survive into the PDF, even though it's the
        // reverse of alphabetical.
        assertThat(text.indexOf("Zebra Band")).isLessThan(text.indexOf("Aerodrom"));
        // C up 2 semitones = D -- confirms the per-item transpose (not
        // just the single-song SongPdfRenderer's own offset) is really
        // wired through.
        assertThat(text).contains("D");
        assertThat(text).doesNotContain("no-such-id");
    }

    @Test
    void includesTheChordDiagramReferenceSectionByDefault() throws IOException {
        Song song = songService.store(new Song("Marko Markovic", "Probna pesma", "C\nlyrics"));

        byte[] pdf = renderer.render(List.of(new SongbookItem(song.id(), 0)), null, true);

        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).contains("Chord Diagrams");
    }

    @Test
    void omitsTheChordDiagramSectionWhenNotWanted() throws IOException {
        Song song = songService.store(new Song("Marko Markovic", "Probna pesma", "C\nlyrics"));

        byte[] pdf = renderer.render(List.of(new SongbookItem(song.id(), 0)), null, false);

        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).doesNotContain("Chord Diagrams");
    }

    @Test
    void usesCustomBookTitleOnTheCoverWhenGiven() throws IOException {
        Song song = songService.store(new Song("Marko Markovic", "Probna pesma", "C\nlyrics"));

        byte[] pdf = renderer.render(List.of(new SongbookItem(song.id(), 0)), "Marko's Setlist", true);

        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).contains("Marko's Setlist").doesNotContain("Your personal songbook");
    }

    @Test
    void blankBookTitleFallsBackToTheDefaultCoverText() throws IOException {
        Song song = songService.store(new Song("Marko Markovic", "Probna pesma", "C\nlyrics"));

        byte[] pdf = renderer.render(List.of(new SongbookItem(song.id(), 0)), "   ", true);

        String text;
        try (PDDocument document = PDDocument.load(pdf)) {
            text = new PDFTextStripper().getText(document);
        }
        assertThat(text).contains("Your personal songbook");
    }
}
