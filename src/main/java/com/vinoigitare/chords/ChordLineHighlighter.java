package com.vinoigitare.chords;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Wraps detected chord lines in {@code <span class="chord-line">} so CSS
 * can tint them differently from the lyrics they annotate (the "Sveska"
 * visual direction's fountain-pen-ink motif -- see {@code app.css}).
 *
 * <p>Reuses {@link ChordTransposer#isHighlightableChordLine} so highlighting
 * and transposition can never disagree about which lines are chord lines.
 * Called from {@code fragments.html}'s {@code songContent} fragment via
 * {@code th:utext="${@chordLineHighlighter.render(song.chords())}"} (not
 * {@code th:text}): since this returns real HTML rather than plain text
 * that Thymeleaf would auto-escape, every line's text is escaped here
 * instead -- required because {@link com.vinoigitare.model.Song#chords()}
 * is user-editable content (the admin form), not trusted markup.
 *
 * <p>A Spring bean (not a static utility, despite having no real
 * instance state) specifically so the template can reach it via
 * {@code @chordLineHighlighter.render(...)} -- Thymeleaf's restricted
 * expression contexts reject {@code T(SomeClass).staticMethod(...)}
 * ("Instantiation of new objects and access to static classes or
 * parameters is forbidden in this context"), the same class of guard as
 * the {@code th:onsubmit} restriction {@code AdminController}'s delete
 * button hit -- but referencing an existing Spring bean via {@code @} is
 * allowed.
 */
@Component
public class ChordLineHighlighter {

    public String render(String chords) {
        String[] lines = chords.split("\n", -1);
        StringBuilder html = new StringBuilder(chords.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            // The explicit-encoding overload, not the no-arg one: the
            // no-arg overload escapes every non-ASCII character as a named
            // HTML entity (e.g. "&scaron;" for s-caron/s), which openhtmltopdf's
            // strict XML parser rejects outright (undeclared entity, no
            // DTD) -- exactly the diacritics this PDF feature exists for.
            // Passing UTF-8 tells it those characters are representable
            // as-is, so only the handful of actually XML-unsafe characters
            // (&, <, >, ", ') get entity-escaped.
            String escaped = HtmlUtils.htmlEscape(lines[i], "UTF-8");
            if (ChordTransposer.isHighlightableChordLine(lines, i)) {
                html.append("<span class=\"chord-line\">").append(escaped).append("</span>");
            } else {
                html.append(escaped);
            }
            if (i < lines.length - 1) {
                html.append('\n');
            }
        }
        return html.toString();
    }
}
