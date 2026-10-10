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
 * Lên cấp GIỮA PHIÊN phải tới được luật trong world ({@link WorldFacade#enqueueCharacterLevel}).
 *
 * <p><b>Bug thật trước 2026-10-10.</b> {@code CharacterLevelComponent} chỉ được gắn lúc attach. Người
 * chơi lên Lv50 giữa phiên thì HUD báo Lv50 (cột {@code character.level} và
 * {@code ExperienceChangedEvent} đều đúng) nhưng {@code SkillSystem} vẫn thấy cấp lúc đăng nhập, nên
 * Tuyệt Kỹ bị từ chối ÂM THẦM cho tới khi đăng nhập lại.
 *
 * <p>Luật phụ thuộc cấp duy nhất trong world hiện nay là cổng Tuyệt Kỹ
 * ({@link SkillConstants#ULTIMATE_REQUIRED_LEVEL}) nên mọi ô ở đây đo bằng nó: có hay không một
 * {@code onSkillResult} mang mã Tuyệt Kỹ (trúng hay trượt đều là "đã được tung").
 *
 * <p>Lớp RIÊNG chứ không thêm vào {@link WorldFacadeTest}: lớp đó đã sát trần
 * {@code CyclomaticComplexity} của PMD — cùng lý do {@link EquipmentHealthSyncTest} tồn tại.
 */
@Timeout(30)
class CharacterLevelSyncTest {

    private static final long CHARACTER_ID = 1L;
    private static final long ABSENT_CHARACTER_ID = 99L;
    private static final String ULTIMATE = "hoa_diem_nguc";
    private static final int ONE_BELOW_GATE = SkillConstants.ULTIMATE_REQUIRED_LEVEL - 1;

    /** Cạnh quái mẫu của bãi tập — trong tầm Tuyệt Kỹ, y hệt {@code WorldFacadeTest}. */
    private static final float SPAWN_X = 5f;

    private static final float SPAWN_Y = 6f;

    private static final class RecordingSink implements WorldOutboundSink {
        final List<Long> monsterIds = new CopyOnWriteArrayList<>();
        final List<String> castSkillCodes = new CopyOnWriteArrayList<>();

        @Override
        public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {
            monsterIds.add(monster.monsterId());
        }

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
            castSkillCodes.add(outcome.skillCode());
        }

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot accepted) {}

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {}

        @Override
        public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {}

        @Override
        public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {}

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
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}
    }

    private final RecordingSink sink = new RecordingSink();
    private final WorldFacade world =
            new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());

    @AfterEach
    void stopWorld() {
        world.stop();
    }

    @Test
    void shouldLetUltimateThrough_onceLevelIsRaisedMidSession() {
        long monsterId = attachBesideMonster(ONE_BELOW_GATE);

        assertThat(castUltimate(monsterId))
                .as("Lv%s lúc attach: Tuyệt Kỹ phải bị từ chối", ONE_BELOW_GATE)
                .isFalse();

        world.enqueueCharacterLevel(CHARACTER_ID, SkillConstants.ULTIMATE_REQUIRED_LEVEL);

        assertThat(castUltimate(monsterId))
                .as("vừa lên Lv%s giữa phiên: Tuyệt Kỹ phải tung được ngay", SkillConstants.ULTIMATE_REQUIRED_LEVEL)
                .isTrue();
    }

    @Test
    void shouldLetUltimateThrough_whenSeveralLevelsAreGainedAtOnce() {
        long monsterId = attachBesideMonster(1);

        world.enqueueCharacterLevel(CHARACTER_ID, SkillConstants.ULTIMATE_REQUIRED_LEVEL + 7);

        assertThat(castUltimate(monsterId)).isTrue();
    }

    /** Hai lượt cộng EXP chạy trên hai luồng: lệnh mang cấp CŨ hơn có thể tới sau. */
    @Test
    void shouldNeverLowerLevel_whenAnOlderCommandArrivesLate() {
        long monsterId = attachBesideMonster(ONE_BELOW_GATE);

        world.enqueueCharacterLevel(CHARACTER_ID, SkillConstants.ULTIMATE_REQUIRED_LEVEL);
        world.enqueueCharacterLevel(CHARACTER_ID, ONE_BELOW_GATE);
        world.enqueueCharacterLevel(CHARACTER_ID, 0);
        world.enqueueCharacterLevel(CHARACTER_ID, Integer.MIN_VALUE);

        assertThat(castUltimate(monsterId)).isTrue();
    }

    @Test
    void shouldStayBelowGate_whenRaisedLevelIsStillTooLow() {
        long monsterId = attachBesideMonster(1);

        world.enqueueCharacterLevel(CHARACTER_ID, ONE_BELOW_GATE);

        assertThat(castUltimate(monsterId)).isFalse();
    }

    /**
     * Lệnh tới khi nhân vật không ở World này (đã ngắt kết nối, hoặc chưa từng vào): bỏ qua êm, không
     * để lại gì cho lần attach sau — lần đó đọc cấp từ DB.
     */
    @Test
    void shouldIgnoreCommand_whenCharacterIsNotInThisWorld() {
        long monsterId = attachBesideMonster(1);
        world.detachCharacter(CHARACTER_ID);

        world.enqueueCharacterLevel(CHARACTER_ID, SkillConstants.ULTIMATE_REQUIRED_LEVEL);
        world.enqueueCharacterLevel(ABSENT_CHARACTER_ID, SkillConstants.ULTIMATE_REQUIRED_LEVEL);
        world.attachCharacter(CHARACTER_ID, SPAWN_X, SPAWN_Y, statsAtLevel(1));

        assertThat(castUltimate(monsterId))
                .as("cấp nâng lúc vắng mặt không được bám vào lần attach sau")
                .isFalse();
        // World vẫn tick và vẫn nhận lệnh cho đúng nhân vật đó sau hai lệnh bị bỏ qua.
        world.enqueueCharacterLevel(CHARACTER_ID, SkillConstants.ULTIMATE_REQUIRED_LEVEL);
        assertThat(castUltimate(monsterId)).isTrue();
    }

    private long attachBesideMonster(int level) {
        world.start();
        world.attachCharacter(CHARACTER_ID, SPAWN_X, SPAWN_Y, statsAtLevel(level));
        awaitTrue(() -> !sink.monsterIds.isEmpty());
        return sink.monsterIds.get(0);
    }

    private static CharacterBaseStats statsAtLevel(int level) {
        return new CharacterBaseStats(10, 10, 10, 10, Element.HOA, level);
    }

    /**
     * Gửi Tuyệt Kỹ rồi chờ tick thread xử lý XONG lệnh đó.
     *
     * @return {@code true} nếu world đã tung nó (trúng hay trượt đều tính)
     */
    private boolean castUltimate(long monsterId) {
        world.useSkill(CHARACTER_ID, ULTIMATE, monsterId);
        // Mốc thứ nhất: lệnh đã ra khỏi hàng đợi. Mốc thứ hai nằm ở lượt tick SAU, nên trọn lượt tick
        // chứa lệnh — kể cả phần phát kết quả — đã chạy xong.
        world.inboxBarrier().join();
        world.inboxBarrier().join();
        return sink.castSkillCodes.contains(ULTIMATE);
    }

    private static void awaitTrue(Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted khi chờ điều kiện tick loop", e);
            }
        }
        throw new AssertionError("Điều kiện không đúng sau 5s chờ tick loop");
    }
}
