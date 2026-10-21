package com.god.ApplicationManager.Helper;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Search text normalisation, kept in a pure class (no Android types) so it is
 * unit-testable on the JVM: lowercases with Locale.ROOT and strips diacritics,
 * so typing "dong bang" finds "Đóng băng".
 */
public final class SearchNormalizer {
    private SearchNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) return "";
        // NFD does not decompose đ/Đ (the stroke is not a combining mark),
        // so map them explicitly before stripping the combining marks.
        String mapped = text.replace('đ', 'd').replace('Đ', 'D');
        String normalized = Normalizer.normalize(mapped, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{Mn}+", "").toLowerCase(Locale.ROOT);
    }
}
