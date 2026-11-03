package io.hibira.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authorizes subscriptions to the private or presence channels matching a
 * pattern. Put it on methods of any Spring bean; {@code {name}} matches one
 * part of the name (anything but a dot) and can be taken as a parameter.
 *
 * <pre>{@code
 * @Component
 * class Channels {
 *     @HibiraChannel("private-orders.{orderId}")
 *     boolean order(Principal user, long orderId) {
 *         return orders.isOwner(orderId, user.getName());
 *     }
 *
 *     @HibiraChannel("presence-room.{room}")
 *     Object room(HibiraUser user, String room) {
 *         return rooms.canJoin(room, user.id()) ? Map.of("name", user.id()) : null;
 *     }
 * }
 * }</pre>
 *
 * <p>Parameters can be {@code {placeholders}} (by name, or with
 * {@link ChannelParam}), converted to the parameter type; the
 * {@link java.security.Principal}; the {@link HibiraUser}; or the
 * {@link HibiraChannelRequest}. A method that takes the principal or the
 * user is only called when someone is signed in; anyone else is refused.
 *
 * <p>Return {@code true} to allow and {@code false} or null to refuse. For a
 * presence channel, return {@code true} to join as the signed-in user, any
 * other object to join as the signed-in user with that object as user info,
 * or an {@link io.hibira.PresenceMember} to choose both.
 *
 * <p>A channel no pattern matches is refused.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HibiraChannel {

    /** The channel name pattern, including the private- or presence- prefix. */
    String value();
}
