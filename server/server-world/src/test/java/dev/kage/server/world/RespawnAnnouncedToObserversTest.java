package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Người ĐANG NHÌN một nhân vật phải được báo khi nhân vật đó hồi sinh (quyết định chủ dự án
 * 2026-10-11).
 *
 * <p>Trước đó {@code onCharacterRespawned} chỉ tới chính người hồi sinh. Client người quan sát suy ra
 * hồi sinh từ gói di chuyển đầu tiên sau khi chết — nên người gục ngay tại điểm hồi sinh rồi đứng yên
 * (không gói di chuyển nào) nằm tư thế chết mãi trên máy họ. Nay mỗi lần hồi sinh phát thêm một
 * {@code HealthChangedEvent} mang máu đầy qua đường ống sẵn có.
 *
 * <p>Kịch bản cố ý đặt nạn nhân ĐÚNG điểm hồi sinh {@code (0, 0)}: đó là ca không có tín hiệu nào khác.
 */
@Timeout(60)
class RespawnAnnouncedToObserversTest {

    private static final long KILLER = 1L;
    private static final long VICTIM = 2L;
    private static final CharacterBaseStats STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);

    private record HealthChanged(long observer, long entityId, int currentHp, int maxHp) {}

    private static final class RecordingSink implements WorldOutboundSink {
        final List<Long> spawnedTo = new CopyOnWriteArrayList<>();
        final List<Long> respawned = new CopyOnWriteArrayList<>();
        final List<HealthChanged> healthChanges = new CopyOnWriteArrayList<>();

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
            spawnedTo.add(targetCharacterId);
        }

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {
            respawned.add(respawn.characterId());
        }

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {
            healthChanges.add(
                    new HealthChanged(targetCharacterId, health.entityId(), health.currentHp(), health.maxHp()));
        }

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot accepted) {}

        @Override
        public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {}

        @Override
        public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {}

        @Override
        public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {}

        @Override
        public void onMonsterDespawned(long targetCharacterId, long monsterId) {}

        @Override
        public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {}

        @Override
        public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {}

        @Override
        public void onNpcSpawned(long targetCharacterId, WorldOutboundSink.NpcSnapshot npc) {}

        @Override
        public void onNpcDespawned(long targetCharacterId, long npcId) {}

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {}

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {}

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}
    }

    private final RecordingSink sink = new RecordingSink();
    private final WorldFacade world =
            new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());

    @AfterEach
    void stopWorld() {
        world.stop();
    }

    @Test
    void observerShouldBeToldTheVictimIsAliveAgain_evenWhenItDiedOnTheRespawnPoint() {
        world.allowPlayerVersusPlayer();
        world.start();
        world.attachCharacter(KILLER, 1f, 0f, STATS);
        world.attachCharacter(VICTIM, 0f, 0f, STATS); // đúng điểm hồi sinh — hồi sinh không đổi vị trí
        awaitTrue(() -> sink.spawnedTo.contains(KILLER) && sink.spawnedTo.contains(VICTIM));
        world.applyEquipmentBonus(KILLER, new EquipmentBonusSnapshot(1000, 0, 0, 0)); // một đòn trúng là hạ
        world.setTarget(KILLER, VICTIM);

        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (!sink.respawned.contains(VICTIM) && Instant.now().isBefore(deadline)) {
            world.attack(KILLER);
            sleepQuietly(50);
        }
        assertThat(sink.respawned).contains(VICTIM);

        awaitTrue(() -> sink.healthChanges.stream().anyMatch(RespawnAnnouncedToObserversTest::tellsKillerVictimIsFull));
    }

    /** Gói gửi cho KILLER (người quan sát), về VICTIM, mang máu đầy. */
    private static boolean tellsKillerVictimIsFull(HealthChanged change) {
        return change.observer() == KILLER
                && change.entityId() == VICTIM
                && change.currentHp() == CombatConstants.CHARACTER_MAX_HP
                && change.maxHp() == CombatConstants.CHARACTER_MAX_HP;
    }

    private static void awaitTrue(Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            sleepQuietly(10);
        }
        throw new AssertionError("Điều kiện không đúng sau 5s chờ tick loop");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted khi chờ tick loop", e);
        }
    }
}
