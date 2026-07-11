package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class AlphabeticalIndexTest {

    @Test
    void bucketsByUppercasedFirstCharacter() {
        assertThat(AlphabeticalIndex.firstLetter("Marko Markovic")).isEqualTo('M');
        assertThat(AlphabeticalIndex.firstLetter("ana anic")).isEqualTo('A');
    }

    @Test
    void bucketsDiacriticLettersAsThemselves() {
        // The known, accepted simplification (see the class Javadoc):
        // single diacritic letters bucket as themselves, not folded onto
        // their plain-ASCII look-alike.
        assertThat(AlphabeticalIndex.firstLetter("Đorđe Đokić")).isEqualTo('Đ');
        assertThat(AlphabeticalIndex.firstLetter("Šaban Šaulić")).isEqualTo('Š');
    }

    @Test
    void doesNotGroupSerbianDigraphsAsOneLetter() {
        // The other half of the same known limitation: "Lj"/"Nj"/"Dž" are
        // NOT single buckets here -- "Ljubomir" lands under "L".
        assertThat(AlphabeticalIndex.firstLetter("Ljubomir")).isEqualTo('L');
    }

    @Test
    void emptyStringBucketsUnderHash() {
        assertThat(AlphabeticalIndex.firstLetter("")).isEqualTo('#');
    }
}
