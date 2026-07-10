package com.vinoigitare.chords;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders a {@link ChordDiagram} as a small self-contained SVG fretboard
 * diagram: 6 strings, 5 frets, open/muted markers above the nut, dots for
 * fretted notes, and a barre bar when the diagram says it's a barre chord
 * ({@link ChordDiagram#barreFret()} &gt; 0).
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
    private static final int STRING_SPACING = 16;
    private static final int FRET_SPACING = 18;
    private static final int DOT_RADIUS = 5;
    private static final int WIDTH = MARGIN_LEFT * 2 + STRING_SPACING * (STRING_COUNT - 1);
    private static final int HEIGHT = MARGIN_TOP + FRET_SPACING * FRETS_SHOWN + 14;

    public String render(ChordDiagram diagram) {
        int[] frets = diagram.frets();
        StringBuilder svg = new StringBuilder(1024);
        svg.append("<svg viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\" class=\"chord-diagram-svg\" role=\"img\" aria-label=\"")
                .append(HtmlUtils.htmlEscape(diagram.name(), "UTF-8")).append(" chord diagram\">");

        appendGrid(svg);
        if (diagram.barreFret() > 0) {
            appendBarre(svg, frets, diagram.barreFret());
        }
        appendMarkersAndDots(svg, frets);

        svg.append("</svg>");
        return svg.toString();
    }

    private void appendGrid(StringBuilder svg) {
        // Strings (vertical lines).
        for (int s = 0; s < STRING_COUNT; s++) {
            int x = stringX(s);
            svg.append("<line x1=\"").append(x).append("\" y1=\"").append(MARGIN_TOP)
                    .append("\" x2=\"").append(x).append("\" y2=\"").append(MARGIN_TOP + FRET_SPACING * FRETS_SHOWN)
                    .append("\" class=\"chord-diagram-string\"/>");
        }
        // Frets (horizontal lines); the top one (the nut) is drawn thicker.
        for (int f = 0; f <= FRETS_SHOWN; f++) {
            int y = MARGIN_TOP + FRET_SPACING * f;
            String cls = f == 0 ? "chord-diagram-nut" : "chord-diagram-fret";
            svg.append("<line x1=\"").append(stringX(0)).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(stringX(STRING_COUNT - 1)).append("\" y2=\"").append(y)
                    .append("\" class=\"").append(cls).append("\"/>");
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
    private void appendBarre(StringBuilder svg, int[] frets, int barreFret) {
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
        int y = fretDotY(barreFret);
        svg.append("<line x1=\"").append(stringX(leftmost)).append("\" y1=\"").append(y)
                .append("\" x2=\"").append(stringX(rightmost)).append("\" y2=\"").append(y)
                .append("\" class=\"chord-diagram-barre\"/>");
    }

    private void appendMarkersAndDots(StringBuilder svg, int[] frets) {
        for (int s = 0; s < STRING_COUNT; s++) {
            int fret = frets[s];
            int x = stringX(s);
            if (fret < 0) {
                svg.append("<text x=\"").append(x).append("\" y=\"").append(MARGIN_TOP - 10)
                        .append("\" class=\"chord-diagram-marker\" text-anchor=\"middle\">&#215;</text>");
            } else if (fret == 0) {
                svg.append("<text x=\"").append(x).append("\" y=\"").append(MARGIN_TOP - 10)
                        .append("\" class=\"chord-diagram-marker\" text-anchor=\"middle\">&#9675;</text>");
            } else {
                svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(fretDotY(fret))
                        .append("\" r=\"").append(DOT_RADIUS).append("\" class=\"chord-diagram-dot\"/>");
            }
        }
    }

    private int stringX(int stringIndex) {
        return MARGIN_LEFT + STRING_SPACING * stringIndex;
    }

    private int fretDotY(int fret) {
        return MARGIN_TOP + (int) Math.round(FRET_SPACING * (fret - 0.5));
    }
}
