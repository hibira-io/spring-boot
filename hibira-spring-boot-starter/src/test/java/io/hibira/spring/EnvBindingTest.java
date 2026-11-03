package io.hibira.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hibira.Hibira;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class EnvBindingTest {

    @Test
    void bindsTheDocumentedEnvironmentVariables() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HibiraAutoConfiguration.class))
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("HIBIRA_APP_ID", "3", "HIBIRA_KEY", "k", "HIBIRA_SECRET", "s",
                                "HIBIRA_PUSH_INSTANCE_ID", "inst", "HIBIRA_PUSH_SECRET_KEY", "ps"))))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    Hibira h = ctx.getBean(Hibira.class);
                    assertThat(h.options().appId()).isEqualTo("3");
                    assertThat(h.options().pushInstanceId()).isEqualTo("inst");
                    assertThat(h.options().pushSecretKey()).isEqualTo("ps");
                });
    }
}
