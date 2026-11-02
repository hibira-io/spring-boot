package io.hibira;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A verified webhook request.
 *
 * @param timeMs when Hibira sent it, in Unix milliseconds
 * @param events the events
 */
public record Webhook(
        @JsonProperty("time_ms") long timeMs,
        @JsonProperty("events") List<WebhookEvent> events) {

    /** The events, never null. */
    @Override
    public List<WebhookEvent> events() {
        return events == null ? List.of() : events;
    }
}
