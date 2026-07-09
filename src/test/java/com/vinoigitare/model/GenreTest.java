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
}
