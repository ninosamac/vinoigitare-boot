package com.vinoigitare.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

/**
 * Read-only song browsing: artist index, artist page, song view.
 *
 * <p>Replaces the old Vaadin {@code SongTree}/{@code Navigator} components
 * and {@code SongViewer} panel with plain server-rendered pages.
 *
 * <p>Known limitation carried over from the old app's id scheme: artist and
 * song ids are used directly as path segments (URL-encoded by Thymeleaf /
 * decoded by Spring), so an id containing a literal "/" would break
 * routing. Not a real risk for the current sample data; the plan's Phase 4
 * introduces numeric ids + slugs, which removes this problem entirely.
 */
@Controller
public class SongBrowseController {

    private final SongService songService;

    public SongBrowseController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping("/")
    public String index(Model model) {
        Map<String, List<Song>> songsByArtist = songService.loadAllGroupedByArtist();
        model.addAttribute("artists", songsByArtist);
        return "index";
    }

    @GetMapping("/artists/{artist}")
    public String artist(@PathVariable String artist, Model model) {
        List<Song> songs = songService.loadByArtist(artist);
        model.addAttribute("artist", artist);
        model.addAttribute("songs", songs);
        return "artist";
    }

    @GetMapping("/songs/{id}")
    public String song(@PathVariable String id, Model model) {
        Song song = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        model.addAttribute("song", song);
        return "song";
    }
}
