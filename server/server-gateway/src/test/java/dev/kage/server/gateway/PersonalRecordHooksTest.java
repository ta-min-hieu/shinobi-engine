package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.character.api.PersonalRecordFacade;
import dev.kage.character.domain.PersonalRecordPort;
import dev.kage.protocol.ChatMessageEvent;

/** ADR-240 — theo dõi kỷ lục trong RAM: chỉ tính sau khi đã nạp, chuỗi về 0 khi bị hạ. */
class PersonalRecordHooksTest {

    /** Port trong bộ nhớ. */
    private static final class MemoryPort implements PersonalRecordPort {
        private final Map<Kind, Long> saved = new EnumMap<>(Kind.class);

        @Override
        public synchronized Map<Kind, Long> recordsOf(long characterId) {
            return Map.copyOf(saved);
        }

        @Override
        public synchronized boolean raise(long characterId, Kind kind, long value, Instant at) {
            if (saved.getOrDefault(kind, 0L) >= value) {
                return false;
            }
            saved.put(kind, value);
            return true;
        }
    }

    @Test
    void countsStreakOnlyAfterLoadAndResetsOnDeath() {
        PersonalRecordHooks hooks = new PersonalRecordHooks();
        hooks.onKill(7L);
        assertThat(hooks.currentStreak(7L)).isZero();

        hooks.bind(
                new PersonalRecordFacade(new MemoryPort()),
                new ConnectedCharacterRegistry(
                        GatewayPipelineTest.fakeInventoryFacade(),
                        null,
                        ExperienceGrantPort.disabled(),
                        new GatewayNetworkMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())));
        hooks.load(7L);
        hooks.onKill(7L);
        hooks.onKill(7L);
        assertThat(hooks.currentStreak(7L)).isEqualTo(2L);

        hooks.onDefeated(7L);
        assertThat(hooks.currentStreak(7L)).isZero();
    }

    @Test
    void noticeIsBilingual() throws InvalidProtocolBufferException {
        String body = ChatMessageEvent.parseFrom(PersonalRecordHooks.notice(PersonalRecordPort.Kind.HIGHEST_HIT, 321))
                .getBody();
        assertThat(body).contains("321").contains("Kỷ lục mới").contains("New record");
    }
}
