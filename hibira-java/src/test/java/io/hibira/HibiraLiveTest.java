package io.hibira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs against a real server when HIBIRA_E2E_APP_ID, HIBIRA_E2E_KEY and
 * HIBIRA_E2E_SECRET are set (HIBIRA_E2E_HOST, HIBIRA_E2E_PORT and
 * HIBIRA_E2E_TLS=false for a local one).
 */
@EnabledIfEnvironmentVariable(named = "HIBIRA_E2E_SECRET", matches = ".+")
class HibiraLiveTest {

    private static Hibira hibira() {
        String port = System.getenv("HIBIRA_E2E_PORT");
        return HibiraClient.create(HibiraOptions.builder()
                .appId(System.getenv("HIBIRA_E2E_APP_ID"))
                .key(System.getenv("HIBIRA_E2E_KEY"))
                .secret(System.getenv("HIBIRA_E2E_SECRET"))
                .host(System.getenv().getOrDefault("HIBIRA_E2E_HOST", HibiraOptions.DEFAULT_HOST))
                .port(port == null || port.isEmpty() ? null : Integer.valueOf(port))
                .useTls(!"false".equals(System.getenv("HIBIRA_E2E_TLS")))
                .build());
    }

    @Test
    void triggersAndReadsChannels() {
        Hibira h = hibira();
        h.trigger("java-e2e", "hello", Map.of("from", "java"));
        h.triggerBatch(List.of(new BatchEvent("java-e2e", "a", 1), new BatchEvent("java-e2e", "b", "two")));
        h.sendToUser("java-e2e-user", "hello", "hi");
        assertThat(h.channels(null, null)).isNotNull();
        assertThat(h.channel("java-e2e", "subscription_count").occupied()).isFalse();
    }

    @Test
    void refusesABadSignature() {
        Hibira bad = HibiraClient.create(HibiraOptions.builder()
                .appId(System.getenv("HIBIRA_E2E_APP_ID")).key(System.getenv("HIBIRA_E2E_KEY")).secret("wrong-secret-0123456789")
                .host(hibira().options().host()).port(hibira().options().port()).useTls(hibira().options().useTls()).build());
        assertThatThrownBy(() -> bad.trigger("java-e2e", "x", 1))
                .isInstanceOf(HibiraException.class)
                .satisfies(e -> assertThat(((HibiraException) e).status()).isEqualTo(401));
    }

    @Test
    void sendsReadsMarksAndDeletesInboxNotifications() {
        Hibira h = hibira();
        String user = "java-e2e-" + System.nanoTime();
        InboxSendResult sent = h.sendInbox(InboxMessage.builder("Order #1042 shipped").to(user).body("Arriving Tuesday")
                .url("/orders/1042").key("order-1042").data(Map.of("order_id", 1042)).push(InboxMessage.PUSH_NEVER).build());
        assertThat(sent.notifications()).hasSize(1);
        assertThat(sent.notifications().get(0).isNew()).isTrue();
        String id = sent.notifications().get(0).id();

        InboxSendResult again = h.sendInbox(InboxMessage.builder("Order #1042 shipped").to(user).key("order-1042")
                .push(InboxMessage.PUSH_NEVER).build());
        assertThat(again.notifications().get(0).isNew()).as("same key").isFalse();

        InboxPage page = h.listInbox(user, null, 10, null);
        assertThat(page.unread()).isEqualTo(1);
        assertThat(page.notifications().get(0).data().get("order_id").asInt()).isEqualTo(1042);
        assertThat(page.notifications().get(0).createdAt()).isNotNull();

        assertThat(h.markInbox(user, List.of(id), true, null).unread()).isZero();
        assertThat(h.listInbox(user, "unread", 10, null).notifications()).isEmpty();
        assertThat(h.deleteInbox(user, id).ids()).containsExactly(id);
        assertThat(h.listInbox(user, "all", 10, null).notifications()).isEmpty();
    }
}
