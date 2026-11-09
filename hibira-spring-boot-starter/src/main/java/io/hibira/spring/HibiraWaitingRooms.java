package io.hibira.spring;

import io.hibira.WaitingRoom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The waiting rooms from {@code hibira.waiting-room.rooms}: finds the room for
 * a path and decides what to do with the request. Used by the servlet and
 * WebFlux filters.
 */
public class HibiraWaitingRooms {

    private final List<Map.Entry<String, WaitingRoom.Options>> rooms;

    public HibiraWaitingRooms(HibiraProperties p) {
        HibiraProperties.WaitingRoom w = p.getWaitingRoom();
        List<String> secrets = new ArrayList<>();
        secrets.add(p.getSecret());
        if (w.getPreviousSecret() != null && !w.getPreviousSecret().isEmpty()) {
            secrets.add(w.getPreviousSecret());
        }
        String host = w.getHost() != null ? w.getHost() : origin(p);
        this.rooms = w.getRooms().entrySet().stream()
                .map(e -> Map.entry(e.getKey(), new WaitingRoom.Options(p.getAppId(), p.getKey(), secrets, e.getValue(),
                        host, w.getLang(), null)))
                .sorted(Comparator.comparingInt((Map.Entry<String, WaitingRoom.Options> e) -> e.getKey().length()).reversed())
                .toList();
    }

    /** The room protecting this path, or null. */
    public WaitingRoom.Options roomFor(String path) {
        for (Map.Entry<String, WaitingRoom.Options> e : rooms) {
            String prefix = e.getKey();
            if (path.equals(prefix) || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/")) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Decides what to do with a request for {@code room}. */
    public WaitingRoom.Decision decide(WaitingRoom.Options room, String url, String method, String cookieValue) {
        return WaitingRoom.decide(url, method, cookieValue, room, Instant.now());
    }

    private static String origin(HibiraProperties p) {
        String scheme = p.isUseTls() ? "https" : "http";
        Integer port = p.getPort();
        boolean standard = port == null || port == (p.isUseTls() ? 443 : 80);
        return scheme + "://" + p.getHost() + (standard ? "" : ":" + port);
    }
}
