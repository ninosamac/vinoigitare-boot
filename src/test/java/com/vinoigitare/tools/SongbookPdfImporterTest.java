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

    @Test
    void fromAndToRestrictWhichPagesAreProcessed(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("fixture.pdf");
        Path outputDir = tempDir.resolve("out");

        writePdf(pdf, List.of(
                "Front Matter Fictional Page\nNot A Real Artist\nX\nfake front matter body text",
                "Fake Song Two\nFake Artist Two\nC D\nfake body two",
                "Fake Song Three\nFake Artist Three\nE F\nfake body three",
                "Back Matter Fictional Page\nNot A Real Artist Either\nX\nfake back matter body text"
        ));

        run("--pdf", pdf.toString(), "--output-dir", outputDir.toString(), "--mode", "page",
                "--from", "2", "--to", "3");

        List<Path> written = Files.list(outputDir).toList();
        assertEquals(2, written.size(), "expected only pages 2-3 to be processed");
        assertTrue(Files.exists(outputDir.resolve("Fake Artist Two - Fake Song Two.tab")));
        assertTrue(Files.exists(outputDir.resolve("Fake Artist Three - Fake Song Three.tab")));
        assertTrue(Files.notExists(outputDir.resolve("Not A Real Artist - Front Matter Fictional Page.tab")));
        assertTrue(Files.notExists(outputDir.resolve("Not A Real Artist Either - Back Matter Fictional Page.tab")));
    }

    @Test
    void dryRunReportsRealPageNumbersNotRelativeToFrom(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("fixture.pdf");
        Path outputDir = tempDir.resolve("out");

        writePdf(pdf, List.of(
                "Page One Fictional Song\nArtist One\nC\nbody one",
                "Page Two Fictional Song\nArtist Two\nC\nbody two",
                "Page Three Fictional Song\nArtist Three\nC\nbody three"
        ));

        String output = runCapturingStdout("--pdf", pdf.toString(), "--output-dir", outputDir.toString(),
                "--mode", "page", "--dry-run", "--from", "2", "--to", "3");

        assertTrue(output.contains("page 2"), "expected the real page number (2), not a relative index (1)");
        assertTrue(output.contains("page 3"));
        assertTrue(!output.contains("page 1)"), "page 1 was excluded by --from 2 and should not appear");
    }

    @Test
    void expandHomeReplacesLeadingTildeWithGivenHomeDir() {
        assertEquals(Path.of("/home/fake-user/Downloads/x.pdf"),
                SongbookPdfImporter.expandHome("~/Downloads/x.pdf", "/home/fake-user"));
        assertEquals(Path.of("/home/fake-user"),
                SongbookPdfImporter.expandHome("~", "/home/fake-user"));
    }

    @Test
    void expandHomeLeavesNonTildePathsUntouched() {
        assertEquals(Path.of("/absolute/path.pdf"),
                SongbookPdfImporter.expandHome("/absolute/path.pdf", "/home/fake-user"));
        assertEquals(Path.of("relative/path.pdf"),
                SongbookPdfImporter.expandHome("relative/path.pdf", "/home/fake-user"));
        // A tilde that isn't at the very start, or isn't followed by a
        // slash, is not a home-directory reference -- leave it alone.
        assertEquals(Path.of("not~home/path.pdf"),
                SongbookPdfImporter.expandHome("not~home/path.pdf", "/home/fake-user"));
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
