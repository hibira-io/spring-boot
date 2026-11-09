package io.hibira;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaitingRoomTest {

    // Signed by the Hibira server (internal/waitroom NewPass) at 1800000000, ttl 30 min.
    static final String SECRET = "wr-secret-0123456789";
    static final String PASS = "eyJ2IjoxLCJhcHAiOiJ3ci1hcHAiLCJyb29tIjoicHJlc2FsZSIsIm4iOjQyLCJ0aWQiOiJkNzlmZTc4OWEzOGI4NjMxYjUiLCJpYXQiOjE4MDAwMDAw"
            + "MDAsImV4cCI6MTgwMDAwMTgwMH0.q7Uc_O6XKwNA_LVLQITlgv_YtDi9dIwiRzUUUSsGa08";
    static final String TICKET = "eyJ2IjoxLCJhcHAiOiJ3ci1hcHAiLCJyb29tIjoicHJlc2FsZSIsIm4iOjQyLCJ0aWQiOiJkNzlmZTc4OWEzOGI4NjMxYjUiLCJpYXQiOjE4MDAwMDAw"
            + "MDAsImV4cCI6MTgwMDA4NjQwMH0.l-KNMOVwV_xJJCQaQ-JXQKsrj-XlEdsTxG7X4Kp_2ck";
    static final Instant NOW = Instant.ofEpochSecond(1800000100);

    static String sign(String json, String secret) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        byte[] sig = HibiraSignature.hmacBytes(secret, ("hibira-waitroom-pass." + payload).getBytes(StandardCharsets.UTF_8));
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    @Test
    void verifiesAServerPass() {
        WaitingRoom.Pass p = WaitingRoom.verifyPass(PASS, List.of(SECRET), "presale", "wr-app", NOW);
        assertThat(p).isEqualTo(new WaitingRoom.Pass("wr-app", "presale", 42, "d79fe789a38b8631b5", 1800000000, 1800001800));
        assertThat(WaitingRoom.verifyPass(PASS, List.of("old", SECRET), null, null, NOW)).isNotNull();
    }

    @Test
    void rejectsForgedExpiredForeignAndTickets() {
        List<String> s = List.of(SECRET);
        assertThat(WaitingRoom.verifyPass(PASS, List.of("wrong"), null, null, NOW)).isNull();
        assertThat(WaitingRoom.verifyPass(PASS, s, "other", null, NOW)).isNull();
        assertThat(WaitingRoom.verifyPass(PASS, s, null, "other", NOW)).isNull();
        assertThat(WaitingRoom.verifyPass(PASS, s, null, null, Instant.ofEpochSecond(1800001800))).isNull();
        assertThat(WaitingRoom.verifyPass(TICKET, s, null, null, NOW)).isNull();
        assertThat(WaitingRoom.verifyPass(PASS.substring(0, PASS.length() - 3) + "AAA", s, null, null, NOW)).isNull();
        for (String bad : new String[] {"", null, "a.b.c", "!!.??", "a."}) {
            assertThat(WaitingRoom.verifyPass(bad, s, null, null, NOW)).isNull();
        }
        assertThat(WaitingRoom.verifyPass(sign("{\"v\":2,\"exp\":9999999999}", SECRET), s, null, null, NOW)).isNull();
    }

    @Test
    void decides() {
        var o = new WaitingRoom.Options("wr-app", "wr-key", List.of(SECRET), "presale", "https://ws.example.com", null, null);
        var none = WaitingRoom.decide("https://shop.example.com/drop?id=1", "GET", null, o, NOW);
        assertThat(none.location()).isEqualTo("https://ws.example.com/wait/wr-key/presale?return=https%3A%2F%2Fshop.example.com%2Fdrop%3Fid%3D1");
        assertThat(WaitingRoom.decide("https://shop.example.com/drop", "POST", null, o, NOW).action()).isEqualTo(WaitingRoom.Action.DENY);

        var back = WaitingRoom.decide("https://shop.example.com/drop?id=1&hibira_pass=" + PASS, "GET", null, o, NOW);
        assertThat(back.location()).isEqualTo("https://shop.example.com/drop?id=1");
        assertThat(back.setCookie()).isEqualTo("hibira_wr_presale=" + PASS + "; Path=/; Max-Age=1700; HttpOnly; SameSite=Lax; Secure");

        var in = WaitingRoom.decide("https://shop.example.com/drop", "POST", PASS, o, NOW);
        assertThat(in.allowed()).isTrue();
        assertThat(in.pass().number()).isEqualTo(42);

        var bad = WaitingRoom.decide("http://shop.example.com/drop?hibira_pass=x.y", "GET", null, o, NOW);
        assertThat(bad.location()).isEqualTo("https://ws.example.com/wait/wr-key/presale?return=http%3A%2F%2Fshop.example.com%2Fdrop");
    }
}
