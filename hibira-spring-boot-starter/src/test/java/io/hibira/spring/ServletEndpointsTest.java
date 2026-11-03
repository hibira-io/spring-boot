package io.hibira.spring;

import static io.hibira.spring.TestSupport.KEY;
import static io.hibira.spring.TestSupport.SECRET;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hibira.Hibira;
import io.hibira.HibiraSignature;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@SpringBootTest(classes = ServletEndpointsTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
class ServletEndpointsTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({TestSupport.Channels.class, TestSupport.WebhookListener.class})
    static class App {
        static final List<List<Object>> CALLS = new CopyOnWriteArrayList<>();

        @Bean
        Hibira hibira() {
            return TestSupport.recordingHibira(CALLS);
        }

        @Bean
        Filter testUser() {
            return (req, res, chain) -> {
                HttpServletRequest http = (HttpServletRequest) req;
                String user = http.getHeader("X-Test-User");
                if (user == null) {
                    chain.doFilter(req, res);
                    return;
                }
                chain.doFilter(new HttpServletRequestWrapper(http) {
                    @Override
                    public Principal getUserPrincipal() {
                        return new TestSupport.TestPrincipal(user);
                    }
                }, res);
            };
        }
    }

    @Autowired
    Environment env;

    @Autowired
    TestSupport.WebhookListener webhooks;

    @Autowired
    ApplicationEventPublisher events;

    int port;

    @BeforeEach
    void setUp() {
        port = env.getRequiredProperty("local.server.port", Integer.class);
        App.CALLS.clear();
    }

    @Test
    void authorizesPrivateChannelsWithFormBodies() throws Exception {
        HttpResponse<String> r = TestSupport.post(port, "/hibira/auth", "application/x-www-form-urlencoded",
                "socket_id=1234.1234&channel_name=private-orders.1042", "ann");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(r.body()).get("auth").asText()).isEqualTo(TestSupport.sign("1234.1234", "private-orders.1042"));
        assertThat(MAPPER.readTree(r.body()).has("channel_data")).isFalse();
    }

    @Test
    void authorizesWithJsonBodies() throws Exception {
        HttpResponse<String> r = TestSupport.post(port, "/hibira/auth", "application/json",
                "{\"socket_id\":\"1.2\",\"channel_name\":\"private-public-ish\"}", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(r.body()).get("auth").asText()).isEqualTo(TestSupport.sign("1.2", "private-public-ish"));
    }

    @Test
    void refusesWhatTheRulesRefuse() throws Exception {
        assertThat(auth("private-orders.1042", "bob").statusCode()).isEqualTo(403);
        assertThat(auth("private-orders.1042", null).statusCode()).isEqualTo(403);
        assertThat(auth("private-orders.7", "ann").statusCode()).isEqualTo(403);
        assertThat(auth("private-orders.abc", "ann").statusCode()).as("not a long").isEqualTo(403);
        assertThat(auth("private-nothing-matches", "ann").statusCode()).isEqualTo(403);
        assertThat(auth("public-channel", "ann").statusCode()).isEqualTo(400);
        assertThat(TestSupport.post(port, "/hibira/auth", "application/x-www-form-urlencoded", "channel_name=private-a", "ann").statusCode())
                .isEqualTo(400);
    }

    @Test
    void authorizesPresenceChannelsWithUserInfo() throws Exception {
        HttpResponse<String> r = auth("presence-room.lobby", "ann");
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode b = MAPPER.readTree(r.body());
        assertThat(b.get("channel_data").asText()).isEqualTo("{\"user_id\":\"ann\",\"user_info\":{\"name\":\"ANN\"}}");
        assertThat(b.get("auth").asText()).isEqualTo(KEY + ":" + HibiraSignature.hmac(SECRET, "1.2:presence-room.lobby:" + b.get("channel_data").asText()));
        assertThat(auth("presence-room.kitchen", "ann").statusCode()).isEqualTo(403);
        assertThat(auth("presence-room.lobby", null).statusCode()).as("presence needs a user").isEqualTo(403);
        HttpResponse<String> guest = auth("presence-game.chess", null);
        assertThat(guest.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(guest.body()).get("channel_data").asText()).isEqualTo("{\"user_id\":\"guest-12\",\"user_info\":{\"game\":\"chess\"}}");
    }

    @Test
    void signsUsersIn() throws Exception {
        HttpResponse<String> r = TestSupport.post(port, "/hibira/user-auth", "application/x-www-form-urlencoded", "socket_id=1.2", "ann");
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode b = MAPPER.readTree(r.body());
        assertThat(b.get("user_data").asText()).isEqualTo("{\"id\":\"ann\"}");
        assertThat(b.get("auth").asText()).isEqualTo(KEY + ":" + HibiraSignature.hmac(SECRET, "1.2::user::{\"id\":\"ann\"}"));
        assertThat(TestSupport.post(port, "/hibira/user-auth", "application/x-www-form-urlencoded", "socket_id=1.2", null).statusCode())
                .isEqualTo(403);
    }

    @Test
    void handsOutInboxAndPushTokens() throws Exception {
        HttpResponse<String> r = TestSupport.get(port, "/hibira/inbox/token", "ann");
        assertThat(r.statusCode()).isEqualTo(200);
        String token = MAPPER.readTree(r.body()).get("token").asText();
        assertThat(MAPPER.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1])).get("sub").asText()).isEqualTo("ann");
        assertThat(TestSupport.get(port, "/hibira/inbox/token", null).statusCode()).isEqualTo(403);

        HttpResponse<String> p = TestSupport.get(port, "/hibira/push/token?user_id=ann", "ann");
        assertThat(p.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(p.body()).get("token").asText()).contains(".");
        assertThat(TestSupport.get(port, "/hibira/push/token?user_id=bob", "ann").statusCode()).isEqualTo(403);
    }

    @Test
    void publishesVerifiedWebhooksAsApplicationEvents() throws Exception {
        webhooks.all.clear();
        webhooks.joined.clear();
        String body = "{\"time_ms\":1,\"events\":[{\"name\":\"member_added\",\"channel\":\"presence-room.lobby\",\"user_id\":\"ann\"},"
                + "{\"name\":\"channel_occupied\",\"channel\":\"orders\"}]}";
        HttpResponse<String> r = TestSupport.webhook(port, body, HibiraSignature.hmac(SECRET, body));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(webhooks.all).hasSize(2);
        assertThat(webhooks.joined).hasSize(1);
        assertThat(webhooks.joined.get(0).getUserId()).isEqualTo("ann");

        assertThat(TestSupport.webhook(port, body, "0000").statusCode()).isEqualTo(401);
        assertThat(webhooks.all).hasSize(2);
    }

    @Test
    void broadcastsAnnotatedEvents() {
        events.publishEvent(new TestSupport.OrderShipped(1042, "DHL"));
        events.publishEvent(new TestSupport.NotBroadcast("x"));
        events.publishEvent(new TestSupport.Moved("b1", 3, "1.2"));
        assertThat(App.CALLS).hasSize(2);
        List<Object> first = App.CALLS.get(0);
        assertThat(first.get(1)).isEqualTo(List.of("private-orders.1042", "orders"));
        assertThat(first.get(2)).isEqualTo("order.shipped");
        assertThat(first.get(3)).isEqualTo(new TestSupport.OrderShipped(1042, "DHL"));
        List<Object> second = App.CALLS.get(1);
        assertThat(second.get(1)).isEqualTo(List.of("board-b1"));
        assertThat(second.get(2)).isEqualTo("Moved");
        assertThat(second.get(4)).isEqualTo("1.2");
    }

    private HttpResponse<String> auth(String channel, String user) throws Exception {
        return TestSupport.post(port, "/hibira/auth", "application/x-www-form-urlencoded", "socket_id=1.2&channel_name=" + channel, user);
    }
}
