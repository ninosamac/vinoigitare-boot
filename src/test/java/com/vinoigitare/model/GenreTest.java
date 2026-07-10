package com.vinoigitare.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class GenreTest {

    @Test
    void fromSlugFindsMatchingGenre() {
        assertThat(Genre.fromSlug("pop-rock")).contains(Genre.POP_ROCK);
        assertThat(Genre.fromSlug("narodno")).contains(Genre.NARODNO);
        assertThat(Genre.fromSlug("strano")).contains(Genre.STRANO);
    }

    @Test
    void fromSlugIsEmptyForUnknownSlug() {
        assertThat(Genre.fromSlug("not-a-real-genre")).isEmpty();
    }

    @Test
    void slugsAreUrlSafeUnlikeLabels() {
        // "Pop/Rock" contains a literal "/", which can't be a URL path
        // segment -- the slug must not.
        for (Genre genre : Genre.values()) {
            assertThat(genre.slug()).doesNotContain("/");
        }
        assertThat(Genre.POP_ROCK.label()).isEqualTo("Pop/Rock");
    }

    @Test
    void resolveMatchesTheCurrentSlugOrLabel() {
        assertThat(Genre.resolve("strano")).contains(Genre.STRANO);
        assertThat(Genre.resolve("Foreign")).contains(Genre.STRANO);
        assertThat(Genre.resolve("Pop/Rock")).contains(Genre.POP_ROCK);
    }

    @Test
    void resolveAlsoMatchesTheOriginalSerbianLabelTextFromBeforeTheI18nSwitch() {
        // Real bug found in testing: a song saved by SongImporter before
        // the site-wide English i18n switch (see the class Javadoc) still
        // has this literal text in its genre column, and a song edited
        // afterwards got "Foreign" instead -- the two need to resolve to
        // the same Genre, or genre browsing and the admin list silently
        // treat them as different genres.
        assertThat(Genre.resolve("Strano")).contains(Genre.STRANO);
        assertThat(Genre.resolve("Narodno")).contains(Genre.NARODNO);
    }

    @Test
    void resolveIsEmptyForNullBlankOrUnknownValues() {
        assertThat(Genre.resolve(null)).isEmpty();
        assertThat(Genre.resolve("")).isEmpty();
        assertThat(Genre.resolve("not-a-real-genre")).isEmpty();
    }
}
