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
import com.vinoigitare.model.CroatianCollator;
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
 * <p>Phase 4e: every successful song-page load (not the PDF download, not
 * admin) counts as one view.
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
        return "index";
    }

    /**
     * Groups an already-artist-sorted {@code Map<String, List<Song>>} (see
     * {@link SongService#loadAllGroupedByArtist}) by first letter, without
     * disturbing that existing order -- iterating the source map in its
     * own (locale-collated alphabetical) order and appending to each
     * letter's list means every per-letter list comes out sorted for free.
     *
     * <p>The letter groups themselves also need {@link CroatianCollator},
     * not {@code TreeMap}'s default {@code Character} ordering -- a real
     * bug found 2026-07-19 (Nino): plain {@code Character} comparison is
     * raw code-point order, which put the {@code Č}/{@code Ć}/{@code Đ}/
     * {@code Š}/{@code Ž} letter-group headings all the way at the bottom
     * of the homepage tree, after {@code Z}, instead of {@code Č}/{@code Ć}
     * right after {@code C}, {@code Đ} right after {@code D}, and {@code Š}
     * right after {@code S} (only {@code Ž} genuinely belongs at the end).
     */
    private static List<LetterGroup> buildArtistTree(Map<String, List<Song>> songsByArtist) {
        Map<Character, List<ArtistEntry>> byLetter = new TreeMap<>(CroatianCollator.charComparator());
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
        model.addAttribute("metaDescription", metaDescriptionFor(artist, songs.size()));
        model.addAttribute("pageTitle", pageTitleFor(artist));
        return "artist";
    }

    /** Cap for the "More by [Artist]" list on the song page -- see #song. */
    private static final int MORE_BY_ARTIST_LIMIT = 8;

    @GetMapping("/akordi/{id}/{slug}")
    public String song(@PathVariable String id, @PathVariable String slug, Model model) {
        Song song = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        songService.recordView(id);
        model.addAttribute("song", song);
        model.addAttribute("metaDescription", metaDescriptionFor(song));
        model.addAttribute("pageTitle", pageTitleFor(song));

        // Internal linking (2026-07-22, issue #10): "more songs by the
        // same artist" is the one linking signal actually backed by real
        // data -- no genre/tag/similarity data exists to build a genuine
        // "related songs" list from (see internal-linking-plan.md).
        // Capped, not the full list -- some artists have 50+ songs, and
        // dumping all of them here would bury the actual chords content
        // the page exists for; a "See all" link covers the rest via the
        // artist page, which already lists everything.
        List<Song> songsByArtist = songService.loadByArtist(song.artist());
        List<Song> otherSongsByArtist = songsByArtist.stream()
                .filter(other -> !other.id().equals(song.id()))
                .toList();
        model.addAttribute("otherSongsByArtist", otherSongsByArtist.stream().limit(MORE_BY_ARTIST_LIMIT).toList());
        // The artist's real total (including this song) -- what the "See
        // all N songs by [Artist]" link promises, matching the count the
        // artist page itself will show once clicked through to.
        model.addAttribute("totalSongsByArtist", songsByArtist.size());

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

    /**
     * SEO (2026-07-20): "{Artist} - akordi i tekstovi pjesama, {N} pjesama."
     * via the {@code artist.metaDescription} message key -- targets
     * "{artist name} akordi" searches, the natural way a visitor looks for
     * a specific artist's chords. No single lyric line to excerpt here
     * (unlike {@link #metaDescriptionFor(Song)} above), so the song count
     * fills that role instead: a real, concrete fact about the page, not
     * padding.
     */
    private String metaDescriptionFor(String artist, int songCount) {
        return messageSource.getMessage("artist.metaDescription",
                new Object[] {artist, songCount}, LocaleContextHolder.getLocale());
    }

    /**
     * SEO/CTR (2026-07-22, issue #11): a real 28-day Search Console export
     * showed 189 of 198 pages getting zero clicks despite real
     * impressions at decent positions -- the {@code <title>} tag (the
     * actual clickable blue link text in results, the single biggest CTR
     * lever) mentioned neither "akordi" nor "chords" anywhere, unlike the
     * meta description. {@code song.pageTitle} ("{Artist} - {Title}:
     * Akordi i tekst") puts the keyword before the "- Vino i gitare"
     * suffix {@code song.html} appends, so it survives Google's ~50-60
     * character truncation even if the tail doesn't.
     */
    private String pageTitleFor(Song song) {
        return messageSource.getMessage("song.pageTitle",
                new Object[] {song.artist(), song.title()}, LocaleContextHolder.getLocale());
    }

    /** Same reasoning as {@link #pageTitleFor(Song)}, for the artist page. */
    private String pageTitleFor(String artist) {
        return messageSource.getMessage("artist.pageTitle",
                new Object[] {artist}, LocaleContextHolder.getLocale());
    }
}
