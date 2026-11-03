package io.hibira.spring;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * The {@link HibiraChannel} methods of the application's beans, found once
 * the singletons are created.
 */
public class HibiraChannelRegistry implements SmartInitializingSingleton {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private final ConfigurableListableBeanFactory beanFactory;
    private final ConversionService conversion = DefaultConversionService.getSharedInstance();
    private final List<Rule> rules = new ArrayList<>();

    /** Creates a registry over the application's beans. */
    public HibiraChannelRegistry(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String name : beanFactory.getBeanDefinitionNames()) {
            if (name.startsWith("scopedTarget.")) {
                continue;
            }
            Class<?> type;
            try {
                type = beanFactory.getType(name, false);
            } catch (RuntimeException e) {
                continue;
            }
            if (type == null) {
                continue;
            }
            Class<?> userType = ClassUtils.getUserClass(type);
            Map<Method, HibiraChannel> methods = MethodIntrospector.selectMethods(userType,
                    (MethodIntrospector.MetadataLookup<HibiraChannel>) m -> AnnotatedElementUtils.findMergedAnnotation(m, HibiraChannel.class));
            methods.forEach((method, annotation) -> add(name, method, annotation.value()));
        }
    }

    /** Adds a rule (normally found from {@link HibiraChannel}). */
    void add(String beanName, Method method, String pattern) {
        if (!pattern.startsWith("private-") && !pattern.startsWith("presence-")) {
            throw new IllegalStateException("@HibiraChannel(\"" + pattern + "\") on " + method
                    + ": only private- and presence- channels need authorization");
        }
        if (pattern.startsWith("private-encrypted-")) {
            throw new IllegalStateException("@HibiraChannel(\"" + pattern + "\") on " + method
                    + ": encrypted channels are not supported yet");
        }
        List<String> names = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        Matcher m = PLACEHOLDER.matcher(pattern);
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(pattern.substring(last, m.start()))).append("([^.]+)");
            names.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(pattern.substring(last))).append('$');

        Parameter[] params = method.getParameters();
        ArgSource[] sources = new ArgSource[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Class<?> t = p.getType();
            ChannelParam cp = p.getAnnotation(ChannelParam.class);
            if (cp != null) {
                if (!names.contains(cp.value())) {
                    throw new IllegalStateException("@ChannelParam(\"" + cp.value() + "\") on " + method
                            + " has no {" + cp.value() + "} in \"" + pattern + "\"");
                }
                sources[i] = new ArgSource(ArgKind.PARAM, cp.value(), t);
            } else if (t == HibiraChannelRequest.class) {
                sources[i] = new ArgSource(ArgKind.REQUEST, null, t);
            } else if (t == HibiraUser.class) {
                sources[i] = new ArgSource(ArgKind.USER, null, t);
            } else if (Principal.class.isAssignableFrom(t)) {
                sources[i] = new ArgSource(ArgKind.PRINCIPAL, null, t);
            } else if (p.isNamePresent() && names.contains(p.getName())) {
                sources[i] = new ArgSource(ArgKind.PARAM, p.getName(), t);
            } else {
                throw new IllegalStateException("Parameter " + i + " (" + t.getSimpleName() + ") of " + method
                        + " is not a placeholder of \"" + pattern + "\", a Principal, a HibiraUser or a HibiraChannelRequest."
                        + " Name it after the placeholder (compile with -parameters) or add @ChannelParam.");
            }
        }
        ReflectionUtils.makeAccessible(method);
        rules.add(new Rule(pattern, Pattern.compile(regex.toString()), names, beanName, method, sources));
    }

    /** The patterns, in the order they are tried. */
    public List<String> patterns() {
        return rules.stream().map(Rule::pattern).toList();
    }

    /** Whether no rule is registered. */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** Finds the first rule matching {@code channel}. */
    Optional<Match> match(String channel) {
        for (Rule r : rules) {
            Matcher m = r.regex().matcher(channel);
            if (m.matches()) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < r.names().size(); i++) {
                    values.put(r.names().get(i), m.group(i + 1));
                }
                return Optional.of(new Match(r, Collections.unmodifiableMap(values)));
            }
        }
        return Optional.empty();
    }

    /** Calls the rule's method. */
    Object invoke(Match match, HibiraChannelRequest request) throws Exception {
        Rule r = match.rule();
        Object bean = beanFactory.getBean(r.beanName());
        Method method = AopUtils.selectInvocableMethod(r.method(), bean.getClass());
        Object[] args = new Object[r.sources().length];
        for (int i = 0; i < args.length; i++) {
            ArgSource s = r.sources()[i];
            args[i] = switch (s.kind()) {
                case PARAM -> convert(match.values().get(s.name()), s.type(), r);
                case REQUEST -> request;
                case USER -> request.user();
                case PRINCIPAL -> s.type().isInstance(request.principal()) ? request.principal() : null;
            };
        }
        try {
            return method.invoke(bean, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private Object convert(String value, Class<?> type, Rule rule) {
        try {
            return conversion.convert(value, type);
        } catch (RuntimeException e) {
            throw new ChannelParamException("\"" + value + "\" is not a " + type.getSimpleName() + " (" + rule.pattern() + ")", e);
        }
    }

    record Rule(String pattern, Pattern regex, List<String> names, String beanName, Method method, ArgSource[] sources) {
        /** Whether the method takes the user or principal, so it is only called when someone is signed in. */
        boolean needsUser() {
            for (ArgSource s : sources) {
                if (s.kind() == ArgKind.USER || s.kind() == ArgKind.PRINCIPAL) {
                    return true;
                }
            }
            return false;
        }
    }

    record Match(Rule rule, Map<String, String> values) {
    }

    record ArgSource(ArgKind kind, String name, Class<?> type) {
    }

    enum ArgKind {
        PARAM, REQUEST, USER, PRINCIPAL
    }

    /** A placeholder value that does not convert to its parameter's type: the subscription is refused. */
    static final class ChannelParamException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ChannelParamException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
