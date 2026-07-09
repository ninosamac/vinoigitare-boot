package com.vinoigitare.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SongbookPdfImporter} end-to-end against a synthetic PDF
 * this test builds itself -- fictional made-up songs, never any real
 * songbook. Covers the layouts the importer's heuristics need to handle:
 * one song per page, two songs sharing a page, and one song spanning two
 * pages.
 */
@Tag("io")
class SongbookPdfImporterTest {

    // Same embedded font Phase 3's PDF export uses, so we can actually
    // write š/đ/č/ć/ž into the synthetic fixture and get them back out.
    private static final String FONT_RESOURCE = "/fonts/DejaVuSansMono.ttf";

    @Test
    void splitsOneSongPerPageAndWritesTabFiles(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("fixture.pdf");
        Path outputDir = tempDir.resolve("out");

        writePdf(pdf, List.of(
                "Fictional Test Song\nMade-up Test Artist\nX Y Z\nla la la, this is not a real song\nX Y Z\nmore fake lyrics for testing",
                "Second Fictional Song\nAnother Fake Artist\nA B C\nsecond verse of nonsense\nA B C\nmore nonsense for the fixture"
        ));

        run("--pdf", pdf.toString(), "--output-dir", outputDir.toString(), "--mode", "page");

        assertTrue(Files.exists(outputDir.resolve("Made-up Test Artist - Fictional Test Song.tab")));
        assertTrue(Files.exists(outputDir.resolve("Another Fake Artist - Second Fictional Song.tab")));
        String body = Files.readString(outputDir.resolve("Made-up Test Artist - Fictional Test Song.tab"), StandardCharsets.UTF_8);
        assertTrue(body.contains("la la la"));
    }

    // The next two tests exercise SongbookPdfImporter.splitByBlankLines
    // directly with hand-built PageText values, rather than round-tripping
    // through an actual generated PDF like the other tests here. That's
    // deliberate: real PDF text extraction does not reliably preserve
    // fully-blank lines (a line with literally no rendered glyph is often
    // just a bigger vertical gap to the extractor, not a guaranteed blank
    // text line), so trying to assert an exact blank-line COUNT survived a
    // synthetic PDF round-trip is testing PDFBox's gap-detection heuristics,
    // not this importer's own splitting logic. Testing the string-level
    // logic directly is both more precise and faster.

    @Test
    void splitsTwoSongsSharingOnePageByBlankLineRun() {
        String page = "First Shared-Page Song\nFake Artist One\nC D E\nfirst fake song body line one\nC D E\nfirst fake song body line two"
                + "\n\n\n"
                + "Second Shared-Page Song\nFake Artist Two\nF G A\nsecond fake song body line one\nF G A\nsecond fake song body line two";

        List<SongbookPdfImporter.ParsedSong> songs =
                SongbookPdfImporter.splitByBlankLines(List.of(new SongbookPdfImporter.PageText(1, page)));

        assertEquals(2, songs.size());
        assertEquals("First Shared-Page Song", songs.get(0).firstLine());
        assertEquals("Fake Artist One", songs.get(0).secondLine());
        assertEquals("Second Shared-Page Song", songs.get(1).firstLine());
        assertEquals("Fake Artist Two", songs.get(1).secondLine());
    }

    @Test
    void doesNotSplitASongSpanningTwoPages() {
        SongbookPdfImporter.PageText page1 =
                new SongbookPdfImporter.PageText(1, "Cross-Page Fictional Song\nFake Touring Artist\nC D E\nfirst half of the fake body on page one");
        SongbookPdfImporter.PageText page2 =
                new SongbookPdfImporter.PageText(2, "C D E\nsecond half of the fake body, continuing on page two");

        List<SongbookPdfImporter.ParsedSong> songs = SongbookPdfImporter.splitByBlankLines(List.of(page1, page2));

        assertEquals(1, songs.size(), "expected the two pages to merge into a single song");
        assertEquals("Cross-Page Fictional Song", songs.get(0).firstLine());
        assertTrue(songs.get(0).body().contains("first half"));
        assertTrue(songs.get(0).body().contains("second half"));
        assertEquals(1, songs.get(0).startPage());
        assertEquals(2, songs.get(0).endPage());
    }

    @Test
    void diacriticsSurviveExtractionAndFilenameSanitization(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("fixture.pdf");
        Path outputDir = tempDir.resolve("out");

        writePdf(pdf, List.of(
                "Šašava Fiktivna Pesma\nĐorđe Testić\nC G\nali š, đ, č, ć i ž moraju da prođu ceo put"
        ));

        run("--pdf", pdf.toString(), "--output-dir", outputDir.toString(), "--mode", "page");

        List<Path> written = Files.list(outputDir).toList();
        assertEquals(1, written.size());
        String body = Files.readString(written.get(0), StandardCharsets.UTF_8);
        for (char c : new char[] {'š', 'đ', 'č', 'ć', 'ž'}) {
            assertTrue(body.indexOf(c) >= 0, "expected '" + c + "' to survive round-trip");
        }
    }

    @Test
    void dryRunWritesNoFilesAndPrintsNoLyricText(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("fixture.pdf");
        Path outputDir = tempDir.resolve("out");

        writePdf(pdf, List.of(
                "Dry Run Fictional Song\nDry Run Fake Artist\nC G\nsome fake secret lyric text that must not appear in dry-run output"
        ));

        String output = runCapturingStdout("--pdf", pdf.toString(), "--output-dir", outputDir.toString(),
                "--mode", "page", "--dry-run");

        assertTrue(Files.notExists(outputDir), "dry-run must not create the output directory");
        assertTrue(output.contains("Detected"));
        assertTrue(output.contains("page 1"));
        assertTrue(!output.contains("secret lyric text"), "dry-run must not print extracted lyric text");
    }

    private static void run(String... args) throws IOException {
        SongbookPdfImporter.main(args);
    }

    private static String runCapturingStdout(String... args) throws IOException {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            SongbookPdfImporter.main(args);
        } finally {
            System.setOut(originalOut);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    /** Writes one PDF page per string in {@code pageTexts}, one line per {@code \n}. */
    private static void writePdf(Path path, List<String> pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = PDType0Font.load(document, SongbookPdfImporterTest.class.getResourceAsStream(FONT_RESOURCE));
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 12);
                    stream.setLeading(14.5f);
                    stream.newLineAtOffset(50, 750);
                    for (String line : pageText.split("\n", -1)) {
                        stream.showText(line);
                        stream.newLine();
                    }
                    stream.endText();
                }
            }
            document.save(path.toFile());
        }
    }
}
