package io.hibira.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hibira.Hibira;
import io.hibira.HibiraClient;
import io.hibira.HibiraOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Hibira: the {@link Hibira} client from
 * {@code hibira.*}, {@link HibiraChannel} authorization, the endpoints
 * (Spring MVC or WebFlux) and {@link HibiraBroadcast} events.
 */
@AutoConfiguration
@EnableConfigurationProperties(HibiraProperties.class)
public class HibiraAutoConfiguration {

    /** The client, from {@code hibira.*}. Event data is serialized with the application's Jackson 2 ObjectMapper when it has one. */
    @Bean
    @ConditionalOnMissingBean
    public Hibira hibira(HibiraProperties p, ObjectProvider<ObjectMapper> objectMapper) {
        HibiraOptions options = HibiraOptions.builder()
                .appId(p.getAppId())
                .key(p.getKey())
                .secret(p.getSecret())
                .host(p.getHost())
                .port(p.getPort())
                .useTls(p.isUseTls())
                .timeout(p.getTimeout())
                .pushInstanceId(p.getPush().getInstanceId())
                .pushSecretKey(p.getPush().getSecretKey())
                .pushEndpoint(p.getPush().getEndpoint())
                .build();
        if (options.appId().isEmpty() || options.key().isEmpty() || options.secret().isEmpty()) {
            throw new IllegalStateException("Hibira: set hibira.app-id, hibira.key and hibira.secret (for example the "
                    + "HIBIRA_APP_ID, HIBIRA_KEY and HIBIRA_SECRET environment variables). Copy them from your app page on "
                    + "https://hibira.io/portal.");
        }
        return HibiraClient.create(options, null, objectMapper.getIfUnique());
    }

    /** The {@link HibiraChannel} methods. */
    @Bean
    @ConditionalOnMissingBean
    public static HibiraChannelRegistry hibiraChannelRegistry(ConfigurableListableBeanFactory beanFactory) {
        return new HibiraChannelRegistry(beanFactory);
    }

    /** The default user: the principal's name as the ID. */
    @Bean
    @ConditionalOnMissingBean
    public HibiraUserResolver hibiraUserResolver() {
        return principal -> HibiraUser.of(principal.getName());
    }

    /** What the endpoints do. */
    @Bean
    @ConditionalOnMissingBean
    public HibiraEndpointHandler hibiraEndpointHandler(Hibira hibira, HibiraChannelRegistry channels, HibiraUserResolver users,
            ApplicationEventPublisher events, HibiraProperties properties) {
        return new HibiraEndpointHandler(hibira, channels, users, events, properties);
    }

    /** Sends {@link HibiraBroadcast} application events. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hibira.broadcast", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HibiraBroadcaster hibiraBroadcaster(Hibira hibira) {
        return new HibiraBroadcaster(hibira);
    }

    /** The endpoints on Spring MVC. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = {"org.springframework.web.servlet.DispatcherServlet", "jakarta.servlet.http.HttpServletRequest"})
    @ConditionalOnProperty(prefix = "hibira.endpoints", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ServletEndpointsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        HibiraServletEndpoints hibiraServletEndpoints(HibiraEndpointHandler handler) {
            return new HibiraServletEndpoints(handler);
        }
    }

    /** The endpoints on Spring WebFlux. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(name = "org.springframework.web.reactive.DispatcherHandler")
    @ConditionalOnProperty(prefix = "hibira.endpoints", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ReactiveEndpointsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        HibiraReactiveEndpoints hibiraReactiveEndpoints(HibiraEndpointHandler handler) {
            return new HibiraReactiveEndpoints(handler);
        }
    }

    /** Waiting rooms on Spring MVC, when {@code hibira.waiting-room.rooms} is set. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnProperty(prefix = "hibira.waiting-room", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ServletWaitingRoomConfiguration {

        @Bean
        @ConditionalOnMissingBean
        HibiraWaitingRoomServletFilter hibiraWaitingRoomFilter(HibiraProperties properties) {
            return new HibiraWaitingRoomServletFilter(new HibiraWaitingRooms(properties));
        }
    }

    /** Waiting rooms on Spring WebFlux, when {@code hibira.waiting-room.rooms} is set. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
    @ConditionalOnProperty(prefix = "hibira.waiting-room", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ReactiveWaitingRoomConfiguration {

        @Bean
        @ConditionalOnMissingBean
        HibiraWaitingRoomWebFilter hibiraWaitingRoomWebFilter(HibiraProperties properties) {
            return new HibiraWaitingRoomWebFilter(new HibiraWaitingRooms(properties));
        }
    }
}
