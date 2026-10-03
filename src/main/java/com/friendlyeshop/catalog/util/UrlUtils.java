package com.friendlyeshop.catalog.util;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static String join(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }
}
