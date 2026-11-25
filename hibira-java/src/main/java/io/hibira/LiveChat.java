package io.hibira;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hibira Live Chat verified visitors: sign a signed-in user's ID with the
 * workspace's identity secret (Live Chat › Settings › Widget › Verified
 * visitors) and put it on the widget's script tag as {@code data-user-hash},
 * so the user chats as themselves and nobody can pretend to be them.
 */
public final class LiveChat {

    /** The widget script. */
    public static final String SCRIPT_URL = "https://chat.hibira.io/w/v1.js";

    private LiveChat() {
    }

    /** hex(HMAC-SHA256(identity secret, user ID)). */
    public static String userHash(String identitySecret, String userId) {
        if (identitySecret == null || identitySecret.isEmpty()) {
            throw new IllegalArgumentException("the Live Chat identity secret is required");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId is required");
        }
        return HibiraSignature.hmac(identitySecret, userId);
    }

    /**
     * The verified-visitor fields ({@code id, hash, name, email}) for
     * {@code HibiraLiveChat.identify()} or the script tag's data attributes.
     * {@code name} and {@code email} may be null.
     */
    public static Map<String, String> identity(String identitySecret, String userId, String name, String email) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("id", userId);
        out.put("hash", userHash(identitySecret, userId));
        if (name != null && !name.isEmpty()) {
            out.put("name", name);
        }
        if (email != null && !email.isEmpty()) {
            out.put("email", email);
        }
        return out;
    }
}
