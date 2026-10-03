package com.friendlyeshop.catalog.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlUtilsTest {

    @Test
    void joinsBaseAndPathNormalizingSlashes() {
        assertThat(UrlUtils.join("https://api.test", "/catalog/images/1"))
                .isEqualTo("https://api.test/catalog/images/1");
        assertThat(UrlUtils.join("https://api.test/", "/catalog/images/1"))
                .isEqualTo("https://api.test/catalog/images/1");
        assertThat(UrlUtils.join("https://api.test/", "catalog/images/1"))
                .isEqualTo("https://api.test/catalog/images/1");
    }
}
