package io.hibira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HibiraClientTest {

    // The example credentials of the Pusher Channels protocol documentation.
    static final String KEY = "278d425bdf160c739803";
    static final String SECRET = "7ad3773142a6692b25b8";

    static final ObjectMapper MAPPER = new ObjectMapper();

    record Req(String method, String path, Map<String, String> query, String body, Map<String, String> headers) {
        JsonNode json() throws IOException {
            return MAPPER.readTree(body);
        }
    }

    HttpServer server;
    final List<Req> requests = new CopyOnWriteArrayList<>();
    volatile Function<Req, Object[]> answer = r -> new Object[] {200, "{}"};

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        Map<String, String> q = new TreeMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String kv : raw.split("&")) {
                int i = kv.indexOf('=');
                q.put(kv.substring(0, i), java.net.URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
        Req r = new Req(ex.getRequestMethod(), ex.getRequestURI().getRawPath(), q,
                new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), headers);
        requests.add(r);
        Object[] a = answer.apply(r);
        byte[] out = ((String) a[1]).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders((Integer) a[0], out.length == 0 ? -1 : out.length);
        if (out.length > 0) {
            ex.getResponseBody().write(out);
        }
        ex.close();
    }

    HibiraClient client() {
        return client(HibiraOptions.builder());
    }

    HibiraClient client(HibiraOptions.Builder b) {
        HibiraOptions o = b.appId("3").key(KEY).secret(SECRET)
                .host("127.0.0.1").port(server.getAddress().getPort()).useTls(false).build();
        return HibiraClient.create(o, null, null, Clock.fixed(Instant.ofEpochSecond(1353088179), ZoneOffset.UTC));
    }

    @Test
    void signsLikeThePusherDocumentationExample() {
        String body = "{\"name\":\"foo\",\"channels\":[\"project-3\"],\"data\":\"{\\\"some\\\":\\\"data\\\"}\"}";
        String q = HibiraSignature.signedQuery(KEY, SECRET, "POST", "/apps/3/events", null,
                body.getBytes(StandardCharsets.UTF_8), Instant.ofEpochSecond(1353088179));
        assertThat(q).isEqualTo("auth_key=278d425bdf160c739803&auth_timestamp=1353088179&auth_version=1.0"
                + "&body_md5=ec365a775a4cd0599faeb73354201b6f"
                + "&auth_signature=da454824c97ba181a32ccc17a72625ba02771f50b50e1e7430e47a1f3f457e6c");
    }

    @Test
    void authorizesAPrivateChannelLikeThePusherDocumentationExample() {
        ChannelAuth auth = client().authorizeChannel("1234.1234", "private-foobar");
        assertThat(auth.auth()).isEqualTo("278d425bdf160c739803:58df8b0c36d6982b82c3ecf6b4662e34fe8c25bba48f5369f135bf843651c3a4");
        assertThat(auth.channelData()).isNull();
    }

    @Test
    void authorizesAPresenceChannel() {
        ChannelAuth auth = client().authorizePresenceChannel("1234.1234", "presence-room", PresenceMember.of("ann", Map.of("name", "Ann")));
        assertThat(auth.channelData()).isEqualTo("{\"user_id\":\"ann\",\"user_info\":{\"name\":\"Ann\"}}");
        assertThat(auth.auth()).isEqualTo(KEY + ":" + HibiraSignature.hmac(SECRET, "1234.1234:presence-room:" + auth.channelData()));
    }

    @Test
    void signsAUserIn() {
        UserAuth auth = client().authenticateUser("1234.1234", "ann", Map.of("user_info", Map.of("name", "Ann")));
        assertThat(auth.userData()).isEqualTo("{\"id\":\"ann\",\"user_info\":{\"name\":\"Ann\"}}");
        assertThat(auth.auth()).isEqualTo(KEY + ":" + HibiraSignature.hmac(SECRET, "1234.1234::user::" + auth.userData()));
    }

    @Test
    void refusesBadSocketIdsChannelsAndUsers() {
        HibiraClient c = client();
        assertThatThrownBy(() -> c.authorizeChannel("abc", "private-a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.authorizeChannel("1.2", "private a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.authenticateUser("1.2", "ann smith")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.trigger("bad channel", "e", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void triggersASignedEvent() throws IOException {
        client().trigger("orders", "order.shipped", Map.of("id", 1042), "1.2");
        Req r = requests.get(0);
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.path()).isEqualTo("/apps/3/events");
        assertThat(r.headers().get("content-type")).isEqualTo("application/json");
        assertThat(r.headers().get("user-agent")).startsWith("hibira-java/");
        JsonNode b = r.json();
        assertThat(b.get("name").asText()).isEqualTo("order.shipped");
        assertThat(b.get("data").asText()).isEqualTo("{\"id\":1042}");
        assertThat(b.get("channels").get(0).asText()).isEqualTo("orders");
        assertThat(b.get("socket_id").asText()).isEqualTo("1.2");
        assertSigned(r);
    }

    @Test
    void sendsStringsAsIs() throws IOException {
        client().trigger("orders", "e", "plain text");
        assertThat(requests.get(0).json().get("data").asText()).isEqualTo("plain text");
    }

    @Test
    void splitsManyChannelsAndBatches() throws IOException {
        List<String> channels = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            channels.add("c" + i);
        }
        client().trigger(channels, "e", 1, null);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).json().get("channels").size()).isEqualTo(100);
        assertThat(requests.get(1).json().get("channels").size()).isEqualTo(50);

        requests.clear();
        List<BatchEvent> events = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            events.add(new BatchEvent("c" + i, "e", i));
        }
        client().triggerBatch(events);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).path()).isEqualTo("/apps/3/batch_events");
        assertThat(requests.get(0).json().get("batch").size()).isEqualTo(10);
        assertThat(requests.get(1).json().get("batch").get(1).get("data").asText()).isEqualTo("11");
    }

    @Test
    void sendsToAUser() throws IOException {
        client().sendToUser("ann", "hello", "hi");
        assertThat(requests.get(0).json().get("channels").get(0).asText()).isEqualTo("#server-to-user-ann");
    }

    @Test
    void readsChannelsAndUsers() {
        answer = r -> switch (r.path()) {
            case "/apps/3/channels" -> new Object[] {200, "{\"channels\":{\"presence-a\":{\"user_count\":2},\"b\":{}}}"};
            case "/apps/3/channels/presence-a" -> new Object[] {200, "{\"occupied\":true,\"subscription_count\":3}"};
            case "/apps/3/channels/presence-a/users" -> new Object[] {200, "{\"users\":[{\"id\":\"ann\"},{\"id\":\"bob\"}]}"};
            default -> new Object[] {404, "{\"error\":\"nope\"}"};
        };
        HibiraClient c = client();
        Map<String, ChannelInfo> all = c.channels("presence-", "user_count");
        assertThat(all).containsKeys("presence-a", "b");
        assertThat(all.get("presence-a").userCount()).isEqualTo(2);
        assertThat(requests.get(0).query()).containsEntry("filter_by_prefix", "presence-").containsEntry("info", "user_count");
        assertSigned(requests.get(0));
        assertThat(c.channel("presence-a", "subscription_count").subscriptionCount()).isEqualTo(3);
        assertThat(c.presenceUsers("presence-a")).containsExactly("ann", "bob");
    }

    @Test
    void terminatesAUsersConnections() {
        client().terminateUserConnections("ann");
        assertThat(requests.get(0).path()).isEqualTo("/apps/3/users/ann/terminate_connections");
        assertThat(requests.get(0).body()).isEqualTo("{}");
    }

    @Test
    void reportsApiErrors() {
        answer = r -> new Object[] {413, "{\"error\":\"Event data too large\"}"};
        assertThatThrownBy(() -> client().trigger("a", "e", "x"))
                .isInstanceOf(HibiraException.class)
                .hasMessageContaining("413")
                .hasMessageContaining("Event data too large")
                .satisfies(e -> assertThat(((HibiraException) e).status()).isEqualTo(413));
    }

    @Test
    void reportsNetworkErrors() {
        HibiraOptions o = HibiraOptions.builder().appId("3").key(KEY).secret(SECRET).host("127.0.0.1").port(1).useTls(false)
                .timeout(Duration.ofSeconds(2)).build();
        assertThatThrownBy(() -> HibiraClient.create(o).trigger("a", "e", "x"))
                .isInstanceOf(HibiraException.class)
                .satisfies(e -> assertThat(((HibiraException) e).status()).isZero());
    }

    @Test
    void verifiesWebhooks() {
        HibiraClient c = client();
        byte[] body = "{\"time_ms\":1700000000000,\"events\":[{\"name\":\"member_added\",\"channel\":\"presence-a\",\"user_id\":\"ann\"}]}"
                .getBytes(StandardCharsets.UTF_8);
        String sig = HibiraSignature.hmac(SECRET, body);
        Webhook w = c.verifyWebhook(KEY, sig, null, body);
        assertThat(w).isNotNull();
        assertThat(w.events()).hasSize(1);
        assertThat(w.events().get(0).getName()).isEqualTo(WebhookEvent.MEMBER_ADDED);
        assertThat(w.events().get(0).getUserId()).isEqualTo("ann");
        assertThat(w.events().get(0).getTimeMs()).isEqualTo(1700000000000L);
        assertThat(c.verifyWebhook(KEY, "bad", sig.toUpperCase(), body)).isNotNull();
        assertThat(c.verifyWebhook(KEY, "bad", null, body)).isNull();
        assertThat(c.verifyWebhook("other", sig, null, body)).isNull();
        assertThat(c.verifyWebhook(KEY, sig, null, "{}".getBytes(StandardCharsets.UTF_8))).isNull();
    }

    @Test
    void requestsAndReadsApprovals() throws IOException {
        answer = r -> r.method().equals("POST")
                ? new Object[] {201, "{\"approval_id\":\"apr_1\",\"title\":\"Refund?\",\"options\":[\"Refund\",\"Decline\"],"
                        + "\"status\":\"pending\",\"url\":\"https://hibira.io/approve/apr_1/x\",\"created_at\":\"2026-11-02T10:00:00Z\"}"}
                : new Object[] {200, "{\"approval_id\":\"apr_1\",\"status\":\"approved\",\"choice\":\"Refund\",\"answered_at\":\"2026-11-02T10:01:00Z\"}"};
        HibiraClient c = client();
        Approval a = c.requestApproval(ApprovalRequest.builder("Refund?").options("Refund", "Decline").expiresInMinutes(30).build());
        assertThat(a.isPending()).isTrue();
        assertThat(a.url()).startsWith("https://hibira.io/approve/");
        assertThat(a.createdAt()).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));
        JsonNode sent = requests.get(0).json();
        assertThat(sent.get("title").asText()).isEqualTo("Refund?");
        assertThat(sent.get("expires_in_minutes").asInt()).isEqualTo(30);
        assertThat(sent.has("details")).as("unset fields are left out (the API refuses unknown or null fields)").isFalse();

        Approval answered = c.awaitApproval("apr_1", Duration.ofSeconds(60));
        assertThat(answered.isApproved()).isTrue();
        assertThat(answered.choice()).isEqualTo("Refund");
        assertThat(requests.get(1).path()).isEqualTo("/apps/3/approvals/apr_1");

        c.getApproval("apr_1", Duration.ofSeconds(90));
        assertThat(requests.get(2).query()).containsEntry("wait", "25");
    }

    @Test
    void sendsAndManagesTheInbox() throws IOException {
        answer = r -> {
            if (r.path().equals("/apps/3/inbox/notifications")) {
                return new Object[] {201, "{\"notifications\":[{\"user_id\":\"ann\",\"id\":\"ntf_1\",\"new\":true}],\"push\":{\"users\":1}}"};
            }
            if (r.method().equals("GET")) {
                return new Object[] {200, "{\"notifications\":[{\"id\":\"ntf_1\",\"user_id\":\"ann\",\"title\":\"Hi\","
                        + "\"data\":{\"order_id\":1042},\"created_at\":\"2026-11-02T10:00:00Z\",\"read_at\":null}],\"next\":null,\"unread\":1}"};
            }
            if (r.method().equals("DELETE")) {
                return new Object[] {200, "{\"deleted\":[\"ntf_1\"],\"unread\":0}"};
            }
            return new Object[] {200, "{\"changed\":[\"ntf_1\"],\"unread\":0}"};
        };
        HibiraClient c = client();
        InboxSendResult sent = c.sendInbox(InboxMessage.builder("Hi").to("ann").url("/orders/1042")
                .data(Map.of("order_id", 1042)).key("k1").build());
        assertThat(sent.notifications().get(0).isNew()).isTrue();
        assertThat(sent.push().users()).isEqualTo(1);
        JsonNode body = requests.get(0).json();
        assertThat(body.get("users").get(0).asText()).isEqualTo("ann");
        assertThat(body.get("data").get("order_id").asInt()).isEqualTo(1042);
        assertThat(body.has("push")).isFalse();

        InboxPage page = c.listInbox("ann", "unread", 10, null);
        assertThat(page.unread()).isEqualTo(1);
        assertThat(page.notifications().get(0).data().get("order_id").asInt()).isEqualTo(1042);
        assertThat(requests.get(1).path()).isEqualTo("/apps/3/inbox/users/ann/notifications");
        assertThat(requests.get(1).query()).containsEntry("status", "unread").containsEntry("limit", "10");

        assertThat(c.markInbox("ann", List.of("ntf_1"), true, null).ids()).containsExactly("ntf_1");
        assertThat(requests.get(2).json().get("read").asBoolean()).isTrue();
        assertThat(requests.get(2).json().has("archived")).isFalse();
        c.markAllInbox("ann", null, true);
        assertThat(requests.get(3).json().get("all").asBoolean()).isTrue();

        assertThat(c.deleteInbox("ann", "ntf_1").ids()).containsExactly("ntf_1");
        assertThat(requests.get(4).method()).isEqualTo("DELETE");
        assertThat(requests.get(4).path()).isEqualTo("/apps/3/inbox/users/ann/notifications/ntf_1");
        assertSigned(requests.get(4));
    }

    @Test
    void signsInboxTokens() throws IOException {
        String token = client().inboxToken("ann", Duration.ofHours(1));
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        JsonNode header = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(header.get("alg").asText()).isEqualTo("HS256");
        assertThat(claims.get("sub").asText()).isEqualTo("ann");
        assertThat(claims.get("exp").asLong()).isBetween(Instant.now().getEpochSecond() + 3500, Instant.now().getEpochSecond() + 3700);
        byte[] sig = HibiraSignature.hmacBytes(SECRET, (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(sig)).isEqualTo(parts[2]);
        assertThatThrownBy(() -> client().inboxToken("ann", Duration.ofDays(31))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishesPushNotifications() throws IOException {
        answer = r -> new Object[] {200, "{\"publishId\":\"pubid-1\"}"};
        HibiraClient c = client(HibiraOptions.builder().pushInstanceId("inst-1").pushSecretKey("pushsecret"));
        String id = c.publishToUsers(List.of("ann"), PushNotification.of("Shipped", "Arriving Tuesday").url("https://example.com/o/1"));
        assertThat(id).isEqualTo("pubid-1");
        Req r = requests.get(0);
        assertThat(r.path()).isEqualTo("/publish_api/v1/instances/inst-1/publishes/users");
        assertThat(r.headers().get("authorization")).isEqualTo("Bearer pushsecret");
        JsonNode b = r.json();
        assertThat(b.get("users").get(0).asText()).isEqualTo("ann");
        assertThat(b.at("/web/notification/deep_link").asText()).isEqualTo("https://example.com/o/1");
        assertThat(b.at("/fcm/notification/title").asText()).isEqualTo("Shipped");
        assertThat(b.at("/apns/aps/alert/body").asText()).isEqualTo("Arriving Tuesday");
        assertThat(b.at("/apns/data/url").asText()).isEqualTo("https://example.com/o/1");

        c.publishToInterests(List.of("news"), PushNotification.of("Hi", null));
        assertThat(requests.get(1).path()).isEqualTo("/publish_api/v1/instances/inst-1/publishes/interests");

        c.deletePushUser("ann");
        assertThat(requests.get(2).method()).isEqualTo("DELETE");
        assertThat(requests.get(2).path()).isEqualTo("/customer_api/v1/instances/inst-1/users/ann");

        String token = c.pushToken("ann", Duration.ofHours(1));
        JsonNode claims = MAPPER.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(claims.get("sub").asText()).isEqualTo("ann");
        assertThat(claims.get("iss").asText()).isEqualTo("https://inst-1.pushnotifications.pusher.com");

        assertThatThrownBy(() -> client().publishToUsers(List.of("ann"), PushNotification.of("x", "y")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
    }

    @Test
    void buildsBaseUris() {
        assertThat(HibiraOptions.builder().build().baseUri()).isEqualTo(URI.create("https://ws.hibira.io"));
        assertThat(HibiraOptions.builder().host("localhost").port(6001).useTls(false).build().baseUri())
                .isEqualTo(URI.create("http://localhost:6001"));
        assertThat(HibiraOptions.builder().pushEndpoint("push.example.com/").build().pushBaseUri())
                .isEqualTo(URI.create("https://push.example.com"));
        assertThatThrownBy(() -> HibiraClient.create(HibiraOptions.builder().build()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("hibira.io/portal");
    }

    private void assertSigned(Req r) {
        Map<String, String> q = new TreeMap<>(r.query());
        String signature = q.remove("auth_signature");
        StringBuilder canonical = new StringBuilder();
        q.forEach((k, v) -> canonical.append(canonical.length() == 0 ? "" : "&").append(k).append('=').append(v));
        assertThat(signature).isEqualTo(HibiraSignature.hmac(SECRET, r.method() + "\n" + r.path() + "\n" + canonical));
        assertThat(q.get("auth_key")).isEqualTo(KEY);
        if (!r.body().isEmpty()) {
            assertThat(q.get("body_md5")).isEqualTo(HibiraSignature.md5(r.body().getBytes(StandardCharsets.UTF_8)));
        }
    }
}
