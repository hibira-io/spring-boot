package io.hibira.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sends an application event to Hibira channels when it is published with
 * {@link org.springframework.context.ApplicationEventPublisher}. The event
 * object is the data, as JSON.
 *
 * <pre>{@code
 * @HibiraBroadcast(channels = "private-orders.#{orderId}", event = "order.shipped")
 * public record OrderShipped(long orderId, String carrier) {}
 *
 * events.publishEvent(new OrderShipped(1042, "DHL"));
 * }</pre>
 *
 * <p>Channel names are templates: {@code #{...}} is a SpEL expression on the
 * event. Inside a transaction the event is sent after the commit (and not at
 * all on rollback), unless {@link #afterCommit()} is false.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HibiraBroadcast {

    /** The channels, as templates with {@code #{...}} SpEL expressions on the event. */
    String[] channels();

    /** The event name; empty means the class's simple name. */
    String event() default "";

    /** Wait for the surrounding transaction to commit. */
    boolean afterCommit() default true;
}
