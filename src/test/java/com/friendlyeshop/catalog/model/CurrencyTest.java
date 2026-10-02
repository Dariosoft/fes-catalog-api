package com.friendlyeshop.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CurrencyTest {

    @Test
    void buildsFromKnownCode() {
        assertThat(Currency.from("ARS")).isEqualTo(Currency.ARS);
        assertThat(Currency.from("usd")).isEqualTo(Currency.USD);
    }

    @Test
    void rejectsUnknownCode() {
        assertThatThrownBy(() -> Currency.from("EUR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Currency.from(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
