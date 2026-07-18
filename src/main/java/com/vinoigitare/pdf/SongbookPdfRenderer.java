package com.vinoigitare.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;

import com.vinoigitare.chords.ChordDiagram;
import com.vinoigitare.chords.ChordDiagramCatalog;
import com.vinoigitare.chords.ChordDiagramRenderer;
import com.vinoigitare.chords.ChordLineHighlighter;
import com.vinoigitare.chords.ChordTransposer;
import com.vinoigitare.chords.RenderedChordDiagram;
import com.vinoigitare.chords.RenderedChordSection;
import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

/**
 * Renders a visitor's personalized songbook selection to one combined PDF:
 * cover -> table of contents (real page numbers, not just links) -> each
 * selected song -> chord-diagram reference. Personalized songbook PDF plan
 * (Phase A): {@code ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md}.
 *
 * <p>Reuses {@link SongPdfRenderer}'s established patterns rather than
 * inventing new ones: DejaVu fonts embedded from classpath {@code
 * InputStream}s (here DejaVu Serif for headings too, since the cover page
 * mirrors the site's own Georgia-style heading font -- not something a
 * Linux server can rely on having installed, same reasoning DejaVu Sans
 * Mono is embedded rather than trusted from the system), and {@code
 * fragments :: songContent} for each song's actual title/artist/chords
 * block.
 *
 * <p>Unknown/since-deleted song ids are silently dropped, same as {@code
 * SongbookController#details} -- a stale selection shouldn't fail the
 * whole generation.
 */
@Component
public class SongbookPdfRenderer {

    private static final String MONO_FAMILY = "DejaVu Sans Mono";
    private static final String MONO_REGULAR_RESOURCE = "/fonts/DejaVuSansMono.ttf";
    private static final String MONO_BOLD_RESOURCE = "/fonts/DejaVuSansMono-Bold.ttf";

    private static final String SERIF_FAMILY = "DejaVu Serif";
    private static final String SERIF_REGULAR_RESOURCE = "/fonts/DejaVuSerif.ttf";
    private static final String SERIF_BOLD_RESOURCE = "/fonts/DejaVuSerif-Bold.ttf";

    // The wine-and-guitar photo from the About page (2026-07-19, Nino's
    // call) replaces the original text-logo cover -- reuses the exact
    // same static/images/ file the About page already serves, rather
    // than duplicating a copy under pdf/, since nothing about it needs
    // to differ for print (unlike the logo before it, which needed a
    // light-background variant specifically for this white page).
    private static final String COVER_IMAGE_RESOURCE = "/static/images/wine-and-guitar.jpg";

    // No relative resources are loaded via a URL (the cover image is
    // inlined as a base64 data URI instead -- see the prototype note in
    // the plan doc), so this is just a placeholder base URI, same as
    // SongPdfRenderer's.
    private static final String BASE_URI = "pdf://vinoigitare/";

    private final ITemplateEngine templateEngine;
    private final ChordLineHighlighter chordLineHighlighter;
    private final ChordDiagramRenderer chordDiagramRenderer;
    private final SongService songService;

    public SongbookPdfRenderer(ITemplateEngine templateEngine, ChordLineHighlighter chordLineHighlighter,
            ChordDiagramRenderer chordDiagramRenderer, SongService songService) {
        this.templateEngine = templateEngine;
        this.chordLineHighlighter = chordLineHighlighter;
        this.chordDiagramRenderer = chordDiagramRenderer;
        this.songService = songService;
    }

    /** One selected song and the transpose offset it should render at (0 = original key). */
    public record SongbookItem(String songId, int transposeSemitones) {
    }

    /** A resolved, possibly-transposed song ready for the template, paired with its highlighted chords text. */
    public record SongEntry(Song song, String highlightedChords, int transposeSemitones) {
    }

    /**
     * @param bookTitle custom cover title -- null or blank falls back to
     *                   the default {@code #{songbook.pdfCoverTitle}}
     *                   message, resolved in the template itself (not
     *                   here, since that's where every other message
     *                   lookup for this document already happens)
     * @param includeChordDiagrams whether to render the chord-reference
     *                              section at all -- skips computing it
     *                              entirely when false, not just hiding
     *                              it in the template
     */
    public byte[] render(List<SongbookItem> items, String bookTitle, boolean includeChordDiagrams) {
        String xhtml = renderXhtml(items, bookTitle, includeChordDiagrams);
        return toPdfBytes(xhtml);
    }

    private String renderXhtml(List<SongbookItem> items, String bookTitle, boolean includeChordDiagrams) {
        // Rendered in the order the visitor's selection arrived in, not
        // re-sorted -- songbook-reorder-plan.md (2026-07-19, issue #9):
        // /songbook now lets a visitor arrange their own selection with
        // move-up/move-down buttons, so the order they see in the
        // builder is the order that has to end up in the PDF. Previously
        // this forced an alphabetical-by-artist-then-title order
        // regardless of what was submitted -- a real, confirmed
        // mismatch between the builder and the finished book.
        List<SongEntry> entries = items.stream()
                .map(this::resolve)
                .flatMap(Optional::stream)
                .toList();

        Context context = new Context();
        context.setVariable("entries", entries);
        context.setVariable("includeChordDiagrams", includeChordDiagrams);
        context.setVariable("sections", includeChordDiagrams ? renderedChordSections() : List.of());
        context.setVariable("songCount", entries.size());
        context.setVariable("generatedOn", DateTimeFormatter.ofPattern("d MMMM yyyy").format(LocalDate.now()));
        context.setVariable("coverImageBase64", coverImageBase64());
        context.setVariable("bookTitle", (bookTitle == null || bookTitle.isBlank()) ? null : bookTitle.trim());
        return templateEngine.process("songbook-pdf", context);
    }

    private Optional<SongEntry> resolve(SongbookItem item) {
        return songService.load(item.songId()).map(song -> {
            Song forRendering = item.transposeSemitones() == 0 ? song : withTransposedChords(song, item.transposeSemitones());
            String highlighted = chordLineHighlighter.render(forRendering.chords());
            return new SongEntry(forRendering, highlighted, item.transposeSemitones());
        });
    }

    private static Song withTransposedChords(Song song, int transposeSemitones) {
        String transposedChords = ChordTransposer.transpose(song.chords(), transposeSemitones);
        return new Song(song.id(), song.artist(), song.title(), song.slug(), transposedChords,
                song.createdAt(), song.views());
    }

    private List<RenderedChordSection> renderedChordSections() {
        return ChordDiagramCatalog.sections().stream()
                .map(section -> new RenderedChordSection(section.labelKey(),
                        section.chords().stream().map(this::renderDiagram).toList()))
                .toList();
    }

    private RenderedChordDiagram renderDiagram(ChordDiagram diagram) {
        return new RenderedChordDiagram(diagram.name(), chordDiagramRenderer.render(diagram));
    }

    private String coverImageBase64() {
        try (InputStream stream = classpathStream(COVER_IMAGE_RESOURCE)) {
            return Base64.getEncoder().encodeToString(stream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read cover image resource: " + COVER_IMAGE_RESOURCE, e);
        }
    }

    private byte[] toPdfBytes(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // Without this, openhtmltopdf has no SVG rendering pipeline at
            // all -- the chord-diagram SVGs rendered completely blank (not
            // just wrongly styled) until this was added; see the
            // openhtmltopdf-svg-support dependency's comment in pom.xml.
            builder.useSVGDrawer(new BatikSVGDrawer());
            builder.withHtmlContent(xhtml, BASE_URI);
            builder.useFont(() -> classpathStream(MONO_REGULAR_RESOURCE), MONO_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> classpathStream(MONO_BOLD_RESOURCE), MONO_FAMILY, 700, FontStyle.NORMAL, true);
            builder.useFont(() -> classpathStream(SERIF_REGULAR_RESOURCE), SERIF_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> classpathStream(SERIF_BOLD_RESOURCE), SERIF_FAMILY, 700, FontStyle.NORMAL, true);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render PDF", e);
        }
    }

    private InputStream classpathStream(String classpathLocation) {
        InputStream stream = getClass().getResourceAsStream(classpathLocation);
        if (stream == null) {
            throw new IllegalStateException("Resource not found on classpath: " + classpathLocation);
        }
        return stream;
    }
}
