package com.nodezero.server;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Server tuning, under {@code nodezero.*}.
 *
 * @param shards tick threads; 0 means one per available processor
 * @param maxSessions connections beyond this are refused
 * @param sendTimeLimitMs a client whose socket blocks longer than this is disconnected
 * @param sendBufferBytes per-client outgoing buffer before a slow client is disconnected
 * @param allowedOrigins WebSocket origins; tighten for production
 */
@ConfigurationProperties("nodezero")
public record ServerProperties(
        @DefaultValue("0") int shards,
        @DefaultValue("5000") int maxSessions,
        @DefaultValue("1000") int sendTimeLimitMs,
        @DefaultValue("524288") int sendBufferBytes,
        @DefaultValue("*") List<String> allowedOrigins) {

    public int effectiveShards() {
        return shards > 0 ? shards : Runtime.getRuntime().availableProcessors();
    }
}
