package com.vinoigitare.web;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.vinoigitare.model.Genre;
import com.vinoigitare.model.Song;
import com.vinoigitare.service.SongService;

/**
 * Genre categories + A-Z artist index (Phase 4c), mirroring pesmarica.rs's
 * {@code /Akordi/{letter}/{genre}} browsing pattern: pick a genre, pick a
 * letter, see that letter's artists (with their song count within that
 * genre) in that genre.
 *
 * <p>Letter grouping is via {@link AlphabeticalIndex#firstLetter} -- see
 * its Javadoc for the exact rule and its known limitation.
 */
@Controller
@RequestMapping("/genres")
public class GenreBrowseController {

    private final SongService songService;

    public GenreBrowseController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping
    public String genres(Model model) {
        Map<Genre, Integer> songCountByGenre = new java.util.LinkedHashMap<>();
        for (Genre genre : Genre.values()) {
            songCountByGenre.put(genre, songService.loadByGenre(genre).size());
        }
        model.addAttribute("songCountByGenre", songCountByGenre);
        return "genres";
    }

    @GetMapping("/{genreSlug}")
    public String letters(@PathVariable String genreSlug, Model model) {
        Genre genre = genreFor(genreSlug);
        List<Song> songs = songService.loadByGenre(genre);

        TreeSet<Character> letters = new TreeSet<>();
        for (Song song : songs) {
            letters.add(AlphabeticalIndex.firstLetter(song.artist()));
        }

        model.addAttribute("genre", genre);
        model.addAttribute("letters", letters);
        model.addAttribute("songCount", songs.size());
        return "genre-letters";
    }

    @GetMapping("/{genreSlug}/{letter}")
    public String artistsForLetter(@PathVariable String genreSlug, @PathVariable String letter, Model model) {
        Genre genre = genreFor(genreSlug);
        char letterChar = AlphabeticalIndex.firstLetter(letter);

        Map<String, Long> songCountByArtist = new TreeMap<>();
        for (Song song : songService.loadByGenre(genre)) {
            if (AlphabeticalIndex.firstLetter(song.artist()) == letterChar) {
                songCountByArtist.merge(song.artist(), 1L, Long::sum);
            }
        }

        model.addAttribute("genre", genre);
        model.addAttribute("letter", String.valueOf(letterChar));
        model.addAttribute("songCountByArtist", songCountByArtist);
        return "genre-artists";
    }

    private static Genre genreFor(String genreSlug) {
        return Genre.fromSlug(genreSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown genre: " + genreSlug));
    }
}
