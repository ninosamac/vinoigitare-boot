package com.vinoigitare.search;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

/**
 * Replaces the old {@code Song.getSearchableText()} plus the
 * {@code Criteria}/{@code Filter}/{@code ANDCriteria}/{@code SimpleFilter}
 * framework from {@code Vinoigitare_Utils}.
 *
 * <p><b>The encoding fix continues here (see the migration plan, section
 * 1):</b> the old {@code getSearchableText()} patched around broken
 * diacritics with hardcoded {@code .replace('�', 's')} etc. This class
 * does real Unicode normalization instead: {@link Normalizer.Form#NFD}
 * decomposes each accented letter into a base letter plus a combining mark
 * (e.g. {@code č} -> {@code c} + U+030C), then combining marks
 * ({@code \p{M}}) are stripped and the result lowercased. Applying this to
 * both the query and the searchable text means {@code "cacak"} and
 * {@code "čačak"} match each other in both directions.
 *
 * <p>Multi-term queries are split on whitespace and ANDed: a song matches
 * only if every term is found somewhere in its normalized searchable text
 * (artist + title + chords). This replaces {@code ANDCriteria} +
 * {@code SimpleFilter} with a plain {@link Predicate} composition -- no
 * framework needed for what is, in the end, "does every term appear in this
 * blob of text."
 */
@Service
public class SearchService {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");

    private final SongService songService;

    public SearchService(SongService songService) {
        this.songService = songService;
    }

    /**
     * Songs matching every whitespace-separated term of {@code query},
     * diacritic-insensitively. A {@code null}, empty, or blank query
     * returns an empty list (see {@code com.vinoigitare.web.SearchController}
     * for how that's surfaced to the user).
     */
    public List<Song> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> terms = Arrays.stream(query.trim().split("\\s+"))
                .map(SearchService::normalize)
                .filter(term -> !term.isEmpty())
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }

        Predicate<Song> matchesAllTerms = song -> {
            String haystack = normalize(searchableText(song));
            return terms.stream().allMatch(haystack::contains);
        };

        return songService.loadAll().stream()
                .filter(matchesAllTerms)
                .sorted(Comparator.comparing(Song::artist, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Song::title, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private static String searchableText(Song song) {
        return song.artist() + " " + song.title() + " " + song.chords();
    }

    /**
     * NFD-decompose, strip combining marks, lowercase. E.g. {@code "Čačak"}
     * and {@code "cacak"} both normalize to {@code "cacak"}.
     */
    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return withoutMarks.toLowerCase(Locale.ROOT);
    }
}
