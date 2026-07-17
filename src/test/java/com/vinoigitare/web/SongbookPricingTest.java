package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class SongbookPricingTest {

    @Test
    void under100SongsIsFiveDollars() {
        assertThat(SongbookPricing.amountCentsFor(1)).isEqualTo(500);
        assertThat(SongbookPricing.amountCentsFor(99)).isEqualTo(500);
    }

    @Test
    void oneHundredToTwoHundredNinetyNineSongsIsTenDollars() {
        assertThat(SongbookPricing.amountCentsFor(100)).isEqualTo(1000);
        assertThat(SongbookPricing.amountCentsFor(299)).isEqualTo(1000);
    }

    @Test
    void threeHundredOrMoreSongsIsFifteenDollars() {
        assertThat(SongbookPricing.amountCentsFor(300)).isEqualTo(1500);
        assertThat(SongbookPricing.amountCentsFor(1000)).isEqualTo(1500);
    }
}
