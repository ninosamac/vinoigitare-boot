package com.vinoigitare.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class SongTest {

    @Test
    void threeArgConstructorReproducesLegacyIdAndAutoDerivesSlug() {
        Song song = new Song("Marko Markovic", "Probna pesma", "chords");

        assertThat(song.id()).isEqualTo("Marko Markovic - Probna pesma");
        assertThat(song.slug()).isEqualTo("marko-markovic--probna-pesma");
        assertThat(song.createdAt()).isNull();
        assertThat(song.views()).isZero();
    }

    @Test
    void slugStripsDiacriticsIncludingDStroke() {
        Song song = new Song("Đorđe Đokić", "Šašava priča", "chords");

        // š/č/ć/ž decompose via NFD; đ/Đ don't (see the slugify() Javadoc) --
        // both cases must end up plain ASCII in the slug.
        assertThat(song.slug()).isEqualTo("dorde-dokic--sasava-prica");
    }

    @Test
    void slugHandlesPunctuationAndRepeatedSeparators() {
        Song song = new Song("Dr. Zoran & Bend!!", "Ne(će) ići...", "chords");

        assertThat(song.slug()).isEqualTo("dr-zoran-bend--ne-ce-ici");
    }

    @Test
    void explicitIdAndSlugAreRespected() {
        Song song = new Song("42", "Marko Markovic", "Probna pesma", "custom-slug", "chords", null, 5L);

        assertThat(song.id()).isEqualTo("42");
        assertThat(song.slug()).isEqualTo("custom-slug");
        assertThat(song.views()).isEqualTo(5L);
    }

    @Test
    void nullArtistTitleOrChordsThrowNullPointerException() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new Song(null, "Title", "chords"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new Song("Artist", null, "chords"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new Song("Artist", "Title", null));
    }
}
