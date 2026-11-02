package io.hibira;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * User tokens: compact HS256 JWTs with the user ID in {@code sub} and an
 * expiry in {@code exp}. The notification inbox takes one signed with the
 * app secret; Hibira Push (like Pusher Beams) takes one signed with the push
 * secret key.
 */
public final class HibiraTokens {

    /** The longest an inbox user token may live. */
    public static final Duration MAX_INBOX_TOKEN_LIFE = Duration.ofDays(30);

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private HibiraTokens() {
    }

    /** Signs a token for {@code userId}, valid until {@code expiresAt}, with {@code secret}. */
    public static String sign(String secret, String userId, Instant expiresAt, String issuer) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("no secret to sign the token with");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId is required");
        }
        String header = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        StringBuilder claims = new StringBuilder("{\"sub\":").append(Json.quote(userId))
                .append(",\"exp\":").append(expiresAt.getEpochSecond());
        if (issuer != null && !issuer.isEmpty()) {
            claims.append(",\"iss\":").append(Json.quote(issuer));
        }
        claims.append('}');
        String input = header + "." + B64.encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        return input + "." + B64.encodeToString(HibiraSignature.hmacBytes(secret, input.getBytes(StandardCharsets.US_ASCII)));
    }
}
