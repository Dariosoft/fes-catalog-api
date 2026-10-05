package com.friendlyeshop.catalog.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductStageTest {

    @Test
    void buildsFromLowercaseValue() {
        assertThat(ProductStage.from("draft")).isEqualTo(ProductStage.DRAFT);
        assertThat(ProductStage.from("published")).isEqualTo(ProductStage.PUBLISHED);
    }

    @Test
    void rejectsUnknownValue() {
        assertThatThrownBy(() -> ProductStage.from("archived")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductStage.from(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
