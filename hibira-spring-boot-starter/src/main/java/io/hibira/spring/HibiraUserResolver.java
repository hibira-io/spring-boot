package io.hibira.spring;

import java.security.Principal;

/**
 * Turns the request's principal into a {@link HibiraUser}. The default uses
 * {@link Principal#getName()} as the ID. Define a bean to use your own IDs
 * or add user info:
 *
 * <pre>{@code
 * @Bean
 * HibiraUserResolver hibiraUser(UserRepository users) {
 *     return principal -> {
 *         User u = users.findByEmail(principal.getName());
 *         return new HibiraUser(u.getId().toString(), Map.of("name", u.getName()));
 *     };
 * }
 * }</pre>
 */
@FunctionalInterface
public interface HibiraUserResolver {

    /** The user, or null when the principal is not a user of the app. Only called with a principal. */
    HibiraUser resolve(Principal principal);
}
