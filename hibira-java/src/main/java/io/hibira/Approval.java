package io.hibira;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * An approval and its answer.
 *
 * @param approvalId the approval ID
 * @param title the question
 * @param options the choices
 * @param status pending, approved, rejected or expired
 * @param url the answer page to share with whoever decides
 * @param choice the chosen option, once answered
 * @param comment the person's comment, if any
 * @param createdAt when it was created
 * @param expiresAt when it expires
 * @param answeredAt when it was answered
 * @param pushError set when the push notification could not be sent
 */
public record Approval(
        @JsonProperty("approval_id") String approvalId,
        @JsonProperty("title") String title,
        @JsonProperty("options") List<String> options,
        @JsonProperty("status") String status,
        @JsonProperty("url") String url,
        @JsonProperty("choice") String choice,
        @JsonProperty("comment") String comment,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("answered_at") Instant answeredAt,
        @JsonProperty("push_error") String pushError) {

    /** Whether nobody has answered yet and it has not expired. */
    @JsonIgnore
    public boolean isPending() {
        return "pending".equals(status);
    }

    /** Whether the first option was chosen. */
    @JsonIgnore
    public boolean isApproved() {
        return "approved".equals(status);
    }
}
