package io.hibira.spring;

import io.hibira.LiveChat;
import java.util.Map;

/**
 * Hibira Live Chat on your pages. Inject it and put {@link #scriptTag} in
 * your layout (in Thymeleaf: {@code th:utext}), passing the signed-in user,
 * or null for visitors. With {@code hibira.live-chat.identity-secret} set,
 * signed-in users chat as themselves: their ID is signed, so the inbox shows
 * their name and email and nobody can pretend to be them.
 */
public class HibiraLiveChat {

    private final String workspace;
    private final String identitySecret;

    public HibiraLiveChat(String workspace, String identitySecret) {
        this.workspace = blank(workspace) ? null : workspace;
        this.identitySecret = blank(identitySecret) ? null : identitySecret;
    }

    /** The workspace ID, or null when {@code hibira.live-chat.workspace} is not set. */
    public String workspace() {
        return workspace;
    }

    /**
     * The verified-visitor fields ({@code id, hash, name, email}) for
     * {@code HibiraLiveChat.identify()} in an SPA, or null without a user ID
     * or an identity secret.
     */
    public Map<String, String> identity(String userId, String name, String email) {
        if (identitySecret == null || blank(userId)) {
            return null;
        }
        return LiveChat.identity(identitySecret, userId, name, email);
    }

    /** The widget's script tag for a visitor who is not signed in; empty without a workspace. */
    public String scriptTag() {
        return scriptTag(null, null, null);
    }

    /** The widget's script tag, with the signed-in user when there is one; empty without a workspace. */
    public String scriptTag(String userId, String name, String email) {
        if (workspace == null) {
            return "";
        }
        StringBuilder html = new StringBuilder("<script src=\"").append(LiveChat.SCRIPT_URL).append('"');
        attr(html, "data-workspace", workspace);
        Map<String, String> who = identity(userId, name, email);
        if (who != null) {
            attr(html, "data-user-id", who.get("id"));
            attr(html, "data-user-hash", who.get("hash"));
            attr(html, "data-user-name", who.get("name"));
            attr(html, "data-user-email", who.get("email"));
        }
        return html.append(" async></script>").toString();
    }

    private static void attr(StringBuilder html, String name, String value) {
        if (value == null) {
            return;
        }
        html.append(' ').append(name).append("=\"").append(escape(value)).append('"');
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
