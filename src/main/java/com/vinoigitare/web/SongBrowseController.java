package com.vinoigitare.web;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/")
    public String index(Model model) {
        Map<String, List<Song>> songsByArtist = songService.loadAllGroupedByArtist();
        model.addAttribute("artists", songsByArtist);
        model.addAttribute("newestSongs", songService.loadNewest(HOMEPAGE_LIST_SIZE));
        model.addAttribute("popularSongs", songService.loadMostViewed(HOMEPAGE_LIST_SIZE));
        return "index";
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
