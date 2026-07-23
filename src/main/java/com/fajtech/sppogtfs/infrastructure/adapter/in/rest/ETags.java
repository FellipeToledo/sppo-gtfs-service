package com.fajtech.sppogtfs.infrastructure.adapter.in.rest;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Strong-enough content ETag derived from feedVersion.id + lineCode (+ format/simplify),
 * per §5.4. When the feed changes, the id changes and the ETag changes, so the backend's
 * {@code If-None-Match} revalidation stays correct.
 */
public final class ETags {

    private ETags() {
    }

    public static String forLine(String feedVersionId, String normalizedLine,
                                 OutputFormat format, double simplifyMeters) {
        String seed = feedVersionId + '|' + normalizedLine + '|' + format + '|' + simplifyMeters;
        return quote(hash(seed));
    }

    public static String forShape(String feedVersionId, String shapeId,
                                  OutputFormat format, double simplifyMeters) {
        String seed = feedVersionId + '|' + shapeId + '|' + format + '|' + simplifyMeters;
        return quote(hash(seed));
    }

    private static String hash(String seed) {
        CRC32 crc = new CRC32();
        crc.update(seed.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }

    private static String quote(String value) {
        return '"' + value + '"';
    }

    /** True when the client's If-None-Match header matches the given ETag. */
    public static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String c = candidate.trim();
            if (c.equals("*") || c.equals(etag)) {
                return true;
            }
        }
        return false;
    }
}
