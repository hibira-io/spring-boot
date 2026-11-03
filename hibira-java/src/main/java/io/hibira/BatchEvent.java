package io.hibira;

/**
 * One event of {@link Hibira#triggerBatch}.
 *
 * @param channel the channel
 * @param name the event name
 * @param data the payload: a String is sent as is, anything else as JSON
 * @param socketId a connection to leave out (usually the sender's), or null
 */
public record BatchEvent(String channel, String name, Object data, String socketId) {

    /** An event without a socket to leave out. */
    public BatchEvent(String channel, String name, Object data) {
        this(channel, name, data, null);
    }
}
