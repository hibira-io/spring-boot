package io.hibira.spring;

import io.hibira.Hibira;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.ClassUtils;

/**
 * Sends application events marked with {@link HibiraBroadcast} (or
 * implementing {@link HibiraBroadcastable}) to Hibira. Inside a transaction
 * the send waits for the commit. A failed send is logged, not thrown: the
 * change that raised the event has already happened.
 */
public class HibiraBroadcaster implements SmartApplicationListener {

    private static final Log log = LogFactory.getLog(HibiraBroadcaster.class);
    private static final boolean TX_PRESENT = ClassUtils.isPresent(
            "org.springframework.transaction.support.TransactionSynchronizationManager", HibiraBroadcaster.class.getClassLoader());

    private final Hibira hibira;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final TemplateParserContext template = new TemplateParserContext();
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    /** Creates the broadcaster. */
    public HibiraBroadcaster(Hibira hibira) {
        this.hibira = hibira;
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return true;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        Object payload = event instanceof PayloadApplicationEvent<?> p ? p.getPayload() : event;
        Send send = toSend(payload);
        if (send == null) {
            return;
        }
        if (send.afterCommit() && TX_PRESENT && Tx.active()) {
            Tx.afterCommit(() -> deliver(send));
        } else {
            deliver(send);
        }
    }

    Send toSend(Object payload) {
        if (payload instanceof HibiraBroadcastable b) {
            return new Send(b.broadcastChannels(), b.broadcastEvent(), b.broadcastData(), b.broadcastExceptSocketId(), true);
        }
        HibiraBroadcast a = AnnotatedElementUtils.findMergedAnnotation(payload.getClass(), HibiraBroadcast.class);
        if (a == null) {
            return null;
        }
        List<String> channels = new ArrayList<>(a.channels().length);
        SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().withRootObject(payload).build();
        for (String c : a.channels()) {
            if (!c.contains("#{")) {
                channels.add(c);
                continue;
            }
            Expression e = expressions.computeIfAbsent(c, k -> parser.parseExpression(k, template));
            channels.add(String.valueOf(e.getValue(ctx)));
        }
        String name = a.event().isEmpty() ? payload.getClass().getSimpleName() : a.event();
        return new Send(channels, name, payload, null, a.afterCommit());
    }

    private void deliver(Send s) {
        try {
            hibira.trigger(s.channels(), s.event(), s.data(), s.exceptSocketId());
        } catch (RuntimeException e) {
            log.warn("Hibira: could not send " + s.event() + " to " + s.channels() + ": " + e.getMessage(), e);
        }
    }

    record Send(List<String> channels, String event, Object data, String exceptSocketId, boolean afterCommit) {
    }

    private static final class Tx {
        static boolean active() {
            return org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()
                    && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
        }

        static void afterCommit(Runnable r) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            r.run();
                        }
                    });
        }
    }
}
