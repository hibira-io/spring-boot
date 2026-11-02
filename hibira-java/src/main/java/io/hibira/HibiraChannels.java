package io.hibira;

/** Channel name rules and helpers. */
public final class HibiraChannels {

    /** Channel names are at most this many characters. */
    public static final int MAX_LENGTH = 164;

    /** The prefix of a signed-in user's own channel. */
    public static final String SERVER_TO_USER_PREFIX = "#server-to-user-";

    private HibiraChannels() {
    }

    /** Whether {@code c} may appear in a channel name: a-z A-Z 0-9 _ - = @ , . ; */
    public static boolean isValidChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '=' || c == '@' || c == ',' || c == '.' || c == ';';
    }

    /** Whether {@code name} is a valid channel name. */
    public static boolean isValid(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
            return false;
        }
        String rest = name.startsWith(SERVER_TO_USER_PREFIX) ? name.substring(SERVER_TO_USER_PREFIX.length()) : name;
        if (rest.isEmpty()) {
            return false;
        }
        for (int i = 0; i < rest.length(); i++) {
            if (!isValidChar(rest.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code userId} can be a signed-in user's ID (it must fit in a channel name). */
    public static boolean isValidUserId(String userId) {
        return userId != null && !userId.isEmpty() && isValid(SERVER_TO_USER_PREFIX + userId);
    }

    /** The channel a signed-in user receives {@link Hibira#sendToUser} events on. */
    public static String forUser(String userId) {
        requireUserId(userId);
        return SERVER_TO_USER_PREFIX + userId;
    }

    /** Whether {@code channel} is a private channel (including private-encrypted-). */
    public static boolean isPrivate(String channel) {
        return channel != null && channel.startsWith("private-");
    }

    /** Whether {@code channel} is a presence channel. */
    public static boolean isPresence(String channel) {
        return channel != null && channel.startsWith("presence-");
    }

    static String requireUserId(String userId) {
        if (!isValidUserId(userId)) {
            throw new IllegalArgumentException("not a valid user ID (use up to 148 of a-z A-Z 0-9 _ - = @ , . ;): " + userId);
        }
        return userId;
    }

    static String requireChannel(String channel) {
        if (!isValid(channel)) {
            throw new IllegalArgumentException("not a valid channel name: " + channel);
        }
        return channel;
    }
}
