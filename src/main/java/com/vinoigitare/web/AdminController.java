package com.vinoigitare.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

/**
 * Minimal song CRUD, replacing the old {@code SongEditor} component and
 * {@code NewSongAction}/{@code EditSongAction}/{@code RemoveSongAction}.
 *
 * <p>TODO: this entire {@code /admin} path is UNAUTHENTICATED. There is no
 * Spring Security (or any other guard) in front of it yet -- deliberately
 * out of scope for Phase 1 per the migration plan, which defers accounts
 * to Phase 5. Do not expose this application beyond localhost / the home
 * lab network until at least basic auth is added here.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final SongService songService;

    public AdminController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("songs", songService.loadAll());
        return "admin/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("songForm", new SongForm("", "", ""));
        model.addAttribute("isNew", true);
        return "admin/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute SongForm songForm) {
        songService.store(new Song(songForm.artist(), songForm.title(), songForm.chords()));
        return "redirect:/admin";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable String id, Model model) {
        Song song = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        model.addAttribute("songForm", new SongForm(song.artist(), song.title(), song.chords()));
        model.addAttribute("isNew", false);
        model.addAttribute("originalId", id);
        return "admin/form";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable String id, @ModelAttribute SongForm songForm) {
        // Phase 4a: with the database-backed repository, id is a stable
        // numeric row id that never changes just because artist/title did
        // -- unlike the old file-storage scheme (still visible in
        // TextFileSongRepository), where the id *was* the filename and
        // editing artist/title meant deleting the old file and writing a
        // new one. Load the existing row first so its id/genre/createdAt
        // /views survive the edit; slug is passed as null so it's
        // recomputed from the (possibly changed) artist/title.
        Song existing = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        Song updated = new Song(existing.id(), songForm.artist(), songForm.title(), null, existing.genre(),
                songForm.chords(), existing.createdAt(), existing.views());
        songService.store(updated);
        return "redirect:/admin";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable String id) {
        songService.remove(id);
        return "redirect:/admin";
    }

    public record SongForm(String artist, String title, String chords) {
    }
}
