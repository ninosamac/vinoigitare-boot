package com.vinoigitare.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Static About page: project motivation, privacy notice, and a
 * support/Ko-fi button. No database, no per-request state -- mirrors
 * {@link ChordDiagramController}'s shape (the closest existing precedent
 * for a static-content page in this app), just simpler still, since
 * there's no catalog to render into a model. All content lives directly
 * in {@code about.html} via message-bundle keys.
 *
 * <p>Replaces the removed public Genres browsing tab in the nav (see
 * {@code ~/knowledge/projects/vinoigitare/about-page-and-genre-removal-plan.md}).
 * All content is now real, final copy: motivation, privacy, and a plain
 * (not floating-widget) link to Nino's actual Ko-fi page, confirmed
 * 2026-07-12.
 */
@Controller
public class AboutController {

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
