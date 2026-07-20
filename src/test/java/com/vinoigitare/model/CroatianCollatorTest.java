package com.vinoigitare.model;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class CroatianCollatorTest {

    @Test
    void stringComparatorOrdersDiacriticLettersByRealAlphabetPosition() {
        List<String> names = new ArrayList<>(List.of(
                "Zana", "Sarajevo", "Đorđe", "Čola", "Cune", "Šaban", "Dino", "Žan"));

        names.sort(CroatianCollator.stringComparator());

        assertThat(names).containsExactly(
                "Cune", "Čola", "Dino", "Đorđe", "Sarajevo", "Šaban", "Zana", "Žan");
    }

    @Test
    void charComparatorOrdersLetterGroupHeadingsByRealAlphabetPosition() {
        List<Character> letters = new ArrayList<>(List.of('Z', 'S', 'Đ', 'Č', 'C', 'Š', 'D', 'Ž'));

        letters.sort(CroatianCollator.charComparator());

        assertThat(letters).containsExactly('C', 'Č', 'D', 'Đ', 'S', 'Š', 'Z', 'Ž');
    }
}
