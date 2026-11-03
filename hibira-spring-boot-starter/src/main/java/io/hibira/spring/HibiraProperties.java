package io.hibira.spring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hibira settings, under {@code hibira.*}. Copy the app ID, key and secret
 * from your app page on hibira.io/portal into environment variables
 * ({@code HIBIRA_APP_ID}, {@code HIBIRA_KEY}, {@code HIBIRA_SECRET}) or any
 * other property source.
 */
@ConfigurationProperties(prefix = "hibira")
public class HibiraProperties {

    /** The app ID. */
    private String appId;

    /** The app key. It is public: browsers connect with it. */
    private String key;

    /** The app secret. Keep it out of source control. */
    private String secret;

    /** The host shown on the app page: ws.hibira.io, or ws-<cluster>.hibira.io for apps in another region. */
    private String host = "ws.hibira.io";

    /** The port; unset means 443 with TLS and 80 without. */
    private Integer port;

    /** Use https. Turn off only for a local server. */
    private boolean useTls = true;

    /** Timeout of HTTP API requests. */
    private Duration timeout = Duration.ofSeconds(30);

    private final Push push = new Push();

    private final Inbox inbox = new Inbox();

    private final Endpoints endpoints = new Endpoints();

    private final Broadcast broadcast = new Broadcast();

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Push getPush() {
        return push;
    }

    public Inbox getInbox() {
        return inbox;
    }

    public Endpoints getEndpoints() {
        return endpoints;
    }

    public Broadcast getBroadcast() {
        return broadcast;
    }

    /** Hibira Push (app page, Push notifications). */
    public static class Push {

        /** The push instance ID. */
        private String instanceId;

        /** The push secret key. */
        private String secretKey;

        /** The push endpoint, when it differs from the app's host. */
        private String endpoint;

        /** How long the push tokens the push token endpoint hands out live. */
        private Duration tokenTtl = Duration.ofDays(1);

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }
    }

    /** The notification inbox. */
    public static class Inbox {

        /** How long the user tokens the inbox token endpoint hands out live (at most 30 days). */
        private Duration tokenTtl = Duration.ofDays(1);

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }
    }

    /** The HTTP endpoints the starter adds to the application. */
    public static class Endpoints {

        /** Add the endpoints (Spring MVC or WebFlux). */
        private boolean enabled = true;

        /** Channel authorization, pusher-js channelAuthorization.endpoint. */
        private String auth = "/hibira/auth";

        /** User sign-in, pusher-js userAuthentication.endpoint. */
        private String userAuth = "/hibira/user-auth";

        /** Webhooks, the URL to enter on the app page. */
        private String webhooks = "/hibira/webhooks";

        /** Inbox user token, the tokenUrl of @hibira-io/inbox. */
        private String inboxToken = "/hibira/inbox/token";

        /** Push token, the TokenProvider URL of the push SDKs. */
        private String pushToken = "/hibira/push/token";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAuth() {
            return auth;
        }

        public void setAuth(String auth) {
            this.auth = auth;
        }

        public String getUserAuth() {
            return userAuth;
        }

        public void setUserAuth(String userAuth) {
            this.userAuth = userAuth;
        }

        public String getWebhooks() {
            return webhooks;
        }

        public void setWebhooks(String webhooks) {
            this.webhooks = webhooks;
        }

        public String getInboxToken() {
            return inboxToken;
        }

        public void setInboxToken(String inboxToken) {
            this.inboxToken = inboxToken;
        }

        public String getPushToken() {
            return pushToken;
        }

        public void setPushToken(String pushToken) {
            this.pushToken = pushToken;
        }
    }

    /** {@code @HibiraBroadcast} application events. */
    public static class Broadcast {

        /** Send application events marked with @HibiraBroadcast (or implementing HibiraBroadcastable) to Hibira. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
