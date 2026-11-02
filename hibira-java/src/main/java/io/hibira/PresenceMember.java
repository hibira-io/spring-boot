package io.hibira;

/**
 * A presence channel member: the user ID and what other members see.
 *
 * @param userId the user ID
 * @param userInfo anything serializable to JSON, or null
 */
public record PresenceMember(String userId, Object userInfo) {

    /** A member without user info. */
    public static PresenceMember of(String userId) {
        return new PresenceMember(userId, null);
    }

    /** A member with user info. */
    public static PresenceMember of(String userId, Object userInfo) {
        return new PresenceMember(userId, userInfo);
    }
}
