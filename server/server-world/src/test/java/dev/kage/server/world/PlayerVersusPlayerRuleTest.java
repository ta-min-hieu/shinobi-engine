package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tạm cấm PvP ngoài Arena (chủ dự án 2026-09-24). Thiết kế đã duyệt (STORY.md §7) là PvP THEO VÙNG —
 * mở ở Hoang Vực — nhưng code chưa có vùng đó, nên mọi World mặc định cấm và chỉ World đấu Arena bật
 * {@link WorldFacade#allowPlayerVersusPlayer()}. Trước bản vá, ở World chính một người chơi nhắm và
 * đánh được người chơi khác với sát thương thật.
 *
 * <p>Không có ô "kỹ năng diện rộng": mọi skill hiện có đều nhắm MỘT mục tiêu ({@link SkillTargetKind}
 * chỉ có ENEMY/SELF/ALLY) — khi thêm skill AoE, nó phải đi qua cùng cổng này.
 */
@Timeout(20)
class PlayerVersusPlayerRuleTest {

    private static final CharacterBaseStats FIRE = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    private static final CharacterBaseStats WATER = new CharacterBaseStats(10, 10, 10, 10, Element.THUY, 1);
    private static final long ATTACKER = 1L;
    private static final long VICTIM = 2L;
    private static final long SETTLE_MILLIS = 600L;

    private record Hit(long attacker, long target, String skillCode) {}

    private final RecordingSink sink = new RecordingSink();
    private WorldFacade world;

    @AfterEach
    void stopWorld() {
        if (world != null) {
            world.stop();
        }
    }

    @Test
    void shouldNotLetABasicAttackHitAnotherPlayer_outsideArena() {
        startWorld(false, FIRE);

        world.setTarget(ATTACKER, VICTIM);
        world.attack(ATTACKER);
        sleepQuietly(SETTLE_MILLIS);

        assertThat(hitsBy(ATTACKER)).isEmpty();
    }

    @Test
    void shouldNotLetADamageSkillHitAnotherPlayer_outsideArena() {
        startWorld(false, FIRE);

        world.useSkill(ATTACKER, SkillConstants.FIREBALL_SKILL_CODE, VICTIM);
        sleepQuietly(SETTLE_MILLIS);

        assertThat(hitsBy(ATTACKER)).isEmpty();
    }

    @Test
    void shouldNotLetADebuffSkillLandOnAnotherPlayer_outsideArena() {
        startWorld(false, WATER);

        world.useSkill(ATTACKER, "thuy_phong_toa", VICTIM);
        sleepQuietly(SETTLE_MILLIS);

        assertThat(hitsBy(ATTACKER)).isEmpty();
    }

    /** Đối chứng: hồi máu cho người chơi khác (ALLY) KHÔNG phải gây hại — vẫn phải chạy. */
    @Test
    void shouldStillLetAHealReachAnotherPlayer_outsideArena() {
        startWorld(false, WATER);

        world.useSkill(ATTACKER, "thuy_hoi_phuc", VICTIM);

        awaitTrue(() -> !hitsBy(ATTACKER).isEmpty());
        assertThat(hitsBy(ATTACKER)).allMatch(hit -> hit.target() == VICTIM);
    }

    /** Đối chứng: World đấu Arena vẫn cho đánh nhau — cả đòn thường lẫn skill. */
    @Test
    void shouldStillLetPlayersFight_insideAnArenaWorld() {
        startWorld(true, FIRE);

        world.setTarget(ATTACKER, VICTIM);
        world.attack(ATTACKER);
        world.useSkill(ATTACKER, SkillConstants.FIREBALL_SKILL_CODE, VICTIM);

        awaitTrue(
                () -> hitsBy(ATTACKER).stream().anyMatch(hit -> hit.skillCode().isEmpty()));
        awaitTrue(
                () -> hitsBy(ATTACKER).stream().anyMatch(hit -> !hit.skillCode().isEmpty()));
    }

    private void startWorld(boolean arena, CharacterBaseStats attackerStats) {
        world = new WorldFacade(sink, false);
        if (arena) {
            world.allowPlayerVersusPlayer();
        }
        world.start();
        world.attachCharacter(ATTACKER, 0f, 0f, attackerStats);
        world.attachCharacter(VICTIM, 0.5f, 0f, FIRE);
        sleepQuietly(100);
    }

    private List<Hit> hitsBy(long attacker) {
        return sink.hits.stream().filter(hit -> hit.attacker() == attacker).toList();
    }

    private static void awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Điều kiện không thành sau 10 giây");
            }
            sleepQuietly(10);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("bị ngắt khi chờ", e);
        }
    }

    /** Sink tối thiểu — chỉ ghi đòn đánh thường (skillCode rỗng) và kết quả skill. */
    private static final class RecordingSink implements WorldOutboundSink {
        final List<Hit> hits = new CopyOnWriteArrayList<>();

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
            hits.add(new Hit(outcome.attackerEntityId(), outcome.targetEntityId(), ""));
        }

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
            hits.add(new Hit(outcome.attackerEntityId(), outcome.targetEntityId(), outcome.skillCode()));
        }

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot snapshot) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {}

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
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {}

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}
    }
}
