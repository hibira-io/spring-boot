package io.hibira;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** The signatures of the Pusher Channels protocol, which Hibira speaks. */
public final class HibiraSignature {

    private static final HexFormat HEX = HexFormat.of();

    private HibiraSignature() {
    }

    /** Lowercase hex HMAC-SHA256 of {@code message}. */
    public static String hmac(String secret, String message) {
        return hmac(secret, message.getBytes(StandardCharsets.UTF_8));
    }

    /** Lowercase hex HMAC-SHA256 of raw bytes. */
    public static String hmac(String secret, byte[] message) {
        return HEX.formatHex(hmacBytes(secret, message));
    }

    static byte[] hmacBytes(String secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** Compares two hex signatures in constant time, ignoring case. */
    public static boolean equal(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                b.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    /** Lowercase hex MD5 of {@code body}. */
    public static String md5(byte[] body) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("MD5").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }

    /**
     * The query string of a signed HTTP API request: {@code parameters} plus
     * auth_key, auth_timestamp, auth_version, body_md5 (when there is a body)
     * and auth_signature.
     */
    public static String signedQuery(String key, String secret, String method, String path,
            Map<String, String> parameters, byte[] body, Instant now) {
        TreeMap<String, String> q = new TreeMap<>();
        if (parameters != null) {
            parameters.forEach((k, v) -> q.put(k.toLowerCase(Locale.ROOT), v));
        }
        q.put("auth_key", key);
        q.put("auth_timestamp", Long.toString(now.getEpochSecond()));
        q.put("auth_version", "1.0");
        if (body != null && body.length > 0) {
            q.put("body_md5", md5(body));
        }
        StringJoiner canonical = new StringJoiner("&");
        StringJoiner encoded = new StringJoiner("&");
        q.forEach((k, v) -> {
            canonical.add(k + "=" + v);
            encoded.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20"));
        });
        String signature = hmac(secret, method.toUpperCase(Locale.ROOT) + "\n" + path + "\n" + canonical);
        return encoded + "&auth_signature=" + signature;
    }
}
