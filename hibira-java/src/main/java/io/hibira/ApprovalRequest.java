package io.hibira;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A request for a decision from a person (Hibira approvals).
 *
 * <pre>{@code
 * Approval a = hibira.requestApproval(ApprovalRequest.builder("Refund order #1042 for $89?")
 *     .details("Customer says the parcel never arrived.")
 *     .options("Refund", "Decline")
 *     .build());
 * }</pre>
 *
 * @param title the question, at most 200 characters
 * @param details more context, at most 4000 characters, or null
 * @param options 2 to 4 choices, the first one approves (default Approve / Reject), or null
 * @param expiresInMinutes 1 to 10080 (default 60), or null
 * @param channel a channel that receives an {@code approval} event on request and decision, or null
 * @param pushInterests push interests to notify (needs Hibira Push on the app), or null
 * @param pushUsers push users to notify (not with pushInterests), or null
 * @param agent who asks, at most 60 characters, or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalRequest(
        @JsonProperty("title") String title,
        @JsonProperty("details") String details,
        @JsonProperty("options") List<String> options,
        @JsonProperty("expires_in_minutes") Integer expiresInMinutes,
        @JsonProperty("channel") String channel,
        @JsonProperty("push_interests") List<String> pushInterests,
        @JsonProperty("push_users") List<String> pushUsers,
        @JsonProperty("agent") String agent) {

    /** Starts a request with its question. */
    public static Builder builder(String title) {
        return new Builder(title);
    }

    /** Builds an {@link ApprovalRequest}. */
    public static final class Builder {
        private final String title;
        private String details;
        private List<String> options;
        private Integer expiresInMinutes;
        private String channel;
        private List<String> pushInterests;
        private List<String> pushUsers;
        private String agent;

        private Builder(String title) {
            this.title = title;
        }

        /** More context, at most 4000 characters. */
        public Builder details(String details) {
            this.details = details;
            return this;
        }

        /** 2 to 4 choices; the first one approves. */
        public Builder options(String... options) {
            this.options = List.of(options);
            return this;
        }

        /** How long the person has, 1 to 10080 minutes (default 60). */
        public Builder expiresInMinutes(int minutes) {
            this.expiresInMinutes = minutes;
            return this;
        }

        /** A channel that receives an {@code approval} event, for the @hibira-io/approvals inbox. */
        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        /** Sends the link as a push notification to these interests. */
        public Builder pushInterests(String... interests) {
            this.pushInterests = List.of(interests);
            return this;
        }

        /** Sends the link as a push notification to these users. */
        public Builder pushUsers(String... users) {
            this.pushUsers = List.of(users);
            return this;
        }

        /** Who asks (default "Workflow"), at most 60 characters. */
        public Builder agent(String agent) {
            this.agent = agent;
            return this;
        }

        /** Builds the request. */
        public ApprovalRequest build() {
            return new ApprovalRequest(title, details, options, expiresInMinutes, channel, pushInterests, pushUsers, agent);
        }
    }
}
