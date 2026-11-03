package io.hibira.spring;

import io.hibira.Hibira;
import io.hibira.HibiraClient;
import io.hibira.HibiraOptions;
import io.hibira.HibiraSignature;
import io.hibira.PresenceMember;
import io.hibira.WebhookEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Shared test beans: channel rules, a recording client and webhook capture. */
final class TestSupport {

    static final String KEY = "278d425bdf160c739803";
    static final String SECRET = "7ad3773142a6692b25b8";

    private TestSupport() {
    }

    /** A Hibira whose API calls are recorded instead of sent; signatures are real. */
    static Hibira recordingHibira(List<List<Object>> calls) {
        HibiraClient real = HibiraClient.create(HibiraOptions.builder().appId("3").key(KEY).secret(SECRET)
                .pushInstanceId("inst-1").pushSecretKey("pushsecret").build());
        InvocationHandler h = (proxy, method, args) -> {
            String n = method.getName();
            if (n.equals("trigger") && args.length == 4) {
                calls.add(Arrays.asList(n, args[0], args[1], args[2], args[3]));
                return null;
            }
            if (n.equals("triggerBatch") || n.startsWith("publish") || n.equals("sendInbox")) {
                calls.add(Arrays.asList(n, args[0]));
                return null;
            }
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        };
        return (Hibira) Proxy.newProxyInstance(Hibira.class.getClassLoader(), new Class<?>[] {Hibira.class}, h);
    }

    static String sign(String socketId, String channel) {
        return KEY + ":" + HibiraSignature.hmac(SECRET, socketId + ":" + channel);
    }

    static HttpResponse<String> post(int port, String path, String contentType, String body, String user) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (user != null) {
            b.header("X-Test-User", user);
        }
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> get(int port, String path, String user) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (user != null) {
            b.header("X-Test-User", user);
        }
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> webhook(int port, String body, String signature) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/hibira/webhooks"))
                .header("Content-Type", "application/json")
                .header("X-Pusher-Key", KEY)
                .header("X-Pusher-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    }

    record TestPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }

    /** Channel rules used by the endpoint tests. */
    @Component
    static class Channels {
        @HibiraChannel("private-orders.{orderId}")
        boolean order(Principal user, long orderId) {
            return user != null && orderId == 1042 && user.getName().equals("ann");
        }

        @HibiraChannel("private-public-ish")
        boolean anyone() {
            return true;
        }

        @HibiraChannel("presence-room.{room}")
        Object room(HibiraUser user, @ChannelParam("room") String name) {
            return name.equals("lobby") ? Map.of("name", user.id().toUpperCase()) : null;
        }

        @HibiraChannel("presence-game.{game}")
        Object game(HibiraChannelRequest request, String game) {
            return PresenceMember.of("guest-" + request.socketId().replace(".", ""), Map.of("game", game));
        }
    }

    /** Collects webhook events. */
    @Component
    static class WebhookListener {
        final List<WebhookEvent> all = new CopyOnWriteArrayList<>();
        final List<WebhookEvent> joined = new CopyOnWriteArrayList<>();

        @EventListener
        void on(WebhookEvent e) {
            all.add(e);
        }

        @EventListener(condition = "#e.name == 'member_added'")
        void onJoin(WebhookEvent e) {
            joined.add(e);
        }
    }

    @HibiraBroadcast(channels = {"private-orders.#{orderId}", "orders"}, event = "order.shipped")
    record OrderShipped(long orderId, String carrier) {
    }

    record NotBroadcast(String x) {
    }

    record Moved(String board, int x, String socket) implements HibiraBroadcastable {
        @Override
        public List<String> broadcastChannels() {
            return List.of("board-" + board);
        }

        @Override
        public Object broadcastData() {
            return Map.of("x", x);
        }

        @Override
        public String broadcastExceptSocketId() {
            return socket;
        }
    }
}
