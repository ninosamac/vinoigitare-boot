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
 * {@code ~/knowledge/projects/vinoigitare/about-page-and-genre-removal-plan.md}
 * -- Stage 2 of that plan). Placeholder copy for the motivation/privacy/
 * support sections ships first so the page and its layout can be reviewed
 * before real content (Ko-fi handle, final wording) is confirmed -- see
 * that plan doc's open questions.
 */
@Controller
public class AboutController {

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
