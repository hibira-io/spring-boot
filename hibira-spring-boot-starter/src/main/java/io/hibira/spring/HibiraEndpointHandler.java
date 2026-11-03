package io.hibira.spring;

import io.hibira.Hibira;
import io.hibira.HibiraChannels;
import io.hibira.PresenceMember;
import io.hibira.Webhook;
import io.hibira.WebhookEvent;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * What the endpoints do, apart from the web stack: Spring MVC and WebFlux
 * both hand their requests to this class.
 */
public class HibiraEndpointHandler {

    private static final Log log = LogFactory.getLog(HibiraEndpointHandler.class);

    private final Hibira hibira;
    private final HibiraChannelRegistry channels;
    private final HibiraUserResolver users;
    private final ApplicationEventPublisher events;
    private final HibiraProperties properties;

    /** Creates the handler. */
    public HibiraEndpointHandler(Hibira hibira, HibiraChannelRegistry channels, HibiraUserResolver users,
            ApplicationEventPublisher events, HibiraProperties properties) {
        this.hibira = hibira;
        this.channels = channels;
        this.users = users;
        this.events = events;
        this.properties = properties;
    }

    /** An HTTP answer: a status and a JSON body. */
    public record Result(int status, Object body) {
        static Result ok(Object body) {
            return new Result(200, body);
        }

        static Result error(int status, String message) {
            return new Result(status, Map.of("error", message));
        }

        /** The body as JSON. */
        public String json() {
            return EndpointParams.toJson(body);
        }
    }

    /** Authorizes a private or presence channel subscription (pusher-js channelAuthorization). */
    public Result authorizeChannel(String socketId, String channel, Principal principal) {
        if (isBlank(socketId) || isBlank(channel)) {
            return Result.error(400, "socket_id and channel_name are required");
        }
        if (!HibiraChannels.isValid(channel)) {
            return Result.error(400, "invalid channel name");
        }
        if (!HibiraChannels.isPrivate(channel) && !HibiraChannels.isPresence(channel)) {
            return Result.error(400, "only private- and presence- channels need authorization");
        }
        Optional<HibiraChannelRegistry.Match> match = channels.match(channel);
        if (match.isEmpty()) {
            log.debug("Hibira: no @HibiraChannel matches " + channel + ", refused");
            return Result.error(403, "forbidden");
        }
        HibiraUser user = resolve(principal);
        if (user == null && match.get().rule().needsUser()) {
            return Result.error(403, "forbidden");
        }
        HibiraChannelRequest request = new HibiraChannelRequest(socketId, channel, principal, user, match.get().values());
        Object decision;
        try {
            decision = channels.invoke(match.get(), request);
        } catch (HibiraChannelRegistry.ChannelParamException e) {
            log.debug("Hibira: " + channel + " refused: " + e.getMessage());
            return Result.error(403, "forbidden");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try {
            if (HibiraChannels.isPresence(channel)) {
                PresenceMember member = member(decision, user);
                return member == null ? Result.error(403, "forbidden") : Result.ok(hibira.authorizePresenceChannel(socketId, channel, member));
            }
            boolean allowed = decision != null && !Boolean.FALSE.equals(decision);
            return allowed ? Result.ok(hibira.authorizeChannel(socketId, channel)) : Result.error(403, "forbidden");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    private static PresenceMember member(Object decision, HibiraUser user) {
        if (decision == null || Boolean.FALSE.equals(decision)) {
            return null;
        }
        if (decision instanceof PresenceMember m) {
            return m;
        }
        if (user == null) {
            log.debug("Hibira: presence channels need a signed-in user (or return a PresenceMember)");
            return null;
        }
        if (Boolean.TRUE.equals(decision)) {
            return PresenceMember.of(user.id(), user.info());
        }
        return PresenceMember.of(user.id(), decision);
    }

    /** Signs the user in on a connection (pusher-js userAuthentication, pusher.signin()). */
    public Result authenticateUser(String socketId, Principal principal) {
        if (isBlank(socketId)) {
            return Result.error(400, "socket_id is required");
        }
        HibiraUser user = resolve(principal);
        if (user == null) {
            return Result.error(403, "not signed in");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (user.info() != null) {
            data.put("user_info", user.info());
        }
        try {
            return Result.ok(hibira.authenticateUser(socketId, user.id(), data));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** A notification inbox user token for the signed-in user ({"token"}). */
    public Result inboxToken(Principal principal) {
        HibiraUser user = resolve(principal);
        if (user == null) {
            return Result.error(403, "not signed in");
        }
        var ttl = properties.getInbox().getTokenTtl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", hibira.inboxToken(user.id(), ttl));
        body.put("expires_at", Instant.now().plus(ttl).toString());
        return Result.ok(body);
    }

    /** A push token for the signed-in user (push SDK TokenProvider, ?user_id=). */
    public Result pushToken(String userIdParam, Principal principal) {
        if (!hibira.options().pushConfigured()) {
            return Result.error(404, "Hibira Push is not configured");
        }
        HibiraUser user = resolve(principal);
        if (user == null) {
            return Result.error(403, "not signed in");
        }
        if (!isBlank(userIdParam) && !userIdParam.equals(user.id())) {
            return Result.error(403, "user_id is not the signed-in user");
        }
        return Result.ok(Map.of("token", hibira.pushToken(user.id(), properties.getPush().getTokenTtl())));
    }

    /** Verifies a webhook and publishes each of its events as an application event. */
    public Result webhook(String key, String signature, String previousSignature, byte[] body) {
        Webhook webhook = hibira.verifyWebhook(key, signature, previousSignature, body);
        if (webhook == null) {
            return Result.error(401, "invalid webhook signature");
        }
        for (WebhookEvent e : webhook.events()) {
            events.publishEvent(e);
        }
        return Result.ok(Map.of());
    }

    private HibiraUser resolve(Principal principal) {
        if (principal == null
                || "org.springframework.security.authentication.AnonymousAuthenticationToken".equals(principal.getClass().getName())) {
            return null;
        }
        HibiraUser user = users.resolve(principal);
        if (user == null || isBlank(user.id())) {
            return null;
        }
        return user;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
