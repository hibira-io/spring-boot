package io.hibira;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Hibira HTTP API and the signatures your server makes for clients.
 * Create one with {@link HibiraClient#create(HibiraOptions)}, or inject it in
 * Spring Boot (hibira-spring-boot-starter). It is thread-safe: keep one per app.
 *
 * <p>Every method that calls the API throws {@link HibiraException} when the
 * request fails.
 */
public interface Hibira {

    /** The app this client works for. */
    HibiraOptions options();

    // ---- Events -------------------------------------------------------------

    /**
     * Sends an event to a channel. {@code data} is sent as is when it is a
     * String and as JSON otherwise.
     */
    default void trigger(String channel, String event, Object data) {
        trigger(List.of(channel), event, data, null);
    }

    /** Sends an event to a channel, leaving out the connection {@code socketId} (usually the sender's). */
    default void trigger(String channel, String event, Object data, String socketId) {
        trigger(List.of(channel), event, data, socketId);
    }

    /** Sends an event to many channels (more than 100 are split into several requests). */
    void trigger(Collection<String> channels, String event, Object data, String socketId);

    /** Sends up to 10 events per request (more are split). */
    void triggerBatch(List<BatchEvent> events);

    /** Sends an event to every connection signed in as {@code userId}. */
    default void sendToUser(String userId, String event, Object data) {
        trigger(HibiraChannels.forUser(userId), event, data);
    }

    // ---- Channels and users -------------------------------------------------

    /** Lists occupied channels, optionally only those starting with {@code prefix} (null for all). */
    Map<String, ChannelInfo> channels(String prefix, String info);

    /** Reads one channel. {@code info} is a comma-separated list such as "user_count,subscription_count", or null. */
    ChannelInfo channel(String channel, String info);

    /** Lists the user IDs on a presence channel. */
    List<String> presenceUsers(String presenceChannel);

    /** Closes every connection signed in as {@code userId}. */
    void terminateUserConnections(String userId);

    // ---- Signatures for clients ---------------------------------------------

    /** Signs a private channel subscription for the connection {@code socketId}. */
    ChannelAuth authorizeChannel(String socketId, String channel);

    /** Signs a presence channel subscription; the member is what other members see. */
    ChannelAuth authorizePresenceChannel(String socketId, String channel, PresenceMember member);

    /**
     * Signs a user in on the connection {@code socketId}, so
     * {@link #sendToUser} and the notification inbox reach it.
     * {@code userData} may add fields such as "user_info" or "watchlist".
     */
    UserAuth authenticateUser(String socketId, String userId, Map<String, ?> userData);

    /** Signs a user in with no extra user data. */
    default UserAuth authenticateUser(String socketId, String userId) {
        return authenticateUser(socketId, userId, null);
    }

    /**
     * Checks a webhook request and parses it. Returns null when the key or
     * signature is wrong. Pass the X-Pusher-Key and X-Pusher-Signature headers
     * (and X-Hibira-Previous-Signature during a secret rotation, or null) and
     * the raw body.
     */
    Webhook verifyWebhook(String key, String signature, String previousSignature, byte[] body);

    // ---- Approvals ----------------------------------------------------------

    /** Asks a person for a decision. Share {@link Approval#url()} with them. */
    Approval requestApproval(ApprovalRequest request);

    /** Reads an approval now. */
    default Approval getApproval(String approvalId) {
        return getApproval(approvalId, null);
    }

    /** Reads an approval, holding the request up to {@code wait} (at most 25 seconds) until the person answers. */
    Approval getApproval(String approvalId, Duration wait);

    /** Waits until the approval is answered or expires, polling with 25-second holds, at most {@code timeout}. */
    default Approval awaitApproval(String approvalId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        Approval a = getApproval(approvalId, null);
        while (a.isPending()) {
            Duration left = Duration.between(Instant.now(), deadline);
            if (left.isNegative() || left.isZero()) {
                return a;
            }
            a = getApproval(approvalId, left.compareTo(Duration.ofSeconds(25)) > 0 ? Duration.ofSeconds(25) : left);
        }
        return a;
    }

    // ---- Notification inbox -------------------------------------------------

    /** Stores a notification in each recipient's inbox, delivers it live and pushes it to users who are offline. */
    InboxSendResult sendInbox(InboxMessage message);

    /**
     * Reads a user's inbox, newest first. {@code status} is null (not
     * archived), "unread", "read", "archived" or "all"; {@code limit} 1 to 100;
     * {@code before} the {@link InboxPage#next()} of the previous page, or null.
     */
    InboxPage listInbox(String userId, String status, int limit, String before);

    /** Marks notifications read or unread ({@code read}) and/or archived ({@code archived}); null leaves one as is. */
    InboxChange markInbox(String userId, List<String> ids, Boolean read, Boolean archived);

    /** Marks all of a user's notifications. */
    InboxChange markAllInbox(String userId, Boolean read, Boolean archived);

    /** Deletes one notification. */
    InboxChange deleteInbox(String userId, String notificationId);

    /** Deletes all of a user's notifications. */
    InboxChange deleteAllInbox(String userId);

    /**
     * A user token for the browser or app of {@code userId}: it reads and
     * marks its own inbox directly (Authorization: Bearer). At most 30 days.
     */
    default String inboxToken(String userId, Duration ttl) {
        HibiraChannels.requireUserId(userId);
        if (ttl.compareTo(HibiraTokens.MAX_INBOX_TOKEN_LIFE) > 0) {
            throw new IllegalArgumentException("inbox tokens live at most 30 days");
        }
        return HibiraTokens.sign(options().secret(), userId, Instant.now().plus(ttl), null);
    }

    // ---- Chat API (https://hibira.io/chat-api) ------------------------------

    /**
     * Any Chat API call, signed. {@code path} is relative to /apps/APP_ID/chat,
     * such as {@code /conversations/c1/typing}; {@code body} is sent as JSON
     * (or null). Returns the JSON answer.
     */
    default JsonNode chat(String method, String path, Map<String, String> query, Object body) {
        throw new UnsupportedOperationException("this Hibira implementation has no Chat API");
    }

    /**
     * Creates or replaces a conversation. {@code fields} takes name, members
     * (user IDs; leave out to keep them), metadata, roles and watchers.
     */
    default JsonNode putConversation(String conversationId, Map<String, ?> fields) {
        return chat("PUT", "/conversations/" + HibiraChannels.pathSegment(conversationId), null, fields == null ? Map.of() : fields);
    }

    default JsonNode getConversation(String conversationId) {
        return chat("GET", "/conversations/" + HibiraChannels.pathSegment(conversationId), null, null);
    }

    /** Deletes a conversation with its members, messages and files. */
    default JsonNode deleteConversation(String conversationId) {
        return chat("DELETE", "/conversations/" + HibiraChannels.pathSegment(conversationId), null, null);
    }

    default JsonNode addMembers(String conversationId, List<String> userIds) {
        return chat("POST", "/conversations/" + HibiraChannels.pathSegment(conversationId) + "/members", null, Map.of("user_ids", userIds));
    }

    default JsonNode removeMember(String conversationId, String userId) {
        return chat("DELETE", "/conversations/" + HibiraChannels.pathSegment(conversationId) + "/members/" + HibiraChannels.pathSegment(userId), null, null);
    }

    /** Posts a message as a member; offline members get a push (Hibira Push). */
    default JsonNode sendMessage(String conversationId, String userId, String text) {
        return sendMessage(conversationId, userId, text, Map.of());
    }

    /**
     * Posts a message as a member. {@code extra} takes name, metadata, push
     * ("offline", "always", "never"), push_title, push_url, visible_to and
     * idempotency_key.
     */
    default JsonNode sendMessage(String conversationId, String userId, String text, Map<String, ?> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", userId);
        if (text != null) {
            body.put("text", text);
        }
        if (extra != null) {
            body.putAll(extra);
        }
        return chat("POST", "/conversations/" + HibiraChannels.pathSegment(conversationId) + "/messages", null, body);
    }

    /** Messages oldest first after a seq (0 for the start), at most {@code limit} (0 for the default). */
    default JsonNode listMessages(String conversationId, long after, int limit) {
        Map<String, String> q = new LinkedHashMap<>();
        if (after > 0) {
            q.put("after", Long.toString(after));
        }
        if (limit > 0) {
            q.put("limit", Integer.toString(limit));
        }
        return chat("GET", "/conversations/" + HibiraChannels.pathSegment(conversationId) + "/messages", q, null);
    }

    default JsonNode markRead(String conversationId, String userId, long seq) {
        return chat("POST", "/conversations/" + HibiraChannels.pathSegment(conversationId) + "/read", null, Map.of("user_id", userId, "seq", seq));
    }

    /** A message with no sender, such as "Alice joined". */
    default JsonNode systemMessage(String conversationId, String text, Map<String, ?> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (metadata != null) {
            body.put("metadata", metadata);
        }
        return chat("POST", "/conversations/" + HibiraChannels.pathSegment(conversationId) + "/system", null, body);
    }

    /** A user's conversations, latest activity first, each with its unread count. */
    default JsonNode userConversations(String userId, int limit, String before) {
        Map<String, String> q = new LinkedHashMap<>();
        if (limit > 0) {
            q.put("limit", Integer.toString(limit));
        }
        if (before != null && !before.isEmpty()) {
            q.put("before", before);
        }
        return chat("GET", "/users/" + HibiraChannels.pathSegment(userId) + "/conversations", q, null);
    }

    // ---- Push notifications (needs the push instance ID and secret key) -----

    /** Publishes to devices subscribed to any of {@code interests} (up to 100). Returns the publish ID. */
    default String publishToInterests(List<String> interests, PushNotification notification) {
        return publishToInterests(interests, notification.toPayload());
    }

    /** Publishes a raw Beams-format body (apns, fcm, web) to interests. Returns the publish ID. */
    String publishToInterests(List<String> interests, Map<String, Object> payload);

    /** Publishes to the devices of {@code users} (up to 1,000). Returns the publish ID. */
    default String publishToUsers(List<String> users, PushNotification notification) {
        return publishToUsers(users, notification.toPayload());
    }

    /** Publishes a raw Beams-format body (apns, fcm, web) to users. Returns the publish ID. */
    String publishToUsers(List<String> users, Map<String, Object> payload);

    /** Removes a user and unlinks all their devices from push. */
    void deletePushUser(String userId);

    /**
     * A push token for the device of {@code userId}, what the push SDKs'
     * TokenProvider fetches from your backend (Pusher Beams format).
     */
    default String pushToken(String userId, Duration ttl) {
        HibiraOptions o = options();
        if (!o.pushConfigured()) {
            throw new IllegalStateException("Hibira Push is not configured: set the push instance ID and secret key");
        }
        return HibiraTokens.sign(o.pushSecretKey(), userId, Instant.now().plus(ttl),
                "https://" + o.pushInstanceId() + ".pushnotifications.pusher.com");
    }
}
