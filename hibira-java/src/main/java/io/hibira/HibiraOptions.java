package io.hibira;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Credentials and host of a Hibira app. Copy the app ID, key and secret from
 * your app page on <a href="https://hibira.io/portal">hibira.io/portal</a>.
 *
 * <pre>{@code
 * HibiraOptions options = HibiraOptions.builder()
 *     .appId(System.getenv("HIBIRA_APP_ID"))
 *     .key(System.getenv("HIBIRA_KEY"))
 *     .secret(System.getenv("HIBIRA_SECRET"))
 *     .build();
 * }</pre>
 */
public final class HibiraOptions {

    /** The host of apps in the default cluster. */
    public static final String DEFAULT_HOST = "ws.hibira.io";

    private final String appId;
    private final String key;
    private final String secret;
    private final String host;
    private final Integer port;
    private final boolean useTls;
    private final Duration timeout;
    private final String pushInstanceId;
    private final String pushSecretKey;
    private final String pushEndpoint;

    private HibiraOptions(Builder b) {
        this.appId = trim(b.appId);
        this.key = trim(b.key);
        this.secret = b.secret == null ? "" : b.secret;
        this.host = b.host == null || b.host.isBlank() ? DEFAULT_HOST : b.host.trim();
        this.port = b.port;
        this.useTls = b.useTls;
        this.timeout = b.timeout == null ? Duration.ofSeconds(30) : b.timeout;
        this.pushInstanceId = trim(b.pushInstanceId);
        this.pushSecretKey = b.pushSecretKey == null ? "" : b.pushSecretKey;
        this.pushEndpoint = trim(b.pushEndpoint);
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** The app ID. */
    public String appId() {
        return appId;
    }

    /** The app key. It is public: browsers connect with it. */
    public String key() {
        return key;
    }

    /** The app secret. Keep it on the server. */
    public String secret() {
        return secret;
    }

    /** The host shown on the app page: {@code ws.hibira.io}, or {@code ws-<cluster>.hibira.io}. */
    public String host() {
        return host;
    }

    /** The port in use: the one set, or 443 with TLS and 80 without. */
    public int port() {
        return port != null ? port : (useTls ? 443 : 80);
    }

    /** Whether requests use https. */
    public boolean useTls() {
        return useTls;
    }

    /** Timeout of HTTP API requests. */
    public Duration timeout() {
        return timeout;
    }

    /** The Hibira Push instance ID (app page, Push notifications), or empty. */
    public String pushInstanceId() {
        return pushInstanceId;
    }

    /** The Hibira Push secret key, or empty. */
    public String pushSecretKey() {
        return pushSecretKey;
    }

    /** Whether push publishing is configured (instance ID and secret key). */
    public boolean pushConfigured() {
        return !pushInstanceId.isEmpty() && !pushSecretKey.isEmpty();
    }

    /** The base URL of the HTTP API, for example {@code https://ws.hibira.io}. */
    public URI baseUri() {
        String scheme = useTls ? "https" : "http";
        int defaultPort = useTls ? 443 : 80;
        return URI.create(port() == defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port());
    }

    /** The base URL of the push API: the one set, or the HTTP API's. */
    public URI pushBaseUri() {
        if (!pushEndpoint.isEmpty()) {
            String e = pushEndpoint.endsWith("/") ? pushEndpoint.substring(0, pushEndpoint.length() - 1) : pushEndpoint;
            return URI.create(e.contains("://") ? e : "https://" + e);
        }
        return baseUri();
    }

    /** Throws when the app ID, key or secret is missing. */
    public HibiraOptions validate() {
        if (appId.isEmpty() || key.isEmpty() || secret.isEmpty()) {
            throw new IllegalStateException("Hibira: the app ID, key and secret are required. Copy them from your app page "
                    + "on https://hibira.io/portal (for example HIBIRA_APP_ID, HIBIRA_KEY and HIBIRA_SECRET).");
        }
        return this;
    }

    @Override
    public String toString() {
        return "HibiraOptions{appId=" + appId + ", key=" + key + ", host=" + host + ", port=" + port() + ", useTls=" + useTls + "}";
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** Builds {@link HibiraOptions}. */
    public static final class Builder {
        private String appId;
        private String key;
        private String secret;
        private String host;
        private Integer port;
        private boolean useTls = true;
        private Duration timeout;
        private String pushInstanceId;
        private String pushSecretKey;
        private String pushEndpoint;

        private Builder() {
        }

        /** The app ID. */
        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        /** The app key. */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /** The app secret. */
        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        /** The host shown on the app page (default {@code ws.hibira.io}). */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /** The port; null means 443 with TLS and 80 without. */
        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        /** Use https (default). Turn off only for a local server. */
        public Builder useTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        /** Timeout of HTTP API requests (default 30 seconds). */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /** The Hibira Push instance ID, for push notifications. */
        public Builder pushInstanceId(String pushInstanceId) {
            this.pushInstanceId = pushInstanceId;
            return this;
        }

        /** The Hibira Push secret key, for push notifications. */
        public Builder pushSecretKey(String pushSecretKey) {
            this.pushSecretKey = pushSecretKey;
            return this;
        }

        /** The push endpoint, when it differs from the app's host. */
        public Builder pushEndpoint(String pushEndpoint) {
            this.pushEndpoint = pushEndpoint;
            return this;
        }

        /** Builds the options without checking them. */
        public HibiraOptions build() {
            return new HibiraOptions(this);
        }
    }
}
