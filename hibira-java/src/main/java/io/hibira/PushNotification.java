package io.hibira;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A push notification for iOS, Android and browsers, turned into the
 * per-platform payloads of the push API (the Pusher Beams format).
 *
 * <pre>{@code
 * hibira.publishToUsers(List.of("ann"), PushNotification.of("Order shipped", "Arriving Tuesday")
 *     .url("https://example.com/orders/1042"));
 * }</pre>
 */
public final class PushNotification {

    private final String title;
    private final String body;
    private String url;
    private String icon;
    private Map<String, String> data;

    private PushNotification(String title, String body) {
        this.title = title;
        this.body = body;
    }

    /** A notification with a title and a body. */
    public static PushNotification of(String title, String body) {
        return new PushNotification(title, body);
    }

    /** Opens this URL when tapped (web: deep_link; the apps receive it in data as "url"). */
    public PushNotification url(String url) {
        this.url = url;
        return this;
    }

    /** The icon browsers show. */
    public PushNotification icon(String icon) {
        this.icon = icon;
        return this;
    }

    /** Data for your app (string values, as FCM requires). */
    public PushNotification data(Map<String, String> data) {
        this.data = data == null ? null : new LinkedHashMap<>(data);
        return this;
    }

    /** The publish body without the interests or users. */
    public Map<String, Object> toPayload() {
        Map<String, Object> d = new LinkedHashMap<>();
        if (data != null) {
            d.putAll(data);
        }
        if (url != null) {
            d.putIfAbsent("url", url);
        }

        Map<String, Object> webNotification = new LinkedHashMap<>();
        webNotification.put("title", title);
        if (body != null) {
            webNotification.put("body", body);
        }
        if (icon != null) {
            webNotification.put("icon", icon);
        }
        if (url != null) {
            webNotification.put("deep_link", url);
        }
        Map<String, Object> web = new LinkedHashMap<>();
        web.put("notification", webNotification);
        if (!d.isEmpty()) {
            web.put("data", d);
        }

        Map<String, Object> fcmNotification = new LinkedHashMap<>();
        fcmNotification.put("title", title);
        if (body != null) {
            fcmNotification.put("body", body);
        }
        Map<String, Object> fcm = new LinkedHashMap<>();
        fcm.put("notification", fcmNotification);
        if (!d.isEmpty()) {
            fcm.put("data", d);
        }

        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("title", title);
        if (body != null) {
            alert.put("body", body);
        }
        Map<String, Object> apns = new LinkedHashMap<>();
        apns.put("aps", Map.of("alert", alert));
        if (!d.isEmpty()) {
            apns.put("data", d);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("apns", apns);
        payload.put("fcm", fcm);
        payload.put("web", web);
        return payload;
    }
}
