package com.vinoigitare.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.vinoigitare.model.Song;
import com.vinoigitare.search.SearchService;

/**
 * Search endpoint, replacing the old {@code SearchDialog}/{@code SearchAction}
 * /{@code SearchEvent} trio. Mirrors pesmarica.rs's {@code /pesme/pretraga?q=}
 * shape with {@code /search?q=}.
 *
 * <p>Decision: a blank/missing query renders the search page with an
 * empty-state message rather than redirecting to {@code /} or returning an
 * error. This keeps the query box and page visible (so the user can just
 * type something) and mirrors how pesmarica.rs itself handles an empty
 * search ("Nema rezultata za ''").
 */
@Controller
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String q, Model model) {
        String query = q == null ? "" : q.trim();
        List<Song> results = query.isBlank() ? List.of() : searchService.search(query);
        model.addAttribute("query", query);
        model.addAttribute("results", results);
        return "search";
    }
}
