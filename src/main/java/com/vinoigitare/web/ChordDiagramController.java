package com.vinoigitare.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.vinoigitare.chords.ChordDiagram;
import com.vinoigitare.chords.ChordDiagramCatalog;
import com.vinoigitare.chords.ChordDiagramRenderer;
import com.vinoigitare.chords.RenderedChordDiagram;
import com.vinoigitare.chords.RenderedChordSection;
import com.vinoigitare.chords.SongChord;

/**
 * Static chord-diagram reference page (Phase 7, per the migration plan --
 * mirrors pesmarica.rs's {@code /dijagramiakorda}). No database, no
 * per-song state: just the fixed catalog in {@link ChordDiagramCatalog}.
 *
 * <p>{@link RenderedChordSection}/{@link RenderedChordDiagram} used to be
 * nested here, but the personalized-songbook PDF's chord-reference section
 * (see {@code com.vinoigitare.pdf.SongbookPdfRenderer}) needs the exact
 * same rendered shape, and a {@code pdf}-package class depending on
 * nested types from this {@code web}-package controller would be backwards
 * layering -- moved to the {@code chords} package instead, where both
 * consumers can reach them cleanly.
 */
@Controller
public class ChordDiagramController {

    private final ChordDiagramRenderer chordDiagramRenderer;

    public ChordDiagramController(ChordDiagramRenderer chordDiagramRenderer) {
        this.chordDiagramRenderer = chordDiagramRenderer;
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
        List<RenderedChordSection> sections = ChordDiagramCatalog.sections().stream()
                .map(section -> new RenderedChordSection(section.labelKey(),
                        section.chords().stream().map(this::render).toList()))
                .toList();
        model.addAttribute("sections", sections);
        return "chord-diagrams";
    }

    private RenderedChordDiagram render(ChordDiagram diagram) {
        return new RenderedChordDiagram(diagram.name(), chordDiagramRenderer.render(diagram), diagram.fretsCsv());
    }

    /**
     * The whole catalog as {@code [{name, fretsCsv}, ...]} (issue #13,
     * 2026-07-22) -- for {@code song-chords.js}'s name -&gt; fret-data
     * lookup on the song page, which needs to resolve a chord's fret data
     * *after* transposing it client-side (the transposed name might be one
     * the song never originally used, e.g. G transposed +2 becomes A, so
     * the song page can't just carry its own few chords' data down with
     * it -- see the plan doc's decision on this). Small, static, and
     * identical for every page, so it's a good fit for the browser's/service
     * worker's own HTTP caching rather than embedding it inline per song page.
     */
    @GetMapping("/chord-diagrams/catalog.json")
    @ResponseBody
    public List<SongChord> catalogJson() {
        return ChordDiagramCatalog.all().stream()
                .map(diagram -> new SongChord(diagram.name(), diagram.fretsCsv()))
                .toList();
    }
}
