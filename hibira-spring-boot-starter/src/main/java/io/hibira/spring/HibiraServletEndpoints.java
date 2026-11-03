package io.hibira.spring;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The starter's endpoints for Spring MVC. Paths come from
 * {@code hibira.endpoints.*}. Behind Spring Security, the browser endpoints
 * need the session (and CSRF token, or an exemption) like any other POST, and
 * the webhook endpoint must be reachable without a session or CSRF token.
 */
@ResponseBody
@org.springframework.stereotype.Controller
public class HibiraServletEndpoints {

    private final HibiraEndpointHandler handler;

    /** Creates the endpoints. */
    public HibiraServletEndpoints(HibiraEndpointHandler handler) {
        this.handler = handler;
    }

    /** Channel authorization: form or JSON with socket_id and channel_name. */
    @PostMapping(path = "${hibira.endpoints.auth:/hibira/auth}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> auth(HttpServletRequest request) throws IOException {
        Map<String, String> p = params(request);
        return respond(handler.authorizeChannel(p.get("socket_id"), p.get("channel_name"), request.getUserPrincipal()));
    }

    /** User sign-in: form or JSON with socket_id. */
    @PostMapping(path = "${hibira.endpoints.user-auth:/hibira/user-auth}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> userAuth(HttpServletRequest request) throws IOException {
        Map<String, String> p = params(request);
        return respond(handler.authenticateUser(p.get("socket_id"), request.getUserPrincipal()));
    }

    /** The signed-in user's inbox token. */
    @GetMapping(path = "${hibira.endpoints.inbox-token:/hibira/inbox/token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> inboxToken(Principal principal) {
        return respond(handler.inboxToken(principal));
    }

    /** The signed-in user's push token. */
    @GetMapping(path = "${hibira.endpoints.push-token:/hibira/push/token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pushToken(@RequestParam(name = "user_id", required = false) String userId, Principal principal) {
        return respond(handler.pushToken(userId, principal));
    }

    /** Webhooks from Hibira. */
    @PostMapping(path = "${hibira.endpoints.webhooks:/hibira/webhooks}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> webhooks(HttpServletRequest request) throws IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        return respond(handler.webhook(request.getHeader("X-Pusher-Key"), request.getHeader("X-Pusher-Signature"),
                request.getHeader("X-Hibira-Previous-Signature"), body));
    }

    private static Map<String, String> params(HttpServletRequest request) throws IOException {
        String type = request.getContentType();
        if (type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
            return EndpointParams.fromJson(StreamUtils.copyToByteArray(request.getInputStream()));
        }
        return EndpointParams.of(request.getParameter("socket_id"), request.getParameter("channel_name"));
    }

    private static ResponseEntity<String> respond(HibiraEndpointHandler.Result r) {
        return ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON).body(r.json());
    }
}
