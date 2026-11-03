package io.hibira;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The answer to marking or deleting inbox notifications.
 *
 * @param ids the notifications that changed or were deleted
 * @param unread the user's unread count afterwards
 */
public record InboxChange(
        @JsonProperty("changed") @JsonAlias("deleted") List<String> ids,
        @JsonProperty("unread") int unread) {
}
