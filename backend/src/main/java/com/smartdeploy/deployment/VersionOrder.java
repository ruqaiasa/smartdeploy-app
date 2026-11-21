package com.smartdeploy.deployment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Comparaison de numéros de version par leurs nombres de tête (« 1.1.2-compilation » → 1.1.2). */
public final class VersionOrder {

    private static final Pattern NUMBERS = Pattern.compile("^\\D*(\\d+(?:\\.\\d+)*)");

    private VersionOrder() {
    }

    /** Vrai si {@code candidate} est strictement plus ancienne que {@code reference} ; faux si incomparable. */
    public static boolean isOlder(String candidate, String reference) {
        int[] a = numbers(candidate);
        int[] b = numbers(reference);
        if (a.length == 0 || b.length == 0) {
            return false;
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    static int[] numbers(String version) {
        if (version == null) {
            return new int[0];
        }
        Matcher m = NUMBERS.matcher(version.trim());
        if (!m.find()) {
            return new int[0];
        }
        String[] parts = m.group(1).split("\\.");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException exception) {
                return new int[0];
            }
        }
        return values;
    }
}
