package com.vinoigitare.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Every route here requires the single admin credential configured in
 * {@code application.yml} -- see {@link com.vinoigitare.security.SecurityConfig}
 * and the admin-auth plan (~/knowledge/projects/vinoigitare/admin-auth-plan.md)
 * for the full rationale. Resolves what used to be a standing TODO on this
 * exact class.
 *
 * <p><b>Genre removed entirely (2026-07-12):</b> it was assigned round-robin
 * at import time purely so the (now-removed) public genre-browsing tab had
 * something in every category, never a real, human-curated attribute --
 * "crazy" once that context is gone, per Nino. Dropped from the {@code Song}
 * model, the database column, and this admin form -- see
 * {@code ~/knowledge/projects/vinoigitare/progress.md} for the full story.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger LOG = LoggerFactory.getLogger(AdminController.class);

    private final SongService songService;

    public AdminController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping
    public String list() {
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
        Song created = songService
                .store(new Song(null, songForm.artist(), songForm.title(), null, songForm.chords(), null, 0L));
        LOG.info("Admin created song {} ({} - {})", created.id(), created.artist(), created.title());
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
        // new one. Load the existing row first so its id/createdAt/views
        // survive the edit; slug is passed as null so it's recomputed from
        // the (possibly changed) artist/title.
        Song existing = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        Song updated = new Song(existing.id(), songForm.artist(), songForm.title(), null, songForm.chords(),
                existing.createdAt(), existing.views());
        songService.store(updated);
        LOG.info("Admin updated song {} ({} - {} -> {} - {})", id, existing.artist(), existing.title(),
                updated.artist(), updated.title());
        return "redirect:/admin";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable String id) {
        Song existing = songService.load(id).orElse(null);
        songService.remove(id);
        if (existing != null) {
            LOG.info("Admin deleted song {} ({} - {})", id, existing.artist(), existing.title());
        } else {
            LOG.info("Admin deleted song {} (already gone)", id);
        }
        return "redirect:/admin";
    }

    public record SongForm(String artist, String title, String chords) {
    }
}
