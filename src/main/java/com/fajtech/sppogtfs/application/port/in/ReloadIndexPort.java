package com.fajtech.sppogtfs.application.port.in;

/**
 * Inbound port to trigger an atomic rebuild of the in-memory index
 * (admin endpoint / scheduled reload).
 */
public interface ReloadIndexPort {

    /**
     * Rebuild the index from the source DB and atomically swap it in.
     *
     * @return a summary of the freshly built index.
     */
    ReloadResult reload();

    record ReloadResult(String feedVersionId, long routesIndexed, long shapesIndexed,
                        double durationSeconds) {
    }
}
