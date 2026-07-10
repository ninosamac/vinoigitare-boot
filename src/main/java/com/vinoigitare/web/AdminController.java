package com.vinoigitare.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.vinoigitare.model.Genre;
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

    /**
     * @param song       the underlying song
     * @param genreLabel the current, canonical display label for {@code
     *                   song.genre()} (resolved via {@link Genre#resolve},
     *                   not the raw stored value -- see that method's
     *                   Javadoc for why those can differ) or {@code null}
     *                   if no genre is assigned. Computed here rather than
     *                   in the template: calling {@code Genre.resolve(...)}
     *                   from inside admin/list.html's {@code th:each} would
     *                   hit the same restricted-expression guard {@code
     *                   ChordDiagramController} already worked around (see
     *                   its Javadoc).
     */
    public record SongRow(Song song, String genreLabel) {
    }

    @GetMapping
    public String list(Model model) {
        List<SongRow> rows = songService.loadAll().stream()
                .map(song -> new SongRow(song, Genre.resolve(song.genre()).map(Genre::label).orElse(null)))
                .toList();
        model.addAttribute("songs", rows);
        return "admin/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("songForm", new SongForm("", "", "", ""));
        model.addAttribute("isNew", true);
        model.addAttribute("genres", Genre.values());
        return "admin/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute SongForm songForm) {
        String genre = blankToNull(songForm.genre());
        songService.store(new Song(null, songForm.artist(), songForm.title(), null, genre, songForm.chords(),
                null, 0L));
        return "redirect:/admin";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable String id, Model model) {
        Song song = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        // Genre.resolve(...), not song.genre() directly: the select's
        // options now use slugs as their value (see admin/form.html), but
        // a song saved before that change -- or imported before the
        // English i18n switch -- may still have the display label (or
        // even the old Serbian label text) sitting in this column. Without
        // resolving to the slug here, th:field wouldn't match any option
        // for those rows, the dropdown would silently show "(no genre)",
        // and saving would then erase the genre entirely. Saving after
        // this also normalizes the stored value to the slug going forward.
        String genre = Genre.resolve(song.genre()).map(Genre::slug).orElse("");
        model.addAttribute("songForm", new SongForm(song.artist(), song.title(), song.chords(), genre));
        model.addAttribute("isNew", false);
        model.addAttribute("originalId", id);
        model.addAttribute("genres", Genre.values());
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
        // the (possibly changed) artist/title. Genre (Phase 4c) comes from
        // the form, not the existing row, since it's now editable.
        Song existing = songService.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Song not found: " + id));
        String genre = blankToNull(songForm.genre());
        Song updated = new Song(existing.id(), songForm.artist(), songForm.title(), null, genre,
                songForm.chords(), existing.createdAt(), existing.views());
        songService.store(updated);
        return "redirect:/admin";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable String id) {
        songService.remove(id);
        return "redirect:/admin";
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public record SongForm(String artist, String title, String chords, String genre) {
    }
}
