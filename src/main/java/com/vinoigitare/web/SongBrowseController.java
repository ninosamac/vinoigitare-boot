package com.vinoigitare.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.vinoigitare.chords.ChordTransposer;
import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

/**
 * Read-only song browsing: artist index, artist page, song view.
 *
 * <p>Replaces the old Vaadin {@code SongTree}/{@code Navigator} components
 * and {@code SongViewer} panel with plain server-rendered pages.
 *
 * <p><b>Phase 4a URL scheme change:</b> the song view route is now {@code
 * /akordi/{id}/{slug}}, matching pesmarica.rs's own {@code
 * /akordi/{id}/{Artist-Name}--{Song-Title}} pattern, and replaces the
 * Phase 1-3 {@code /songs/{id}} route outright (no redirect kept: nothing
 * external depends on the old URL yet, and the plan explicitly allows
 * dropping it in that case). {@code id} is now expected to be the numeric
 * database id from {@link DatabaseSongRepository}; {@code slug} is
 * decorative for readability/SEO and isn't validated against the song's
 * canonical slug -- the lookup is by id alone, same lenient approach
 * pesmarica.rs itself appears to take.
 *
 * <p>Phase 4e: the homepage also shows "newest" and "popular" lists, and
 * every successful song-page load (not the PDF download, not admin) counts
 * as one view.
 *
 * <p><b>Homepage artist tree (rethinking the main page for scale):</b> the
 * old homepage was a single flat, unsectioned artist list -- fine for a
 * handful of artists, unusable once there are hundreds of artists and
 * thousands of songs (pesmarica.rs's own homepage has exactly this
 * problem). Replaced with a letter-grouped, expandable tree -- artists as
 * "folders", songs as "files" -- mirroring the physical songbook's own
 * table-of-contents principle (the real PDF this project is modeled on).
 * {@link #buildArtistTree} does the grouping here in Java, not in the
 * template: Thymeleaf's restricted-expression guard rejects
 * {@code T()}/{@code new}/{@code @bean} access inside a {@code th:each}
 * loop on a directly-rendered page (see {@code ChordDiagramController}'s
 * Javadoc for the first time this was hit), and grouping-by-letter needs
 * exactly that kind of computation.
 */
@Controller
public class SongBrowseController {

    private static final int HOMEPAGE_LIST_SIZE = 5;

    private final SongService songService;
    private final MessageSource messageSource;

    public SongBrowseController(SongService songService, MessageSource messageSource) {
        this.songService = songService;
        this.messageSource = messageSource;
    }

    /** One letter's worth of artists in the homepage tree, e.g. all of "B". */
    public record LetterGroup(String letter, List<ArtistEntry> artists) {
    }

    /** One artist's node in the tree: their name and all of their songs (already sorted by title). */
    public record ArtistEntry(String artist, List<Song> songs) {
    }

    @GetMapping("/")
    public String index(Model model) {
        Map<String, List<Song>> songsByArtist = songService.loadAllGroupedByArtist();
        model.addAttribute("artistTree", buildArtistTree(songsByArtist));
        model.addAttribute("totalArtists", songsByArtist.size());
        model.addAttribute("totalSongs", songsByArtist.values().stream().mapToInt(List::size).sum());
        model.addAttribute("newestSongs", songService.loadNewest(HOMEPAGE_LIST_SIZE));
        model.addAttribute("popularSongs", songService.loadMostViewed(HOMEPAGE_LIST_SIZE));
        return "index";
    }

    /**
     * Groups an already-artist-sorted {@code Map<String, List<Song>>} (see
     * {@link SongService#loadAllGroupedByArtist}) by first letter, without
     * disturbing that existing order -- iterating the source map in its
     * own (case-insensitive alphabetical) order and appending to each
     * letter's list means every per-letter list comes out sorted for free.
     */
    private static List<LetterGroup> buildArtistTree(Map<String, List<Song>> songsByArtist) {
        Map<Character, List<ArtistEntry>> byLetter = new TreeMap<>();
        songsByArtist.forEach((artist, songs) -> byLetter
                .computeIfAbsent(AlphabeticalIndex.firstLetter(artist), letter -> new ArrayList<>())
                .add(new ArtistEntry(artist, songs)));
        return byLetter.entrySet().stream()
                .map(entry -> new LetterGroup(String.valueOf(entry.getKey()), entry.getValue()))
                .toList();
    }

    @GetMapping("/artists/{artist}")
    public String artist(@PathVariable String artist, Model model) {
        List<Song> songs = songService.loadByArtist(artist);
        model.addAttribute("artist", artist);
        model.addAttribute("songs", songs);
        return "artist";
    }

    @GetMapping("/akordi/{id}/{slug}")
    public String song(@PathVariable String id, @PathVariable String slug, Model model) {
        Song song = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        songService.recordView(id);
        model.addAttribute("song", song);
        model.addAttribute("metaDescription", metaDescriptionFor(song));
        return "song";
    }

    /**
     * Phase 7: full-screen "performance view" -- large font, auto-scroll,
     * and minimal chrome (no navbar/search box), for actually playing a
     * song from a music stand rather than reading it at a desk. Counts as
     * a view the same as the normal song page, since it's still a real
     * page load of the song, not a derived artifact like the PDF download.
     */
    @GetMapping("/akordi/{id}/live")
    public String liveView(@PathVariable String id, Model model) {
        Song song = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        songService.recordView(id);
        model.addAttribute("song", song);
        return "song-live";
    }

    /**
     * Phase 4f (SEO): "Artist - Title: chords and lyrics." (via the
     * {@code seo.chordsAndLyrics} message key, {@code messages.properties}
     * -- English by default, {@code messages_sr.properties} keeps the
     * original Serbian) plus, where available, the first actual lyric
     * line (skipping any chord-only lines at the top, via {@link
     * ChordTransposer#isChordLine}, so the excerpt is real words, not "C G
     * Am F").
     */
    private String metaDescriptionFor(Song song) {
        String base = messageSource.getMessage("seo.chordsAndLyrics",
                new Object[] {song.artist(), song.title()}, LocaleContextHolder.getLocale());
        String firstLyricLine = song.chords().lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !ChordTransposer.isChordLine(line))
                .findFirst()
                .orElse("");
        return firstLyricLine.isEmpty() ? base : base + " " + firstLyricLine;
    }
}
