package io.hibira;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The answer to {@link Hibira#sendInbox}.
 *
 * @param notifications one entry per recipient
 * @param push how many users were pushed, and why not when it failed
 */
public record InboxSendResult(
        @JsonProperty("notifications") List<Sent> notifications,
        @JsonProperty("push") Push push) {

    /**
     * One recipient's notification.
     *
     * @param userId the user
     * @param id the notification ID
     * @param isNew false when the user already had a notification with the same key
     */
    public record Sent(
            @JsonProperty("user_id") String userId,
            @JsonProperty("id") String id,
            @JsonProperty("new") boolean isNew) {
    }

    /**
     * The push part of a send.
     *
     * @param users how many users were pushed
     * @param error why the push could not be sent, or null
     */
    public record Push(
            @JsonProperty("users") int users,
            @JsonProperty("error") String error) {
    }
}
