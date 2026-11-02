package io.hibira.spring;

import java.util.List;

/**
 * An application event that says itself where it goes, for when an
 * annotation is not enough. Published with
 * {@link org.springframework.context.ApplicationEventPublisher}, it is sent
 * to Hibira (after the commit, inside a transaction).
 */
public interface HibiraBroadcastable {

    /** The channels. */
    List<String> broadcastChannels();

    /** The event name; by default the class's simple name. */
    default String broadcastEvent() {
        return getClass().getSimpleName();
    }

    /** The data; by default the event itself, as JSON. */
    default Object broadcastData() {
        return this;
    }

    /** A connection to leave out, usually the one of the user who made the change; null for none. */
    default String broadcastExceptSocketId() {
        return null;
    }
}
