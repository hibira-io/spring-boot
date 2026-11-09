package io.hibira;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Waiting rooms: checks the signed pass a visitor brings back from the Hibira
 * queue, and decides what to do with a request, independent of the web
 * framework. The Spring Boot starter wraps this in a servlet filter and a
 * WebFlux filter.
 *
 * <p>A request without a valid pass is redirected to the hosted waiting page
 * (non-GET requests are refused). When it's their turn the page sends the
 * visitor back with {@code ?hibira_pass=...}, which is checked, stored in an
 * HttpOnly cookie ({@code hibira_wr_<room>}) and removed from the URL.
 */
public final class WaitingRoom {

    /** The query parameter the waiting page adds when it sends a visitor back. */
    public static final String PARAM = "hibira_pass";

    /** The request attribute holding the {@link Pass} of an admitted visitor. */
    public static final String ATTRIBUTE = "hibira.pass";

    /** The default hosted waiting page origin. */
    public static final String DEFAULT_HOST = "https://ws.hibira.io";

    private static final byte[] PURPOSE = "hibira-waitroom-pass.".getBytes(StandardCharsets.UTF_8);

    private WaitingRoom() {
    }

    /** The claims of a valid pass. Times are Unix seconds. */
    public record Pass(String appId, String room, long number, String ticketId, long issuedAt, long expiresAt) {
    }

    /** What to do with a request: let it through, redirect it (maybe setting a cookie), or refuse it. */
    public record Decision(Action action, Pass pass, String location, String setCookie) {

        /** Let the request through. */
        public boolean allowed() {
            return action == Action.ALLOW;
        }
    }

    /** The kinds of {@link Decision}. */
    public enum Action { ALLOW, REDIRECT, DENY }

    /** The response body sent with a refusal (status 403). */
    public static final String DENY_BODY = "{\"error\":\"waiting_room\",\"message\":\"join the waiting room first\"}";

    /**
     * Returns the pass's claims, or null when it is missing, forged, expired,
     * or for another room or app. Pass several secrets during a rotation.
     *
     * @param room the room it must be for, or null for any
     * @param appId the app it must be for, or null for any
     */
    public static Pass verifyPass(String token, List<String> secrets, String room, String appId, Instant now) {
        if (token == null || token.length() > 1024) {
            return null;
        }
        int dot = token.indexOf('.');
        if (dot < 1 || token.indexOf('.', dot + 1) >= 0) {
            return null;
        }
        String payload = token.substring(0, dot);
        byte[] sig = decode(token.substring(dot + 1));
        if (sig == null || sig.length != 32) {
            return null;
        }
        byte[] message = concat(PURPOSE, payload.getBytes(StandardCharsets.US_ASCII));
        boolean ok = false;
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()
                    && MessageDigest.isEqual(HibiraSignature.hmacBytes(secret, message), sig)) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            return null;
        }
        JsonNode claims;
        try {
            byte[] raw = decode(payload);
            claims = raw == null ? null : Json.MAPPER.readTree(raw);
        } catch (java.io.IOException e) {
            return null;
        }
        if (claims == null || !claims.isObject() || claims.path("v").asInt() != 1 || !claims.path("exp").isNumber()) {
            return null;
        }
        long exp = claims.path("exp").asLong();
        if (now.getEpochSecond() >= exp) {
            return null;
        }
        String passRoom = claims.path("room").asText("");
        String passApp = claims.path("app").asText("");
        if (room != null && !room.equals(passRoom) || appId != null && !appId.equals(passApp)) {
            return null;
        }
        return new Pass(passApp, passRoom, claims.path("n").asLong(), claims.path("tid").asText(""),
                claims.path("iat").asLong(), exp);
    }

    /** {@link #verifyPass(String, List, String, String, Instant)} with one secret, at the current time. */
    public static Pass verifyPass(String token, String secret, String room) {
        return verifyPass(token, List.of(secret), room, null, Instant.now());
    }

    /** The hosted waiting page address for a room. */
    public static String url(String host, String appKey, String room, String returnUrl, String lang) {
        StringBuilder url = new StringBuilder(stripSlash(host == null ? DEFAULT_HOST : host))
                .append("/wait/").append(pathEscape(appKey)).append('/').append(pathEscape(room));
        StringJoiner query = new StringJoiner("&", "?", "").setEmptyValue("");
        if (returnUrl != null) {
            query.add("return=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8));
        }
        if (lang != null) {
            query.add("lang=" + URLEncoder.encode(lang, StandardCharsets.UTF_8));
        }
        return url.append(query).toString();
    }

    /** Settings for {@link #decide}. */
    public record Options(String appId, String appKey, List<String> secrets, String room, String host, String lang,
            String cookieName) {

        public Options {
            Objects.requireNonNull(appKey, "appKey");
            Objects.requireNonNull(room, "room");
            if (secrets == null || secrets.isEmpty()) {
                throw new IllegalArgumentException("Waiting rooms need the app secret");
            }
            secrets = List.copyOf(secrets);
            if (cookieName == null) {
                cookieName = "hibira_wr_" + room;
            }
        }
    }

    /**
     * Decides what to do with a request.
     *
     * @param url the full request URL, with its query
     * @param cookieValue the value of the room's cookie, or null
     */
    public static Decision decide(String url, String method, String cookieValue, Options o, Instant now) {
        URI uri = URI.create(url);
        String fresh = null;
        List<String> kept = new ArrayList<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String part : rawQuery.split("&")) {
                if (part.startsWith(PARAM + "=")) {
                    fresh = java.net.URLDecoder.decode(part.substring(PARAM.length() + 1), StandardCharsets.UTF_8);
                } else if (!part.isEmpty() && !part.equals(PARAM)) {
                    kept.add(part);
                }
            }
        }
        String base = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        if (base.contains("#")) {
            base = base.substring(0, base.indexOf('#'));
        }
        String clean = kept.isEmpty() ? base : base + "?" + String.join("&", kept);

        if (fresh != null) {
            Pass pass = verifyPass(fresh, o.secrets(), o.room(), o.appId(), now);
            if (pass != null) {
                long maxAge = Math.max(0, pass.expiresAt() - now.getEpochSecond());
                String secure = "https".equalsIgnoreCase(uri.getScheme()) ? "; Secure" : "";
                String cookie = o.cookieName() + "=" + fresh + "; Path=/; Max-Age=" + maxAge + "; HttpOnly; SameSite=Lax"
                        + secure;
                return new Decision(Action.REDIRECT, pass, clean, cookie);
            }
        }
        Pass pass = verifyPass(cookieValue, o.secrets(), o.room(), o.appId(), now);
        if (pass != null) {
            return new Decision(Action.ALLOW, pass, null, null);
        }
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return new Decision(Action.DENY, null, null, null);
        }
        return new Decision(Action.REDIRECT, null, url(o.host(), o.appKey(), o.room(), clean, o.lang()), null);
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String pathEscape(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] decode(String s) {
        if (s.isEmpty() || !s.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
