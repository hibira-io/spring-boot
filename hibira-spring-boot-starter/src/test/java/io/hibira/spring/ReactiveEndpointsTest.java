package io.hibira.spring;

import static io.hibira.spring.TestSupport.SECRET;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hibira.Hibira;
import io.hibira.HibiraSignature;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = ReactiveEndpointsTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=reactive")
class ReactiveEndpointsTest {

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
        WebFilter testUser() {
            return (exchange, chain) -> {
                String user = exchange.getRequest().getHeaders().getFirst("X-Test-User");
                if (user == null) {
                    return chain.filter(exchange);
                }
                return chain.filter(exchange.mutate().principal(Mono.just(new TestSupport.TestPrincipal(user))).build());
            };
        }
    }

    @Autowired
    Environment env;

    @Autowired
    TestSupport.WebhookListener webhooks;

    int port;

    @BeforeEach
    void setUp() {
        port = env.getRequiredProperty("local.server.port", Integer.class);
    }

    @Test
    void authorizesChannels() throws Exception {
        HttpResponse<String> r = TestSupport.post(port, "/hibira/auth", "application/x-www-form-urlencoded",
                "socket_id=1234.1234&channel_name=private-orders.1042", "ann");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(r.body()).get("auth").asText()).isEqualTo(TestSupport.sign("1234.1234", "private-orders.1042"));

        HttpResponse<String> json = TestSupport.post(port, "/hibira/auth", "application/json",
                "{\"socket_id\":\"1.2\",\"channel_name\":\"presence-room.lobby\"}", "ann");
        assertThat(json.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(json.body()).get("channel_data").asText()).contains("\"user_id\":\"ann\"");

        assertThat(TestSupport.post(port, "/hibira/auth", "application/x-www-form-urlencoded",
                "socket_id=1.2&channel_name=private-orders.1042", "bob").statusCode()).isEqualTo(403);
        assertThat(TestSupport.post(port, "/hibira/auth", "application/x-www-form-urlencoded",
                "socket_id=1.2&channel_name=private-orders.1042", null).statusCode()).isEqualTo(403);
    }

    @Test
    void signsUsersInAndHandsOutTokens() throws Exception {
        HttpResponse<String> r = TestSupport.post(port, "/hibira/user-auth", "application/x-www-form-urlencoded", "socket_id=1.2", "ann");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(r.body()).get("user_data").asText()).isEqualTo("{\"id\":\"ann\"}");
        assertThat(TestSupport.get(port, "/hibira/inbox/token", "ann").statusCode()).isEqualTo(200);
        assertThat(TestSupport.get(port, "/hibira/inbox/token", null).statusCode()).isEqualTo(403);
        assertThat(TestSupport.get(port, "/hibira/push/token?user_id=ann", "ann").statusCode()).isEqualTo(200);
    }

    @Test
    void receivesWebhooks() throws Exception {
        webhooks.all.clear();
        String body = "{\"time_ms\":1,\"events\":[{\"name\":\"channel_vacated\",\"channel\":\"orders\"}]}";
        assertThat(TestSupport.webhook(port, body, HibiraSignature.hmac(SECRET, body)).statusCode()).isEqualTo(200);
        assertThat(webhooks.all).extracting(e -> e.getName()).containsExactly("channel_vacated");
        assertThat(TestSupport.webhook(port, body, "bad").statusCode()).isEqualTo(401);
    }
}
