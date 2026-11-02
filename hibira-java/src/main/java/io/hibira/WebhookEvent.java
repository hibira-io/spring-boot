package io.hibira;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One webhook event. In Spring Boot, each one is published as an application
 * event:
 *
 * <pre>{@code
 * @EventListener(condition = "#event.name == 'member_added'")
 * void joined(WebhookEvent event) { ... }
 * }</pre>
 */
public final class WebhookEvent {

    /** A channel got its first subscriber. */
    public static final String CHANNEL_OCCUPIED = "channel_occupied";
    /** A channel lost its last subscriber. */
    public static final String CHANNEL_VACATED = "channel_vacated";
    /** A user joined a presence channel. */
    public static final String MEMBER_ADDED = "member_added";
    /** A user left a presence channel. */
    public static final String MEMBER_REMOVED = "member_removed";
    /** A client sent a client event. */
    public static final String CLIENT_EVENT = "client_event";
    /** A channel's subscription count changed. */
    public static final String SUBSCRIPTION_COUNT = "subscription_count";
    /** A cache channel was subscribed to with nothing cached. */
    public static final String CACHE_MISS = "cache_miss";

    private final String name;
    private final String channel;
    private final String userId;
    private final String event;
    private final String data;
    private final String socketId;
    private final Integer subscriptionCount;
    private final long timeMs;

    /** Creates an event (normally read from a webhook request). */
    @JsonCreator
    public WebhookEvent(
            @JsonProperty("name") String name,
            @JsonProperty("channel") String channel,
            @JsonProperty("user_id") String userId,
            @JsonProperty("event") String event,
            @JsonProperty("data") String data,
            @JsonProperty("socket_id") String socketId,
            @JsonProperty("subscription_count") Integer subscriptionCount,
            @JsonProperty("time_ms") Long timeMs) {
        this.name = name == null ? "" : name;
        this.channel = channel == null ? "" : channel;
        this.userId = userId;
        this.event = event;
        this.data = data;
        this.socketId = socketId;
        this.subscriptionCount = subscriptionCount;
        this.timeMs = timeMs == null ? 0 : timeMs;
    }

    WebhookEvent withTime(long time) {
        return new WebhookEvent(name, channel, userId, event, data, socketId, subscriptionCount, time);
    }

    /** channel_occupied, channel_vacated, member_added, member_removed, client_event, subscription_count or cache_miss. */
    public String getName() {
        return name;
    }

    /** The channel. */
    public String getChannel() {
        return channel;
    }

    /** The user (member events, and client events on presence channels), or null. */
    public String getUserId() {
        return userId;
    }

    /** The client event's name, or null. */
    public String getEvent() {
        return event;
    }

    /** The client event's data (a JSON string), or null. */
    public String getData() {
        return data;
    }

    /** The connection that sent the client event, or null. */
    public String getSocketId() {
        return socketId;
    }

    /** The subscription count (subscription_count events), or null. */
    public Integer getSubscriptionCount() {
        return subscriptionCount;
    }

    /** When the webhook was sent, in Unix milliseconds. */
    public long getTimeMs() {
        return timeMs;
    }

    @Override
    public String toString() {
        return "WebhookEvent{name=" + name + ", channel=" + channel
                + (userId != null ? ", userId=" + userId : "")
                + (event != null ? ", event=" + event : "") + "}";
    }
}
