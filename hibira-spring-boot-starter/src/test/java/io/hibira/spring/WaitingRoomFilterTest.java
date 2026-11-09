package io.hibira.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hibira.Hibira;
import io.hibira.HibiraSignature;
import io.hibira.WaitingRoom;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

class WaitingRoomFilterTest {

    static final String PROPS = "hibira.waiting-room.rooms[/drop]=presale";
    static final String APP = "hibira.app-id=3";
    static final String KEY = "hibira.key=" + TestSupport.KEY;
    static final String SECRET = "hibira.secret=" + TestSupport.SECRET;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
        @Bean
        Hibira hibira() {
            return TestSupport.recordingHibira(new ArrayList<>());
        }

        @Bean
        Pages pages() {
            return new Pages();
        }
    }

    @RestController
    @RequestMapping
    static class Pages {
        @GetMapping("/drop/{id}")
        String drop(jakarta.servlet.http.HttpServletRequest request) {
            return "in #" + ((WaitingRoom.Pass) request.getAttribute(WaitingRoom.ATTRIBUTE)).number();
        }

        @GetMapping("/open")
        String open() {
            return "open";
        }
    }

    @RestController
    static class ReactivePages {
        @GetMapping("/drop/{id}")
        String drop(ServerWebExchange exchange) {
            return "in #" + ((WaitingRoom.Pass) exchange.getAttribute(WaitingRoom.ATTRIBUTE)).number();
        }

        @GetMapping("/open")
        String open() {
            return "open";
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ReactiveApp {
        @Bean
        Hibira hibira() {
            return TestSupport.recordingHibira(new ArrayList<>());
        }

        @Bean
        ReactivePages pages() {
            return new ReactivePages();
        }
    }

    static String fresh(String room) {
        long now = Instant.now().getEpochSecond();
        String json = "{\"v\":1,\"app\":\"3\",\"room\":\"" + room + "\",\"n\":7,\"tid\":\"t7\",\"iat\":" + now + ",\"exp\":" + (now + 600) + "}";
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.HexFormat.of().parseHex(
                HibiraSignature.hmac(TestSupport.SECRET, "hibira-waitroom-pass." + payload)));
        return payload + "." + sig;
    }

    abstract static class Cases {
        final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

        abstract int port();

        HttpResponse<String> send(String method, String path, String cookie) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port() + path))
                    .method(method, HttpRequest.BodyPublishers.noBody());
            if (cookie != null) {
                b.header("Cookie", cookie);
            }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }

        @Test
        void sendsVisitorsWithoutAPassToTheQueue() throws Exception {
            var res = send("GET", "/drop/1?a=b", null);
            assertThat(res.statusCode()).isEqualTo(302);
            assertThat(res.headers().firstValue("Location")).hasValue("https://ws.hibira.io/wait/" + TestSupport.KEY
                    + "/presale?return=http%3A%2F%2Flocalhost%3A" + port() + "%2Fdrop%2F1%3Fa%3Db");
            assertThat(send("GET", "/open", null).body()).isEqualTo("open");
            assertThat(send("POST", "/drop/1", null).statusCode()).isEqualTo(403);
        }

        @Test
        void turnsThePassIntoACookie() throws Exception {
            String pass = fresh("presale");
            var res = send("GET", "/drop/1?a=b&hibira_pass=" + pass, null);
            assertThat(res.statusCode()).isEqualTo(302);
            assertThat(res.headers().firstValue("Location")).hasValue("http://localhost:" + port() + "/drop/1?a=b");
            String cookie = res.headers().firstValue("Set-Cookie").orElseThrow();
            assertThat(cookie).startsWith("hibira_wr_presale=" + pass + "; Path=/; Max-Age=").endsWith("; HttpOnly; SameSite=Lax");
            assertThat(send("GET", "/drop/1", "hibira_wr_presale=" + pass).body()).isEqualTo("in #7");
            assertThat(send("GET", "/drop/1", "hibira_wr_presale=" + fresh("other")).statusCode()).isEqualTo(302);
        }
    }

    @Nested
    @SpringBootTest(classes = App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"spring.main.web-application-type=servlet", PROPS, APP, KEY, SECRET})
    class Servlet extends Cases {
        @LocalServerPort
        int port;

        @Override
        int port() {
            return port;
        }
    }

    @Nested
    @SpringBootTest(classes = ReactiveApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"spring.main.web-application-type=reactive", PROPS, APP, KEY, SECRET})
    class Reactive extends Cases {
        @LocalServerPort
        int port;

        @Override
        int port() {
            return port;
        }
    }
}
