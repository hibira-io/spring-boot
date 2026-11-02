package io.hibira;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * A stored notification in a user's inbox.
 *
 * @param id the notification ID (ntf_...)
 * @param userId the user
 * @param title the title
 * @param body the body, or null
 * @param url where it leads, or null
 * @param icon an image URL, or null
 * @param category your category, or null
 * @param key the idempotency key, or null
 * @param data your own JSON, or null
 * @param createdAt when it was sent
 * @param readAt when it was read, or null
 * @param archivedAt when it was archived, or null
 * @param expiresAt when it is deleted
 */
public record InboxNotification(
        @JsonProperty("id") String id,
        @JsonProperty("user_id") String userId,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("url") String url,
        @JsonProperty("icon") String icon,
        @JsonProperty("category") String category,
        @JsonProperty("key") String key,
        @JsonProperty("data") JsonNode data,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("read_at") Instant readAt,
        @JsonProperty("archived_at") Instant archivedAt,
        @JsonProperty("expires_at") Instant expiresAt) {
}
