package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatMessageEvent;

/** Kiểm tra sẵn sàng (ADR-263), đếm ngược (ADR-264), xúc xắc (ADR-265) — phần thuần, không hẹn giờ. */
class PartySignalsTest {

    private final List<String> sent = new ArrayList<>();
    private final PartySignals.Outbox outbox = (id, event) -> sent.add(id + ":" + body(event));

    private static String body(byte[] event) {
        try {
            return ChatMessageEvent.parseFrom(event).getBody();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void readyCheckResolvesOnceEveryoneAnswered() {
        PartySignals signals = new PartySignals(() -> 0L, false);
        assertThat(signals.startReadyCheck(
                        9L, 1L, new PartySignals.Audience(List.of(1L, 2L, 3L), id -> "P" + id, outbox)))
                .isEqualTo(PartySignals.StartOutcome.OK);
        assertThat(sent).hasSize(3);
        assertThat(signals.startReadyCheck(9L, 1L, new PartySignals.Audience(List.of(1L), id -> "P" + id, outbox)))
                .isEqualTo(PartySignals.StartOutcome.ALREADY_ACTIVE);

        assertThat(signals.respondReadyCheck(9L, 4L, true)).isFalse(); // không có mặt lúc mở
        assertThat(signals.respondReadyCheck(9L, 2L, true)).isTrue();
        assertThat(signals.hasReadyCheck(9L)).isTrue();
        sent.clear();
        assertThat(signals.respondReadyCheck(9L, 3L, true)).isTrue();
        assertThat(signals.hasReadyCheck(9L)).isFalse();
        assertThat(sent)
                .containsExactly(
                        "1:Cả nhóm đã sẵn sàng! / Everyone is ready!",
                        "2:Cả nhóm đã sẵn sàng! / Everyone is ready!",
                        "3:Cả nhóm đã sẵn sàng! / Everyone is ready!");
        assertThat(signals.respondReadyCheck(9L, 2L, false)).isFalse();
    }

    @Test
    void timeoutListsSilentMembers() {
        PartySignals signals = new PartySignals(() -> 0L, false);
        signals.startReadyCheck(5L, 1L, new PartySignals.Audience(List.of(1L, 2L, 3L), id -> "P" + id, outbox));
        signals.respondReadyCheck(5L, 2L, false);
        Object handle = signals.readyCheckHandle(5L).orElseThrow();
        sent.clear();
        signals.resolveReadyCheck(5L, handle);
        signals.resolveReadyCheck(5L, handle); // chốt hai lần chỉ báo một lần
        assertThat(sent).hasSize(3);
        assertThat(sent.getFirst()).contains("sẵn sàng: P1; chưa: P2; không trả lời: P3");
    }

    @Test
    void summaryAllReady() {
        Map<Long, PartySignals.Answer> answers = new LinkedHashMap<>();
        answers.put(1L, PartySignals.Answer.READY);
        assertThat(PartySignals.summary(answers, id -> "A")).startsWith("Cả nhóm");
    }

    @Test
    void countdownBoundsAndText() {
        assertThat(PartySignals.validCountdown(2)).isFalse();
        assertThat(PartySignals.validCountdown(3)).isTrue();
        assertThat(PartySignals.validCountdown(10)).isTrue();
        assertThat(PartySignals.validCountdown(11)).isFalse();
        assertThat(PartySignals.countdownText(3)).isEqualTo("3…");
        assertThat(PartySignals.countdownText(0)).isEqualTo("Bắt đầu! / Go!");
    }

    @Test
    void diceRangeAndCooldown() {
        assertThat(PartySignals.diceMax(0)).contains(100);
        assertThat(PartySignals.diceMax(1)).isEmpty();
        assertThat(PartySignals.diceMax(1001)).isEmpty();
        AtomicLong clock = new AtomicLong(10_000L);
        PartySignals signals = new PartySignals(clock::get, false);
        for (int i = 0; i < 50; i++) {
            clock.addAndGet(PartySignals.DICE_COOLDOWN_MILLIS);
            assertThat(signals.rollDice(1L, 6))
                    .hasValueSatisfying(v -> assertThat(v).isBetween(1, 6));
        }
        assertThat(signals.rollDice(1L, 6)).isEmpty(); // ngay sau lần gieo trước
        assertThat(signals.rollDice(2L, 6)).isPresent(); // người khác không bị ảnh hưởng
    }
}
