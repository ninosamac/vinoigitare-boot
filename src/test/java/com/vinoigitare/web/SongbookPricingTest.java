package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("fast")
class SongbookPricingTest {

    @Test
    void under20PagesIsThreeDollars() {
        assertThat(SongbookPricing.amountCentsFor(1)).isEqualTo(300);
        assertThat(SongbookPricing.amountCentsFor(19)).isEqualTo(300);
    }

    @Test
    void twentyToFortyNinePagesIsFiveDollars() {
        assertThat(SongbookPricing.amountCentsFor(20)).isEqualTo(500);
        assertThat(SongbookPricing.amountCentsFor(49)).isEqualTo(500);
    }

    @Test
    void fiftyToNinetyNinePagesIsSevenDollars() {
        assertThat(SongbookPricing.amountCentsFor(50)).isEqualTo(700);
        assertThat(SongbookPricing.amountCentsFor(99)).isEqualTo(700);
    }

    @Test
    void oneHundredOrMorePagesExceedsTheMax() {
        assertThat(SongbookPricing.exceedsMaxPages(99)).isFalse();
        assertThat(SongbookPricing.exceedsMaxPages(100)).isTrue();
        assertThat(SongbookPricing.exceedsMaxPages(500)).isTrue();
    }
}
