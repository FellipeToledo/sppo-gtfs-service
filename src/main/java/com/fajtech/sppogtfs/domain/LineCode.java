package com.fajtech.sppogtfs.domain;

/**
 * Normalized line code — the shared contract with {@code sppo-tracker-backend}.
 *
 * <p>Normalization (§5.1 of the spec) MUST be identical on both sides, otherwise the
 * GTFS join fails silently. Algorithm:
 * <ol>
 *   <li>{@code trim} + {@code toUpperCase}.</li>
 *   <li>Exact match against {@code viagem_planejada_dia.servico} normalized the same way.</li>
 *   <li>Relaxed fallback: if the code is purely numeric, compare without leading zeros
 *       ({@code 0100 ≡ 100}). Only used when the exact match fails.</li>
 * </ol>
 *
 * <p>This value object carries both the normalized form ({@link #value()}) and the
 * relaxed numeric key ({@link #numericKey()}, {@code null} when not numeric) so callers
 * can implement the two-step resolution without re-deriving the rules.
 */
public record LineCode(String value, String numericKey) {

    public LineCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("line code must not be blank");
        }
    }

    /**
     * Normalize a raw line code (from the GPS feed {@code linha} or from the
     * BigQuery {@code servico} column).
     */
    public static LineCode of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("line code must not be null");
        }
        String normalized = raw.trim().toUpperCase();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("line code must not be blank");
        }
        return new LineCode(normalized, numericKeyOf(normalized));
    }

    /** True when the whole normalized value is composed of ASCII digits. */
    public boolean isNumeric() {
        return numericKey != null;
    }

    private static String numericKeyOf(String normalized) {
        if (normalized.isEmpty()) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        // strip leading zeros; keep at least one digit
        int start = 0;
        while (start < normalized.length() - 1 && normalized.charAt(start) == '0') {
            start++;
        }
        return normalized.substring(start);
    }
}
