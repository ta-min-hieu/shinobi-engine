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
 * Nhắm một NHÂN VẬT khác ⇒ người nhắm nhận ngay máu hiện tại/tối đa của người đó (dòng 32, chủ dự
 * án duyệt 2026-10-18).
 *
 * <p>Trước đó HUD mục tiêu đấu trường đứng ở "máu / ?" cả trận: máu tối đa của người khác chỉ đi
 * trên {@code HealthChangedEvent}, mà World đấu trường không hồi máu nên gói đó không bao giờ tới.
 */
@Timeout(60)
class TargetHealthAnnouncedTest {

    private static final long SEEKER = 1L;
    private static final long TARGET = 2L;
    private static final long MONSTER_LIKE_ID = -1L;
    private static final CharacterBaseStats STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);

    private record HealthChanged(long observer, long entityId, int currentHp, int maxHp) {}

    private static final class RecordingSink implements WorldOutboundSink {
        final List<Long> spawnedTo = new CopyOnWriteArrayList<>();
        final List<HealthChanged> healthChanges = new CopyOnWriteArrayList<>();

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
            spawnedTo.add(targetCharacterId);
        }

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}

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
    void seekerShouldLearnTheTargetsMaxHp_asSoonAsItTargetsAnotherCharacter() {
        world.start();
        world.attachCharacter(SEEKER, 1f, 0f, STATS);
        world.attachCharacter(TARGET, 0f, 0f, STATS);
        awaitTrue(() -> sink.spawnedTo.contains(SEEKER) && sink.spawnedTo.contains(TARGET));

        world.setTarget(SEEKER, TARGET);

        awaitTrue(() -> sink.healthChanges.contains(
                new HealthChanged(SEEKER, TARGET, CombatConstants.CHARACTER_MAX_HP, CombatConstants.CHARACTER_MAX_HP)));
        assertThat(sink.healthChanges)
                .as("chỉ người nhắm được báo — người bị nhắm không nhận gói về chính mình vì việc này")
                .noneMatch(change -> change.observer() == TARGET);
    }

    @Test
    void nothingShouldBeAnnounced_whenTheTargetDoesNotExist() {
        world.start();
        world.attachCharacter(SEEKER, 1f, 0f, STATS);
        sleepQuietly(200);

        world.setTarget(SEEKER, 99L);
        world.setTarget(SEEKER, MONSTER_LIKE_ID);
        world.attack(SEEKER); // một lệnh sau cùng để chắc hai lệnh trên đã được tick rút ra
        sleepQuietly(200);

        assertThat(sink.healthChanges).isEmpty();
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
