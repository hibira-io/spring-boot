package io.hibira.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds a parameter of a {@link HibiraChannel} method to a {@code {placeholder}} of its pattern. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ChannelParam {

    /** The placeholder's name. */
    String value();
}
