package io.hibira;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The default {@link Hibira}, over {@link java.net.http.HttpClient}. Thread-safe. */
public final class HibiraClient implements Hibira {

    /** The library version, sent in the User-Agent. */
    public static final String VERSION = version();

    private static final int MAX_CHANNELS = 100;
    private static final int MAX_BATCH = 10;

    private final HibiraOptions options;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Clock clock;

    private HibiraClient(HibiraOptions options, HttpClient http, ObjectMapper json, Clock clock) {
        this.options = Objects.requireNonNull(options, "options").validate();
        this.http = http != null ? http : HttpClient.newBuilder().connectTimeout(options.timeout()).build();
        this.json = json != null ? json : Json.MAPPER;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /** Creates a client with its own HTTP client. */
    public static HibiraClient create(HibiraOptions options) {
        return new HibiraClient(options, null, null, null);
    }

    /**
     * Creates a client over {@code http} (null: a new one), serializing event
     * data with {@code json} (null: a default ObjectMapper with java.time support).
     */
    public static HibiraClient create(HibiraOptions options, HttpClient http, ObjectMapper json) {
        return new HibiraClient(options, http, json, null);
    }

    static HibiraClient create(HibiraOptions options, HttpClient http, ObjectMapper json, Clock clock) {
        return new HibiraClient(options, http, json, clock);
    }

    @Override
    public HibiraOptions options() {
        return options;
    }

    // ---- Events -------------------------------------------------------------

    @Override
    public void trigger(Collection<String> channels, String event, Object data, String socketId) {
        Objects.requireNonNull(channels, "channels");
        requireText(event, "event");
        List<String> list = new ArrayList<>(new LinkedHashSet<>(channels));
        list.forEach(HibiraChannels::requireChannel);
        if (socketId != null && !socketId.isEmpty()) {
            checkSocketId(socketId);
        }
        String payload = dataString(data);
        for (int i = 0; i < list.size(); i += MAX_CHANNELS) {
            ObjectNode body = json.createObjectNode();
            body.put("name", event);
            body.put("data", payload);
            ArrayNode arr = body.putArray("channels");
            list.subList(i, Math.min(list.size(), i + MAX_CHANNELS)).forEach(arr::add);
            if (socketId != null && !socketId.isEmpty()) {
                body.put("socket_id", socketId);
            }
            send("POST", "events", null, body);
        }
    }

    @Override
    public void triggerBatch(List<BatchEvent> events) {
        Objects.requireNonNull(events, "events");
        for (int i = 0; i < events.size(); i += MAX_BATCH) {
            ObjectNode body = json.createObjectNode();
            ArrayNode batch = body.putArray("batch");
            for (BatchEvent e : events.subList(i, Math.min(events.size(), i + MAX_BATCH))) {
                HibiraChannels.requireChannel(e.channel());
                requireText(e.name(), "event name");
                ObjectNode o = batch.addObject();
                o.put("channel", e.channel());
                o.put("name", e.name());
                o.put("data", dataString(e.data()));
                if (e.socketId() != null && !e.socketId().isEmpty()) {
                    checkSocketId(e.socketId());
                    o.put("socket_id", e.socketId());
                }
            }
            send("POST", "batch_events", null, body);
        }
    }

    // ---- Channels and users -------------------------------------------------

    @Override
    public Map<String, ChannelInfo> channels(String prefix, String info) {
        Map<String, String> q = new LinkedHashMap<>();
        if (prefix != null && !prefix.isEmpty()) {
            q.put("filter_by_prefix", prefix);
        }
        if (info != null && !info.isEmpty()) {
            q.put("info", info);
        }
        JsonNode channels = parse(send("GET", "channels", q, null)).path("channels");
        Map<String, ChannelInfo> result = new LinkedHashMap<>();
        channels.properties().forEach(f -> result.put(f.getKey(), new ChannelInfo(true,
                f.getValue().hasNonNull("user_count") ? f.getValue().get("user_count").asInt() : null,
                f.getValue().hasNonNull("subscription_count") ? f.getValue().get("subscription_count").asInt() : null)));
        return result;
    }

    @Override
    public ChannelInfo channel(String channel, String info) {
        HibiraChannels.requireChannel(channel);
        Map<String, String> q = info == null || info.isEmpty() ? null : Map.of("info", info);
        return read(send("GET", "channels/" + channel, q, null), ChannelInfo.class);
    }

    @Override
    public List<String> presenceUsers(String presenceChannel) {
        HibiraChannels.requireChannel(presenceChannel);
        JsonNode users = parse(send("GET", "channels/" + presenceChannel + "/users", null, null)).path("users");
        List<String> ids = new ArrayList<>();
        users.forEach(u -> {
            String id = u.path("id").asText("");
            if (!id.isEmpty()) {
                ids.add(id);
            }
        });
        return ids;
    }

    @Override
    public void terminateUserConnections(String userId) {
        HibiraChannels.requireUserId(userId);
        send("POST", "users/" + userId + "/terminate_connections", null, json.createObjectNode());
    }

    // ---- Signatures -------------------------------------------------------

    @Override
    public ChannelAuth authorizeChannel(String socketId, String channel) {
        checkSocketId(socketId);
        HibiraChannels.requireChannel(channel);
        return new ChannelAuth(options.key() + ":" + HibiraSignature.hmac(options.secret(), socketId + ":" + channel), null);
    }

    @Override
    public ChannelAuth authorizePresenceChannel(String socketId, String channel, PresenceMember member) {
        checkSocketId(socketId);
        HibiraChannels.requireChannel(channel);
        Objects.requireNonNull(member, "member");
        requireText(member.userId(), "member user ID");
        ObjectNode data = json.createObjectNode();
        data.put("user_id", member.userId());
        if (member.userInfo() != null) {
            data.set("user_info", json.valueToTree(member.userInfo()));
        }
        String channelData = write(data);
        return new ChannelAuth(options.key() + ":" + HibiraSignature.hmac(options.secret(), socketId + ":" + channel + ":" + channelData),
                channelData);
    }

    @Override
    public UserAuth authenticateUser(String socketId, String userId, Map<String, ?> userData) {
        checkSocketId(socketId);
        HibiraChannels.requireUserId(userId);
        ObjectNode data = json.createObjectNode();
        data.put("id", userId);
        if (userData != null) {
            userData.forEach((k, v) -> {
                if (!"id".equals(k)) {
                    data.set(k, json.valueToTree(v));
                }
            });
        }
        String userDataJson = write(data);
        return new UserAuth(options.key() + ":" + HibiraSignature.hmac(options.secret(), socketId + "::user::" + userDataJson), userDataJson);
    }

    @Override
    public Webhook verifyWebhook(String key, String signature, String previousSignature, byte[] body) {
        if (body == null || !options.key().equals(key)) {
            return null;
        }
        String expected = HibiraSignature.hmac(options.secret(), body);
        if (!HibiraSignature.equal(expected, signature) && !HibiraSignature.equal(expected, previousSignature)) {
            return null;
        }
        try {
            Webhook w = json.readValue(body, Webhook.class);
            return new Webhook(w.timeMs(), w.events().stream().map(e -> e.withTime(w.timeMs())).toList());
        } catch (IOException e) {
            return null;
        }
    }

    // ---- Approvals ----------------------------------------------------------

    @Override
    public Approval requestApproval(ApprovalRequest request) {
        Objects.requireNonNull(request, "request");
        requireText(request.title(), "title");
        return read(send("POST", "approvals", null, json.valueToTree(request)), Approval.class);
    }

    @Override
    public Approval getApproval(String approvalId, Duration wait) {
        requireText(approvalId, "approvalId");
        Map<String, String> q = null;
        if (wait != null && !wait.isNegative() && !wait.isZero()) {
            long seconds = Math.min(25, (wait.toMillis() + 999) / 1000);
            q = Map.of("wait", Long.toString(seconds));
        }
        return read(send("GET", "approvals/" + enc(approvalId), q, null), Approval.class);
    }

    // ---- Inbox --------------------------------------------------------------

    @Override
    public InboxSendResult sendInbox(InboxMessage message) {
        Objects.requireNonNull(message, "message");
        requireText(message.title(), "title");
        if (message.users() == null || message.users().isEmpty()) {
            throw new IllegalArgumentException("an inbox notification needs at least one user");
        }
        message.users().forEach(HibiraChannels::requireUserId);
        return read(send("POST", "inbox/notifications", null, json.valueToTree(message)), InboxSendResult.class);
    }

    @Override
    public InboxPage listInbox(String userId, String status, int limit, String before) {
        HibiraChannels.requireUserId(userId);
        Map<String, String> q = new LinkedHashMap<>();
        if (status != null && !status.isEmpty()) {
            q.put("status", status);
        }
        if (limit > 0) {
            q.put("limit", Integer.toString(limit));
        }
        if (before != null && !before.isEmpty()) {
            q.put("before", before);
        }
        return read(send("GET", "inbox/users/" + userId + "/notifications", q, null), InboxPage.class);
    }

    @Override
    public InboxChange markInbox(String userId, List<String> ids, Boolean read, Boolean archived) {
        HibiraChannels.requireUserId(userId);
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids is empty: use markAllInbox to mark everything");
        }
        ObjectNode body = json.createObjectNode();
        ArrayNode arr = body.putArray("ids");
        ids.forEach(arr::add);
        return mark(userId, body, read, archived);
    }

    @Override
    public InboxChange markAllInbox(String userId, Boolean read, Boolean archived) {
        HibiraChannels.requireUserId(userId);
        ObjectNode body = json.createObjectNode();
        body.put("all", true);
        return mark(userId, body, read, archived);
    }

    private InboxChange mark(String userId, ObjectNode body, Boolean read, Boolean archived) {
        if (read == null && archived == null) {
            throw new IllegalArgumentException("set read and/or archived");
        }
        if (read != null) {
            body.put("read", read);
        }
        if (archived != null) {
            body.put("archived", archived);
        }
        return read(send("POST", "inbox/users/" + userId + "/notifications/mark", null, body), InboxChange.class);
    }

    @Override
    public InboxChange deleteInbox(String userId, String notificationId) {
        HibiraChannels.requireUserId(userId);
        requireText(notificationId, "notificationId");
        return read(send("DELETE", "inbox/users/" + userId + "/notifications/" + enc(notificationId), null, null), InboxChange.class);
    }

    @Override
    public InboxChange deleteAllInbox(String userId) {
        HibiraChannels.requireUserId(userId);
        return read(send("DELETE", "inbox/users/" + userId + "/notifications", null, null), InboxChange.class);
    }

    // ---- Push ---------------------------------------------------------------

    @Override
    public String publishToInterests(List<String> interests, Map<String, Object> payload) {
        if (interests == null || interests.isEmpty()) {
            throw new IllegalArgumentException("interests is empty");
        }
        ObjectNode body = json.valueToTree(payload);
        ArrayNode arr = body.putArray("interests");
        interests.forEach(arr::add);
        return parse(push("POST", "/publish_api/v1/instances/%s/publishes/interests", body)).path("publishId").asText("");
    }

    @Override
    public String publishToUsers(List<String> users, Map<String, Object> payload) {
        if (users == null || users.isEmpty()) {
            throw new IllegalArgumentException("users is empty");
        }
        ObjectNode body = json.valueToTree(payload);
        ArrayNode arr = body.putArray("users");
        users.forEach(arr::add);
        return parse(push("POST", "/publish_api/v1/instances/%s/publishes/users", body)).path("publishId").asText("");
    }

    @Override
    public void deletePushUser(String userId) {
        requireText(userId, "userId");
        push("DELETE", "/customer_api/v1/instances/%s/users/" + enc(userId), null);
    }

    // ---- HTTP ---------------------------------------------------------------

    private String send(String method, String resource, Map<String, String> query, JsonNode body) {
        String path = "/apps/" + options.appId() + "/" + resource;
        byte[] bytes = body == null ? null : write(body).getBytes(StandardCharsets.UTF_8);
        String qs = HibiraSignature.signedQuery(options.key(), options.secret(), method, path, query, bytes, clock.instant());
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(options.baseUri() + path + "?" + qs))
                .timeout(options.timeout())
                .header("User-Agent", "hibira-java/" + VERSION);
        if (bytes != null) {
            req.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
        } else {
            req.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return execute(req.build());
    }

    private String push(String method, String pathFormat, JsonNode body) {
        if (!options.pushConfigured()) {
            throw new IllegalStateException("Hibira Push is not configured: set the push instance ID and secret key "
                    + "(app page → Push notifications on hibira.io/portal)");
        }
        String path = String.format(pathFormat, enc(options.pushInstanceId()));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(options.pushBaseUri() + path))
                .timeout(options.timeout())
                .header("User-Agent", "hibira-java/" + VERSION)
                .header("Authorization", "Bearer " + options.pushSecretKey());
        if (body != null) {
            req.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(write(body), StandardCharsets.UTF_8));
        } else {
            req.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return execute(req.build());
    }

    private String execute(HttpRequest req) {
        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new HibiraException("Hibira API request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HibiraException("Hibira API request interrupted", e);
        }
        String text = res.body() == null ? "" : res.body();
        if (res.statusCode() / 100 != 2) {
            String message = text.trim();
            try {
                JsonNode n = json.readTree(text);
                if (n != null && n.hasNonNull("description")) {
                    message = n.get("description").asText();
                } else if (n != null && n.hasNonNull("error")) {
                    message = n.get("error").asText();
                }
            } catch (IOException ignored) {
                // not JSON: keep the text
            }
            throw new HibiraException(res.statusCode(), message.isEmpty() ? "HTTP " + res.statusCode() : message, text);
        }
        return text;
    }

    private String dataString(Object data) {
        if (data == null) {
            return "null";
        }
        if (data instanceof String s) {
            return s;
        }
        return write(data);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("could not serialize to JSON: " + e.getOriginalMessage(), e);
        }
    }

    private JsonNode parse(String text) {
        try {
            JsonNode n = json.readTree(text.isEmpty() ? "{}" : text);
            return n == null ? json.createObjectNode() : n;
        } catch (IOException e) {
            throw new HibiraException(200, "unexpected answer: " + e.getMessage(), text);
        }
    }

    private <T> T read(String text, Class<T> type) {
        try {
            return Json.MAPPER.readValue(text, type);
        } catch (IOException e) {
            throw new HibiraException(200, "unexpected answer: " + e.getMessage(), text);
        }
    }

    private static String version() {
        String v = HibiraClient.class.getPackage().getImplementationVersion();
        return v == null || v.isEmpty() ? "dev" : v;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void requireText(String s, String what) {
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException(what + " is required");
        }
    }

    static void checkSocketId(String socketId) {
        if (socketId == null || socketId.isEmpty() || socketId.length() > 64) {
            throw new IllegalArgumentException("not a socket ID: " + socketId);
        }
        int dot = socketId.indexOf('.');
        if (dot <= 0 || dot == socketId.length() - 1) {
            throw new IllegalArgumentException("not a socket ID: " + socketId);
        }
        for (int i = 0; i < socketId.length(); i++) {
            char c = socketId.charAt(i);
            if (i != dot && (c < '0' || c > '9')) {
                throw new IllegalArgumentException("not a socket ID: " + socketId);
            }
        }
    }
}
