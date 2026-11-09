package io.hibira.spring;

import io.hibira.WaitingRoom;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Sends visitors without a waiting room pass to the Hibira queue (WebFlux).
 * Admitted exchanges carry the {@link WaitingRoom.Pass} in the attribute
 * {@value WaitingRoom#ATTRIBUTE}.
 */
public class HibiraWaitingRoomWebFilter implements WebFilter {

    private final HibiraWaitingRooms rooms;

    public HibiraWaitingRoomWebFilter(HibiraWaitingRooms rooms) {
        this.rooms = rooms;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        WaitingRoom.Options room = rooms.roomFor(request.getPath().pathWithinApplication().value());
        if (room == null) {
            return chain.filter(exchange);
        }
        HttpCookie cookie = request.getCookies().getFirst(room.cookieName());
        WaitingRoom.Decision d = rooms.decide(room, request.getURI().toString(), request.getMethod().name(),
                cookie == null ? null : cookie.getValue());
        if (d.allowed()) {
            exchange.getAttributes().put(WaitingRoom.ATTRIBUTE, d.pass());
            return chain.filter(exchange);
        }
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setCacheControl("no-store");
        if (d.action() == WaitingRoom.Action.DENY) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(WaitingRoom.DENY_BODY.getBytes(StandardCharsets.UTF_8))));
        }
        if (d.setCookie() != null) {
            response.getHeaders().add(HttpHeaders.SET_COOKIE, d.setCookie());
        }
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().set(HttpHeaders.LOCATION, d.location());
        return response.setComplete();
    }
}
