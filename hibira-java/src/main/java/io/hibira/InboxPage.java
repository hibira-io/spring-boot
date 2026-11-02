package io.hibira;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A page of a user's inbox, newest first.
 *
 * @param notifications the notifications
 * @param next pass as {@code before} for the next page, or null on the last page
 * @param unread the user's unread, not archived notifications (the number on the bell)
 */
public record InboxPage(
        @JsonProperty("notifications") List<InboxNotification> notifications,
        @JsonProperty("next") String next,
        @JsonProperty("unread") int unread) {
}
