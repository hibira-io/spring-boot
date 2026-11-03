package io.hibira.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hibira.Hibira;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class HibiraAutoConfigurationTest {

    static final String[] CREDENTIALS = {"hibira.app-id=3", "hibira.key=k", "hibira.secret=s3cr3t"};

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HibiraAutoConfiguration.class));

    @Test
    void createsTheClientFromProperties() {
        runner.withPropertyValues(CREDENTIALS)
                .withPropertyValues("hibira.host=ws-ap2.hibira.io", "hibira.push.instance-id=i", "hibira.push.secret-key=p")
                .run(ctx -> {
                    Hibira h = ctx.getBean(Hibira.class);
                    assertThat(h.options().appId()).isEqualTo("3");
                    assertThat(h.options().baseUri().toString()).isEqualTo("https://ws-ap2.hibira.io");
                    assertThat(h.options().pushConfigured()).isTrue();
                    assertThat(ctx).hasSingleBean(HibiraBroadcaster.class).hasSingleBean(HibiraChannelRegistry.class)
                            .doesNotHaveBean(HibiraServletEndpoints.class).doesNotHaveBean(HibiraReactiveEndpoints.class);
                });
    }

    @Test
    void failsClearlyWithoutCredentials() {
        runner.run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause().hasMessageContaining("hibira.app-id"));
    }

    @Test
    void addsTheEndpointsForTheWebStack() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(HibiraAutoConfiguration.class))
                .withPropertyValues(CREDENTIALS)
                .run(ctx -> assertThat(ctx).hasSingleBean(HibiraServletEndpoints.class).doesNotHaveBean(HibiraReactiveEndpoints.class));
        new ReactiveWebApplicationContextRunner().withConfiguration(AutoConfigurations.of(HibiraAutoConfiguration.class))
                .withPropertyValues(CREDENTIALS)
                .run(ctx -> assertThat(ctx).hasSingleBean(HibiraReactiveEndpoints.class).doesNotHaveBean(HibiraServletEndpoints.class));
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(HibiraAutoConfiguration.class))
                .withPropertyValues(CREDENTIALS).withPropertyValues("hibira.endpoints.enabled=false", "hibira.broadcast.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(HibiraServletEndpoints.class).doesNotHaveBean(HibiraBroadcaster.class));
    }

    @Test
    void refusesBadChannelMethodsAtStartup() {
        runner.withPropertyValues(CREDENTIALS).withBean(BadChannels.class)
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().hasMessageContaining("is not a placeholder"));
    }

    static class BadChannels {
        @HibiraChannel("private-x.{id}")
        boolean x(int other) {
            return true;
        }
    }

    @Test
    void sendsBroadcastsAfterTheTransactionCommits() {
        List<List<Object>> calls = new CopyOnWriteArrayList<>();
        runner.withPropertyValues(CREDENTIALS)
                .withBean(Hibira.class, () -> TestSupport.recordingHibira(calls))
                .run(ctx -> {
                    ApplicationEventPublisher events = ctx;
                    TransactionTemplate tx = new TransactionTemplate(new NoopTransactionManager());
                    tx.executeWithoutResult(s -> {
                        events.publishEvent(new TestSupport.OrderShipped(1, "DHL"));
                        assertThat(calls).as("not before the commit").isEmpty();
                    });
                    assertThat(calls).hasSize(1);

                    tx.executeWithoutResult(s -> {
                        events.publishEvent(new TestSupport.OrderShipped(2, "DHL"));
                        s.setRollbackOnly();
                    });
                    assertThat(calls).as("not after a rollback").hasSize(1);

                    events.publishEvent(new TestSupport.OrderShipped(3, "DHL"));
                    assertThat(calls).as("at once outside a transaction").hasSize(2);
                });
    }

    @SuppressWarnings("serial")
    static class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
