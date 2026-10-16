package com.nodezero.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Guards the build setup itself: tests must run, and they must run under tr_TR. */
class BuildSanityTest {

    @Test
    void testsRunUnderTurkishLocale() {
        assertThat(Locale.getDefault().getLanguage()).isEqualTo("tr");
    }

    @Test
    void defaultLocaleBreaksAsciiUpperCase() {
        // Why jdk-unsafe bans toUpperCase(): under tr_TR, 'i' becomes dotted 'İ'.
        assertThat("id".toUpperCase(Locale.getDefault())).isEqualTo("İD");
    }

    @Test
    void rootLocaleIsSafeForIdentifiers() {
        assertThat("id".toUpperCase(Locale.ROOT)).isEqualTo("ID");
    }
}
