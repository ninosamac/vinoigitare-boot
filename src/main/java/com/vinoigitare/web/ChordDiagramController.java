package com.vinoigitare.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.vinoigitare.chords.ChordDiagram;
import com.vinoigitare.chords.ChordDiagramCatalog;
import com.vinoigitare.chords.ChordDiagramRenderer;

/**
 * Static chord-diagram reference page (Phase 7, per the migration plan --
 * mirrors pesmarica.rs's {@code /dijagramiakorda}). No database, no
 * per-song state: just the fixed catalog in {@link ChordDiagramCatalog}.
 */
@Controller
public class ChordDiagramController {

    private final ChordDiagramRenderer chordDiagramRenderer;

    public ChordDiagramController(ChordDiagramRenderer chordDiagramRenderer) {
        this.chordDiagramRenderer = chordDiagramRenderer;
    }

    /**
     * @param name chord name, for the template's label
     * @param svg  pre-rendered SVG markup for that chord
     */
    public record RenderedDiagram(String name, String svg) {
    }

    /** @param labelKey message-bundle key for the section heading, resolved in the template via {@code #{...}} */
    public record RenderedSection(String labelKey, List<RenderedDiagram> diagrams) {
    }

    @GetMapping("/chord-diagrams")
    public String chordDiagrams(Model model) {
        // Rendered here (plain Java), not via a Thymeleaf expression in
        // the template itself: calling @chordDiagramRenderer.render(...)
        // from inside chord-diagrams.html's th:each loop hit the same
        // Thymeleaf restricted-expression guard the songContent fragment
        // did (see ChordDiagramRenderer's Javadoc) -- so the template
        // just gets a plain list of already-rendered (name, svg) pairs,
        // grouped into sections.
        List<RenderedSection> sections = ChordDiagramCatalog.sections().stream()
                .map(section -> new RenderedSection(section.labelKey(),
                        section.chords().stream().map(this::render).toList()))
                .toList();
        model.addAttribute("sections", sections);
        return "chord-diagrams";
    }

    private RenderedDiagram render(ChordDiagram diagram) {
        return new RenderedDiagram(diagram.name(), chordDiagramRenderer.render(diagram));
    }
}
