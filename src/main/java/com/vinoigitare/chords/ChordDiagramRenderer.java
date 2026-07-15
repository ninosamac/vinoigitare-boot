package com.vinoigitare.chords;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders a {@link ChordDiagram} as a small self-contained SVG fretboard
 * diagram: 6 strings, a 5-fret window, open/muted markers above the nut
 * (or a position label instead, if the window doesn't start at the nut --
 * see {@link ChordDiagram#baseFret()}), dots for fretted notes, and a
 * barre bar when the diagram says it's a barre chord ({@link
 * ChordDiagram#barreFret()} &gt; 0).
 *
 * <p>A Spring bean, called directly from {@code ChordDiagramController}
 * (in Java, not from the template): calling a bean method via {@code
 * @chordDiagramRenderer.render(...)} from inside {@code
 * chord-diagrams.html}'s {@code th:each} loop hit the same Thymeleaf
 * restricted-expression guard as the {@code songContent} fragment did
 * ("Instantiation of new objects and access to static classes or
 * parameters is forbidden in this context") -- so the controller renders
 * each diagram to SVG itself and hands the template a list of plain
 * (name, svg) pairs instead.
 */
@Component
public class ChordDiagramRenderer {

    private static final int STRING_COUNT = 6;
    private static final int FRETS_SHOWN = 5;
    private static final int MARGIN_LEFT = 15;
    private static final int MARGIN_TOP = 26;
    private static final int MARGIN_RIGHT = 22;
    private static final int STRING_SPACING = 16;
    private static final int FRET_SPACING = 18;
    private static final int DOT_RADIUS = 5;
    private static final int WIDTH = MARGIN_LEFT + STRING_SPACING * (STRING_COUNT - 1) + MARGIN_RIGHT;
    private static final int HEIGHT = MARGIN_TOP + FRET_SPACING * FRETS_SHOWN + 14;

    // Same resolved values as app.css's .chord-diagram-* rules (--ink-muted/
    // --ink/--accent), applied as real SVG presentation attributes
    // (stroke=/fill=/etc. as separate XML attributes) on every shape --
    // not left to the class attribute + external stylesheet alone. Real bug
    // found 2026-07-15, in two stages: browsers apply an external
    // stylesheet's class selectors into inline SVG just fine, but
    // openhtmltopdf's SVG rendering does not, so every shape here rendered
    // with no visible stroke/fill at all in the personalized-songbook PDF's
    // chord-reference section (only the marker text remained visible --
    // SVG text defaults to a black fill even with no CSS applied, which is
    // why that alone wasn't already a visible symptom on screen). A first
    // attempt at fixing this bundled the values into a single inline
    // style="" attribute instead -- that did NOT fix it either: confirmed
    // live (rendered output unchanged, byte-for-byte) that openhtmltopdf's
    // SVG support doesn't parse a style="" attribute as CSS at all, only
    // true separate presentation attributes. Those are the original,
    // pre-CSS SVG 1.1 styling mechanism, which is why they're the one
    // form virtually every SVG renderer (including a minimal/embedded
    // one like this) actually implements. class attributes are kept
    // alongside anyway, in case anything ever wants to override on-screen
    // only via app.css.
    private static final String STRING_ATTRS = "stroke=\"#6b675c\" stroke-width=\"1\"";
    private static final String FRET_ATTRS = "stroke=\"#6b675c\" stroke-width=\"1\"";
    private static final String NUT_ATTRS = "stroke=\"#2a2822\" stroke-width=\"2.5\"";
    private static final String BARRE_ATTRS =
            "stroke=\"#2f5d50\" stroke-width=\"8\" stroke-linecap=\"round\" opacity=\"0.55\"";
    private static final String DOT_ATTRS = "fill=\"#2f5d50\"";
    private static final String MARKER_ATTRS = "fill=\"#6b675c\" font-size=\"10px\"";

    public String render(ChordDiagram diagram) {
        int[] frets = diagram.frets();
        int baseFret = diagram.baseFret();
        StringBuilder svg = new StringBuilder(1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\" class=\"chord-diagram-svg\" role=\"img\" aria-label=\"")
                .append(HtmlUtils.htmlEscape(diagram.name(), "UTF-8")).append(" chord diagram\">");

        appendGrid(svg, baseFret);
        if (diagram.barreFret() > 0) {
            appendBarre(svg, frets, diagram.barreFret(), baseFret);
        }
        appendMarkersAndDots(svg, frets, baseFret);
        if (baseFret > 1) {
            appendPositionLabel(svg, baseFret);
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private void appendGrid(StringBuilder svg, int baseFret) {
        // Strings (vertical lines).
        for (int s = 0; s < STRING_COUNT; s++) {
            int x = stringX(s);
            svg.append("<line x1=\"").append(x).append("\" y1=\"").append(MARGIN_TOP)
                    .append("\" x2=\"").append(x).append("\" y2=\"").append(MARGIN_TOP + FRET_SPACING * FRETS_SHOWN)
                    .append("\" class=\"chord-diagram-string\" ").append(STRING_ATTRS).append("/>");
        }
        // Frets (horizontal lines); the top one is drawn thicker as the
        // nut, but only when this window actually starts at the nut --
        // otherwise (baseFret > 1) it's just another fret line, and the
        // position label (appendPositionLabel) makes clear this isn't
        // fret 0.
        for (int f = 0; f <= FRETS_SHOWN; f++) {
            int y = MARGIN_TOP + FRET_SPACING * f;
            boolean isNut = f == 0 && baseFret == 1;
            String cls = isNut ? "chord-diagram-nut" : "chord-diagram-fret";
            String attrs = isNut ? NUT_ATTRS : FRET_ATTRS;
            svg.append("<line x1=\"").append(stringX(0)).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(stringX(STRING_COUNT - 1)).append("\" y2=\"").append(y)
                    .append("\" class=\"").append(cls).append("\" ").append(attrs).append("/>");
        }
    }

    /**
     * Draws the barre bar spanning from the leftmost to the rightmost
     * non-muted string, at {@code barreFret}. Which chords are barre
     * chords is explicit data ({@link ChordDiagram#barreFret()}), not
     * inferred from the numbers -- see that field's Javadoc for why
     * inference alone is ambiguous (open D coincidentally shares a fret
     * across two strings without being a barre).
     */
    private void appendBarre(StringBuilder svg, int[] frets, int barreFret, int baseFret) {
        int leftmost = -1;
        int rightmost = -1;
        for (int s = 0; s < STRING_COUNT; s++) {
            if (frets[s] >= 0) {
                if (leftmost == -1) {
                    leftmost = s;
                }
                rightmost = s;
            }
        }
        if (leftmost == -1) {
            return;
        }
        int y = fretDotY(displayFret(barreFret, baseFret));
        svg.append("<line x1=\"").append(stringX(leftmost)).append("\" y1=\"").append(y)
                .append("\" x2=\"").append(stringX(rightmost)).append("\" y2=\"").append(y)
                .append("\" class=\"chord-diagram-barre\" ").append(BARRE_ATTRS).append("/>");
    }

    private void appendMarkersAndDots(StringBuilder svg, int[] frets, int baseFret) {
        for (int s = 0; s < STRING_COUNT; s++) {
            int fret = frets[s];
            int x = stringX(s);
            if (fret < 0) {
                svg.append("<text x=\"").append(x).append("\" y=\"").append(MARGIN_TOP - 10)
                        .append("\" class=\"chord-diagram-marker\" ").append(MARKER_ATTRS)
                        .append(" text-anchor=\"middle\">&#215;</text>");
            } else if (fret == 0) {
                svg.append("<text x=\"").append(x).append("\" y=\"").append(MARGIN_TOP - 10)
                        .append("\" class=\"chord-diagram-marker\" ").append(MARKER_ATTRS)
                        .append(" text-anchor=\"middle\">&#9675;</text>");
            } else {
                svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(fretDotY(displayFret(fret, baseFret)))
                        .append("\" r=\"").append(DOT_RADIUS).append("\" class=\"chord-diagram-dot\" ")
                        .append(DOT_ATTRS).append("/>");
            }
        }
    }

    /**
     * Small "Nfr" label to the right of the grid, the standard printed-
     * chord-book convention for marking that the diagram's window starts
     * at fret N rather than at the nut.
     */
    private void appendPositionLabel(StringBuilder svg, int baseFret) {
        int x = stringX(STRING_COUNT - 1) + 8;
        int y = fretDotY(1) + 4;
        svg.append("<text x=\"").append(x).append("\" y=\"").append(y)
                .append("\" class=\"chord-diagram-marker\" ").append(MARKER_ATTRS)
                .append(" text-anchor=\"start\">")
                .append(baseFret).append("fr</text>");
    }

    /** Absolute fret N, drawn as if the window starts at {@code baseFret}. */
    private int displayFret(int absoluteFret, int baseFret) {
        return absoluteFret - baseFret + 1;
    }

    private int stringX(int stringIndex) {
        return MARGIN_LEFT + STRING_SPACING * stringIndex;
    }

    private int fretDotY(int displayFret) {
        return MARGIN_TOP + (int) Math.round(FRET_SPACING * (displayFret - 0.5));
    }
}
