package com.vinoigitare.web;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
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

import com.vinoigitare.analytics.LogAnalyticsRepository;
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
    private final LogAnalyticsRepository logAnalyticsRepository;

    public AdminController(SongService songService, LogAnalyticsRepository logAnalyticsRepository) {
        this.songService = songService;
        this.logAnalyticsRepository = logAnalyticsRepository;
    }

    @GetMapping
    public String list() {
        return "admin/list";
    }

    /** Cap for the top-viewed list on {@code /admin/stats} -- see #stats. */
    private static final int TOP_VIEWED_LIMIT = 20;

    /** Rolling window + list cap for the Part 3 traffic section below. */
    private static final int TRAFFIC_WINDOW_DAYS = 7;
    private static final int TOP_TRAFFIC_LIMIT = 10;

    /**
     * Analytics, Part 1 (2026-07-22, issue #14): surfaces the per-song
     * {@code views} counter {@link com.vinoigitare.service.SongService#recordView}
     * has been incrementing since Phase 4e, never shown anywhere until
     * now. Total across the whole catalog + a top-20 list, computed here
     * (not in the template) for the same restricted-expression-guard
     * reason {@code SongBrowseController#buildArtistTree} already
     * documents. Falls under the existing {@code /admin/**} auth gate
     * automatically -- no new {@code SecurityConfig} entry needed.
     *
     * <p>Analytics, Part 3 (2026-07-22): a second section, sourced from
     * {@link LogAnalyticsRepository}'s day-granularity hit/referrer
     * aggregates ({@code LogAnalyticsAggregator} builds these from the
     * request log) -- total hits, top pages, and top referrers over a
     * trailing 7-day window. UTC day boundaries, matching the aggregator's
     * own date bucketing.
     */
    @GetMapping("/stats")
    public String stats(Model model) {
        List<Song> songs = songService.loadAll();
        long totalViews = songs.stream().mapToLong(Song::views).sum();
        List<Song> topViewed = songs.stream()
                .sorted(Comparator.comparingLong(Song::views).reversed())
                .limit(TOP_VIEWED_LIMIT)
                .toList();
        model.addAttribute("totalSongs", songs.size());
        model.addAttribute("totalViews", totalViews);
        model.addAttribute("topViewed", topViewed);

        LocalDate since = LocalDate.now(ZoneOffset.UTC).minusDays(TRAFFIC_WINDOW_DAYS - 1);
        model.addAttribute("trafficWindowDays", TRAFFIC_WINDOW_DAYS);
        model.addAttribute("totalHits", logAnalyticsRepository.totalHitsSince(since));
        model.addAttribute("topPaths", logAnalyticsRepository.topPathsSince(since, TOP_TRAFFIC_LIMIT));
        model.addAttribute("topReferrers", logAnalyticsRepository.topReferrersSince(since, TOP_TRAFFIC_LIMIT));
        return "admin/stats";
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
