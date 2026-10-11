package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Hai luật chủ dự án chốt 2026-10-11, đo trên một World đấu trường thật (luồng tick thật):
 *
 * <ul>
 *   <li><b>README dòng 36(i) — hai người gục cùng tick là HOÀ.</b> Trường hợp đó hiện KHÔNG THỂ xảy
 *       ra: sát thương trừ ngay lúc giải đòn và người đã gục không ra đòn được, nên trong một tick
 *       chỉ một người ngã. Ô test đầu ghim đúng bất biến ấy. Ngày thêm một nguồn sát thương trễ
 *       (độc, phản đòn, chiêu bay) thì ô này đỏ, và đó là lúc phải cài luật hoà ở
 *       {@code ArenaInstanceRegistry}.
 *   <li><b>README dòng 37(iii) — thoát ra vào lại không xoá hồi chiêu.</b>
 * </ul>
 */
@Timeout(30)
class ArenaAndRelogRuleTest {

    /** Sức mạnh đủ để một đòn thường hạ gục đối thủ 100 máu. */
    private static final CharacterBaseStats ONE_HIT_KILLER = new CharacterBaseStats(2000, 10, 10, 10, Element.HOA, 1);

    private static final CharacterBaseStats FIRE = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    private static final long FIRST = 1L;
    private static final long SECOND = 2L;
    private static final long SETTLE_MILLIS = 600L;

    private final RecordingSink sink = new RecordingSink();
    private WorldFacade world;

    @AfterEach
    void stopWorld() {
        if (world != null) {
            world.stop();
        }
    }

    @Test
    void shouldNeverDefeatBothFighters_whenBothStrikeLethallyAtTheSameTime() {
        startArena(SkillCooldownLedger.forgetful(), ONE_HIT_KILLER);
        world.setTarget(FIRST, SECOND);
        world.setTarget(SECOND, FIRST);

        while (sink.defeated.isEmpty()) {
            // Hai lệnh xếp liền nhau ⇒ cùng một tick. Đòn có thể hụt (né), nên lặp tới khi có người ngã.
            world.attack(FIRST);
            world.attack(SECOND);
            sleepQuietly(50);
        }
        sleepQuietly(SETTLE_MILLIS);

        assertThat(sink.defeated).hasSize(1);
    }

    @Test
    void shouldKeepASkillOnCooldown_afterLeavingAndReenteringTheWorld() {
        startArena(new SkillCooldownLedger(), FIRE);
        castFireballAndRelog();

        world.useSkill(FIRST, SkillConstants.FIREBALL_SKILL_CODE, SECOND);
        sleepQuietly(SETTLE_MILLIS);

        assertThat(sink.skillCasts).as("chiêu vẫn đang hồi sau khi vào lại").hasSize(1);
    }

    /** Đối chứng: World không được trao sổ thì vào lại là dùng được ngay — đúng hành vi cũ. */
    @Test
    void shouldResetTheCooldownOnReentry_whenTheWorldHasNoLedger() {
        startArena(SkillCooldownLedger.forgetful(), FIRE);
        castFireballAndRelog();

        world.useSkill(FIRST, SkillConstants.FIREBALL_SKILL_CODE, SECOND);

        awaitTrue(() -> sink.skillCasts.size() == 2);
    }

    private void castFireballAndRelog() {
        world.useSkill(FIRST, SkillConstants.FIREBALL_SKILL_CODE, SECOND);
        awaitTrue(() -> sink.skillCasts.size() == 1);
        world.detachCharacter(FIRST);
        world.attachCharacter(FIRST, 0f, 0f, FIRE);
        sleepQuietly(150);
    }

    private void startArena(SkillCooldownLedger ledger, CharacterBaseStats stats) {
        world = new WorldFacade(sink, false);
        world.allowPlayerVersusPlayer();
        world.rememberSkillCooldownsIn(ledger);
        world.start();
        world.attachCharacter(FIRST, 0f, 0f, stats);
        world.attachCharacter(SECOND, 0.5f, 0f, stats);
        sleepQuietly(100);
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

    /** Sink tối thiểu — ghi ai gục và mỗi lần một chiêu được thi triển (theo người thi triển). */
    private static final class RecordingSink implements WorldOutboundSink {
        final List<Long> defeated = new CopyOnWriteArrayList<>();
        final List<String> skillCasts = new CopyOnWriteArrayList<>();

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
            defeated.add(defeatedCharacterId);
        }

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
            // Mỗi kết quả được phát tới MỌI người đang thấy — chỉ đếm bản gửi cho chính người thi triển.
            if (targetCharacterId == outcome.attackerEntityId()) {
                skillCasts.add(outcome.skillCode());
            }
        }

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {}

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot snapshot) {}

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
