package io.hibira.spring;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** The starter's endpoints for Spring WebFlux. Paths come from {@code hibira.endpoints.*}. */
@ResponseBody
@org.springframework.stereotype.Controller
public class HibiraReactiveEndpoints {

    private final HibiraEndpointHandler handler;

    /** Creates the endpoints. */
    public HibiraReactiveEndpoints(HibiraEndpointHandler handler) {
        this.handler = handler;
    }

    /** Channel authorization: form or JSON with socket_id and channel_name. */
    @PostMapping(path = "${hibira.endpoints.auth:/hibira/auth}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> auth(ServerWebExchange exchange) {
        return Mono.zip(params(exchange), principal(exchange))
                .publishOn(Schedulers.boundedElastic())
                .map(t -> respond(handler.authorizeChannel(t.getT1().get("socket_id"), t.getT1().get("channel_name"), t.getT2().orElse(null))));
    }

    /** User sign-in: form or JSON with socket_id. */
    @PostMapping(path = "${hibira.endpoints.user-auth:/hibira/user-auth}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> userAuth(ServerWebExchange exchange) {
        return Mono.zip(params(exchange), principal(exchange))
                .publishOn(Schedulers.boundedElastic())
                .map(t -> respond(handler.authenticateUser(t.getT1().get("socket_id"), t.getT2().orElse(null))));
    }

    /** The signed-in user's inbox token. */
    @GetMapping(path = "${hibira.endpoints.inbox-token:/hibira/inbox/token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> inboxToken(ServerWebExchange exchange) {
        return principal(exchange).map(p -> respond(handler.inboxToken(p.orElse(null))));
    }

    /** The signed-in user's push token. */
    @GetMapping(path = "${hibira.endpoints.push-token:/hibira/push/token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> pushToken(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getQueryParams().getFirst("user_id");
        return principal(exchange).map(p -> respond(handler.pushToken(userId, p.orElse(null))));
    }

    /** Webhooks from Hibira. */
    @PostMapping(path = "${hibira.endpoints.webhooks:/hibira/webhooks}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> webhooks(ServerWebExchange exchange) {
        HttpHeaders h = exchange.getRequest().getHeaders();
        return body(exchange)
                .publishOn(Schedulers.boundedElastic())
                .map(b -> respond(handler.webhook(h.getFirst("X-Pusher-Key"), h.getFirst("X-Pusher-Signature"),
                        h.getFirst("X-Hibira-Previous-Signature"), b)));
    }

    private static Mono<Optional<Principal>> principal(ServerWebExchange exchange) {
        return exchange.getPrincipal().map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    private static Mono<Map<String, String>> params(ServerWebExchange exchange) {
        MediaType type = exchange.getRequest().getHeaders().getContentType();
        if (type != null && type.toString().toLowerCase(Locale.ROOT).startsWith("application/json")) {
            return body(exchange).map(EndpointParams::fromJson);
        }
        return exchange.getFormData().map(f -> EndpointParams.of(f.getFirst("socket_id"), f.getFirst("channel_name")));
    }

    private static Mono<byte[]> body(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0]);
    }

    private static ResponseEntity<String> respond(HibiraEndpointHandler.Result r) {
        return ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON).body(r.json());
    }
}
