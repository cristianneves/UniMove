package com.unimove.shared.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class CityNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");

    private CityNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String lower = raw.trim().toLowerCase();
        String stripped = DIACRITICS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        String hyphenated = NON_ALPHANUM.matcher(stripped).replaceAll("-");
        return EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
    }
}
