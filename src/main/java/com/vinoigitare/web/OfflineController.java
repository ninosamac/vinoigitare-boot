package com.vinoigitare.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Fallback page static/sw.js serves (from its precache) when a navigation
 * request fails offline and nothing matching is already cached. Mirrors
 * {@link AboutController}'s shape -- no model, no database.
 */
@Controller
public class OfflineController {

    @GetMapping("/offline")
    public String offline() {
        return "offline";
    }
}
