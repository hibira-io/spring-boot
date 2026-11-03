package io.hibira;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A notification to send to one or more users' inboxes.
 *
 * <pre>{@code
 * hibira.sendInbox(InboxMessage.builder("Order #1042 shipped")
 *     .to("ann", "bob")
 *     .body("It should arrive on Tuesday.")
 *     .url("/orders/1042")
 *     .key("order-1042-shipped")
 *     .build());
 * }</pre>
 *
 * @param users 1 to 1,000 user IDs
 * @param title 1 to 200 characters
 * @param body up to 2,000 characters, or null
 * @param url an http(s) URL or a path starting with /, or null
 * @param icon an http(s) image URL, or null
 * @param category up to 64 of A-Z a-z 0-9 _ - . :, or null
 * @param key idempotency key: a user who already has a notification with it does not get it again, or null
 * @param data your own JSON-serializable object (at most 4,096 bytes of JSON), or null
 * @param push "offline" (default), "always" or "never", or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboxMessage(
        @JsonProperty("users") List<String> users,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("url") String url,
        @JsonProperty("icon") String icon,
        @JsonProperty("category") String category,
        @JsonProperty("key") String key,
        @JsonProperty("data") Object data,
        @JsonProperty("push") String push) {

    /** Push to the users with no open connection (the default). */
    public static final String PUSH_OFFLINE = "offline";
    /** Push to every user. */
    public static final String PUSH_ALWAYS = "always";
    /** Never push. */
    public static final String PUSH_NEVER = "never";

    /** Starts a notification with its title. */
    public static Builder builder(String title) {
        return new Builder(title);
    }

    /** Builds an {@link InboxMessage}. */
    public static final class Builder {
        private final String title;
        private List<String> users = List.of();
        private String body;
        private String url;
        private String icon;
        private String category;
        private String key;
        private Object data;
        private String push;

        private Builder(String title) {
            this.title = title;
        }

        /** The recipients. */
        public Builder to(String... users) {
            this.users = List.of(users);
            return this;
        }

        /** The recipients. */
        public Builder to(List<String> users) {
            this.users = List.copyOf(users);
            return this;
        }

        /** The body, up to 2,000 characters. */
        public Builder body(String body) {
            this.body = body;
            return this;
        }

        /** Where the notification leads (a path is not used for push, which needs a full URL). */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /** An image URL. */
        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        /** A category for your own filtering and icons. */
        public Builder category(String category) {
            this.category = category;
            return this;
        }

        /** An idempotency key, so a retried send is safe. */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /** Your own data, kept as is. */
        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        /** {@link #PUSH_OFFLINE} (default), {@link #PUSH_ALWAYS} or {@link #PUSH_NEVER}. */
        public Builder push(String push) {
            this.push = push;
            return this;
        }

        /** Builds the notification. */
        public InboxMessage build() {
            return new InboxMessage(users, title, body, url, icon, category, key, data, push);
        }
    }
}
