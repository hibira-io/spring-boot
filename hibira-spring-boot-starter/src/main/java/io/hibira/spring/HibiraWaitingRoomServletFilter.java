package io.hibira.spring;

import io.hibira.WaitingRoom;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sends visitors without a waiting room pass to the Hibira queue (Spring MVC).
 * Admitted requests carry the {@link WaitingRoom.Pass} in the request
 * attribute {@value WaitingRoom#ATTRIBUTE}.
 */
public class HibiraWaitingRoomServletFilter extends OncePerRequestFilter {

    private final HibiraWaitingRooms rooms;

    public HibiraWaitingRoomServletFilter(HibiraWaitingRooms rooms) {
        this.rooms = rooms;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        WaitingRoom.Options room = rooms.roomFor(path);
        if (room == null) {
            chain.doFilter(request, response);
            return;
        }
        String url = request.getRequestURL() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        String cookie = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (c.getName().equals(room.cookieName())) {
                    cookie = c.getValue();
                }
            }
        }
        WaitingRoom.Decision d = rooms.decide(room, url, request.getMethod(), cookie);
        if (d.allowed()) {
            request.setAttribute(WaitingRoom.ATTRIBUTE, d.pass());
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        if (d.action() == WaitingRoom.Action.DENY) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getOutputStream().write(WaitingRoom.DENY_BODY.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (d.setCookie() != null) {
            response.addHeader("Set-Cookie", d.setCookie());
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", d.location());
    }
}
