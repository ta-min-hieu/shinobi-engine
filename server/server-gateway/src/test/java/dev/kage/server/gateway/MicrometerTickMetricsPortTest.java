package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.server.world.TickMetricsPort;
import dev.kage.server.world.WorldFacade;
import dev.kage.server.world.WorldOutboundSink;

/**
 * {@code SYSTEM_NAMES} của {@link MicrometerTickMetricsPort} là một bản sao thủ công của "danh
 * sách System mà {@code WorldFacade} constructor thật sự đăng ký" — đúng hình dạng bug đã bắt
 * được nhiều lần hôm nay (hai sự thật lẽ ra phải giống nhau, chỉ nối bằng một dòng comment
 * "SYSTEM_NAMES phải khớp WorldFacade", không có gì ép compiler/test).
 *
 * <p><b>Đã lệch THẬT ở lúc viết ô này (2026-09-19).</b> {@code SYSTEM_NAMES} chỉ có 6 tên
 * ({@code AiSystem}/{@code MovementSystem}/{@code CombatSystem}/{@code SkillSystem}/
 * {@code BossAiSystem}/{@code StatusEffectSystem}) trong khi {@code WorldFacade} constructor đăng
 * ký THÊM 4 System: {@code VerticalMovementSystem} (ADR-033), {@code TerrainCollisionSystem}
 * (PHYSICS.md §4), {@code TriggerZoneSystem} (ADR-050) và {@code HealthRegenSystem} (ADR-046) —
 * cả bốn thêm SAU LẦN cuối ai đó nhìn lại {@code SYSTEM_NAMES}. Hệ quả không phải crash (
 * {@link MicrometerTickMetricsPort#recordSystemDuration} có nhánh {@code timer == null} trả về êm
 * re) mà là {@code kage.ecs.system.duration} ÂM THẦM thiếu 4/10 System — đúng loại lỗi không ai
 * nhận ra qua log hay test cũ vì không gì hỏng cả, chỉ có SỐ ĐO bị bỏ sót.
 *
 * <p>Ô dưới đây KHÔNG hardcode lại danh sách 10 tên đó (làm vậy chỉ tạo thêm một bản sao thứ ba đi
 * lệch riêng nó) — thay vào đó tick một {@code WorldFacade} THẬT một lần, tự bắt danh sách tên
 * System THẬT NGAY LÚC CHẠY qua chính {@link TickMetricsPort#recordSystemDuration}, rồi lấy đúng
 * danh sách đó nạp vào một {@link MicrometerTickMetricsPort} thật để hỏi registry có Timer hay
 * không. Thêm/bớt System ở {@code WorldFacade} về sau tự động được ô này theo dõi, không cần ai
 * sửa lại test.
 */
class MicrometerTickMetricsPortTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void shouldRecordDurationForAKnownSystemName() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTickMetricsPort port = new MicrometerTickMetricsPort(registry, "test");

        port.recordSystemDuration("CombatSystem", 1_000_000L);

        Timer timer = registry.get("kage.ecs.system.duration")
                .tag("system", "CombatSystem")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @Timeout(15)
    void shouldRecordDurationForEverySystemThatARealWorldFacadeActuallyTicks() {
        Set<String> realSystemNames = ConcurrentHashMap.newKeySet();
        TickMetricsPort spy = new TickMetricsPort() {
            @Override
            public void recordTickDuration(long durationNanos) {}

            @Override
            public void recordTickOverrun() {}

            @Override
            public void recordSystemDuration(String systemName, long durationNanos) {
                realSystemNames.add(systemName);
            }

            @Override
            public void recordEntityCount(int count) {}
        };
        WorldFacade world = new WorldFacade(new NoOpOutboundSink(), true, spy);
        try {
            world.start();
            awaitTrue(() -> realSystemNames.size() >= 11);
        } finally {
            world.stop();
        }

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerTickMetricsPort port = new MicrometerTickMetricsPort(registry, "test");
        for (String systemName : realSystemNames) {
            port.recordSystemDuration(systemName, 1_000L);
        }

        for (String systemName : realSystemNames) {
            Timer timer = registry.find("kage.ecs.system.duration")
                    .tag("system", systemName)
                    .timer();
            assertThat(timer)
                    .as(
                            "System '%s' WorldFacade đăng ký thật nhưng SYSTEM_NAMES của"
                                    + " MicrometerTickMetricsPort không biết tới — kage.ecs.system.duration"
                                    + " âm thầm bỏ sót nó",
                            systemName)
                    .isNotNull();
            assertThat(timer.count())
                    .as("Timer của '%s' tồn tại nhưng chưa từng ghi số đo nào", systemName)
                    .isEqualTo(1);
        }
    }

    private static void awaitTrue(Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted khi chờ WorldFacade tick", e);
            }
        }
        throw new AssertionError("WorldFacade không tick đủ System sau " + AWAIT_TIMEOUT + " chờ");
    }

    /** Không quan tâm output — ô này chỉ cần World tick thật để biết System nào THẬT SỰ chạy. */
    private static final class NoOpOutboundSink implements WorldOutboundSink {

        @Override
        public void onMoveAccepted(MoveAcceptedSnapshot accepted) {}

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {}

        @Override
        public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {}

        @Override
        public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {}

        @Override
        public void onMonsterSpawned(long targetCharacterId, MonsterSnapshot monster) {}

        @Override
        public void onMonsterDespawned(long targetCharacterId, long monsterId) {}

        @Override
        public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {}

        @Override
        public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {}

        @Override
        public void onNpcSpawned(long targetCharacterId, NpcSnapshot npc) {}

        @Override
        public void onNpcDespawned(long targetCharacterId, long npcId) {}

        @Override
        public void onAttackResult(long targetCharacterId, AttackOutcome outcome) {}

        @Override
        public void onSkillResult(long targetCharacterId, SkillOutcome outcome) {}

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onHealthChanged(long targetCharacterId, HealthSnapshot health) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

        @Override
        public void onCharacterRespawned(RespawnSnapshot respawn) {}
    }
}
