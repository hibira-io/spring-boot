package io.hibira.samples.chat;

import io.hibira.Hibira;
import io.hibira.spring.HibiraBroadcast;
import io.hibira.spring.HibiraChannel;
import io.hibira.spring.HibiraUser;
import io.hibira.spring.HibiraUserResolver;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }

    // Two demo users. Use your own UserDetailsService, OAuth2 login, etc.
    @Bean
    InMemoryUserDetailsManager users() {
        return new InMemoryUserDetailsManager(
                User.withUsername("ann").password("{noop}password").roles("USER").build(),
                User.withUsername("bob").password("{noop}password").roles("USER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(a -> a.requestMatchers("/hibira/webhooks").permitAll().anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                // The page sends the XSRF-TOKEN cookie back as a header; Hibira's webhooks carry a signature instead.
                .csrf(c -> c.spa().ignoringRequestMatchers("/hibira/webhooks"))
                .build();
    }

    // What other members of a presence channel see about a user.
    @Bean
    HibiraUserResolver hibiraUser() {
        return principal -> new HibiraUser(principal.getName(), Map.of("name", capitalize(principal.getName())));
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Who may join which channel. */
    @Component
    static class Channels {
        @HibiraChannel("presence-chat.{room}")
        boolean room(HibiraUser user, String room) {
            return room.equals("lobby") || room.equals("team");
        }
    }

    /** Sent to everyone in the room by Hibira when the controller publishes it. */
    @HibiraBroadcast(channels = "presence-chat.#{room}", event = "message")
    record ChatMessage(String room, String user, String text, Instant at) {
    }

    record Send(String room, String text) {
    }

    @RestController
    static class ChatController {
        private final ApplicationEventPublisher events;
        private final Hibira hibira;

        ChatController(ApplicationEventPublisher events, Hibira hibira) {
            this.events = events;
            this.hibira = hibira;
        }

        // The page's user and what pusher-js needs to connect (the key is public; the secret stays here).
        @GetMapping("/me")
        Map<String, Object> me(Principal principal) {
            var o = hibira.options();
            return Map.of("id", principal.getName(), "name", capitalize(principal.getName()),
                    "hibira", Map.of("key", o.key(), "host", o.host(), "port", o.port(), "tls", o.useTls()));
        }

        @PostMapping("/messages")
        ResponseEntity<Void> send(@RequestBody Send send, Principal principal) {
            String text = send.text() == null ? "" : send.text().strip();
            if (text.isEmpty() || text.length() > 500) {
                return ResponseEntity.badRequest().build();
            }
            events.publishEvent(new ChatMessage(send.room(), principal.getName(), text, Instant.now()));
            return ResponseEntity.accepted().build();
        }

        // Straight to every tab the other user has open, through pusher.signin().
        @PostMapping("/wave/{userId}")
        ResponseEntity<Void> wave(@PathVariable String userId, Principal principal) {
            hibira.sendToUser(userId, "wave", Map.of("from", capitalize(principal.getName())));
            return ResponseEntity.accepted().build();
        }
    }
}
