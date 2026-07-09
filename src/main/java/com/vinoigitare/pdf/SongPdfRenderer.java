package com.vinoigitare.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import com.vinoigitare.chords.ChordTransposer;
import com.vinoigitare.model.Song;

/**
 * Renders a {@link Song} to PDF bytes, reusing the exact chords/lyrics text
 * and print layout ({@code templates/song-pdf.html}) that the on-screen
 * song page shows -- "PDF of what you see on screen," per the migration
 * plan. This is what the old (never-implemented, 7-line stub)
 * {@code Vinoigitare_Utils} {@code SongPDF} class was meant to be.
 *
 * <p>Fonts are embedded explicitly (DejaVu Sans Mono, regular + bold,
 * bundled under {@code src/main/resources/fonts/}) via openhtmltopdf's
 * {@code useFont} API, loaded as classpath resources rather than
 * filesystem paths so this also works when packaged into the executable
 * fat jar. This matters because the PDF's whole point is showing š/đ/č/ć/ž
 * correctly -- the default PDF base-14 fonts don't cover Latin Extended,
 * silently dropping or mangling those characters otherwise (see the
 * migration plan's encoding-fix note).
 */
@Component
public class SongPdfRenderer {

    private static final String REGULAR_FONT_RESOURCE = "/fonts/DejaVuSansMono.ttf";
    private static final String BOLD_FONT_RESOURCE = "/fonts/DejaVuSansMono-Bold.ttf";
    private static final String FONT_FAMILY = "DejaVu Sans Mono";

    // No relative resources (images, external CSS) are referenced from the
    // PDF template, so this is just a placeholder base URI for openhtmltopdf's
    // resource resolver.
    private static final String BASE_URI = "pdf://vinoigitare/";

    private final ITemplateEngine templateEngine;

    public SongPdfRenderer(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * @param song song to render
     * @param transposeSemitones semitone offset applied to the chords
     *                            before rendering (0 = as stored). Uses
     *                            {@link ChordTransposer}, the same
     *                            algorithm (independently reimplemented,
     *                            not shared code) as the on-screen song
     *                            page's client-side transpose buttons --
     *                            see {@code
     *                            static/js/transpose.js}. The song itself
     *                            is never mutated; only the copy handed to
     *                            the template is.
     */
    public byte[] render(Song song, int transposeSemitones) {
        String xhtml = renderXhtml(song, transposeSemitones);
        return toPdfBytes(xhtml);
    }

    private String renderXhtml(Song song, int transposeSemitones) {
        Song forRendering = transposeSemitones == 0 ? song : withTransposedChords(song, transposeSemitones);
        Context context = new Context();
        context.setVariable("song", forRendering);
        return templateEngine.process("song-pdf", context);
    }

    private static Song withTransposedChords(Song song, int transposeSemitones) {
        String transposedChords = ChordTransposer.transpose(song.chords(), transposeSemitones);
        return new Song(song.id(), song.artist(), song.title(), song.slug(), song.genre(), transposedChords,
                song.createdAt(), song.views());
    }

    private byte[] toPdfBytes(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, BASE_URI);
            builder.useFont(() -> fontStream(REGULAR_FONT_RESOURCE), FONT_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> fontStream(BOLD_FONT_RESOURCE), FONT_FAMILY, 700, FontStyle.NORMAL, true);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render PDF", e);
        }
    }

    private InputStream fontStream(String classpathLocation) {
        InputStream stream = getClass().getResourceAsStream(classpathLocation);
        if (stream == null) {
            throw new IllegalStateException("Font resource not found on classpath: " + classpathLocation);
        }
        return stream;
    }
}
