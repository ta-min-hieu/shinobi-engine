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
 * Đổi hệ phái GIỮA PHIÊN phải tới được luật chiến đấu trong world
 * ({@link WorldFacade#enqueueCharacterElement}, quyết định chủ dự án 2026-10-11).
 *
 * <p><b>Trước đó.</b> Chỉ ngoại hình đổi (ADR-045). {@code CombatStatsComponent} giữ nguyên tố và
 * passive của hệ lúc đăng nhập, nên người chơi thấy mình đã sang Thuỷ mà world vẫn chỉ cho tung chiêu
 * Hoả, vẫn tính khắc chế theo Hoả, tới khi đăng nhập lại.
 *
 * <p>Đo bằng cổng hệ phái của {@code SkillSystem}: có hay không một {@code onSkillResult} mang mã
 * chiêu (trúng hay trượt đều là "đã được tung"). Lớp RIÊNG, cùng lý do {@link CharacterLevelSyncTest}.
 */
@Timeout(30)
class CharacterElementSyncTest {

    private static final long CHARACTER_ID = 1L;
    private static final long ABSENT_CHARACTER_ID = 99L;
    private static final String FIRE_SKILL = "hoa_tia_lua";
    private static final String WATER_SKILL = "thuy_phong_toa";

    /** Cạnh quái mẫu của bãi tập — trong tầm chiêu, y hệt {@code CharacterLevelSyncTest}. */
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
    void shouldFollowTheNewFactionAtOnce_whenItChangesMidSession() {
        long monsterId = attachBesideMonster(Element.HOA);
        assertThat(cast(WATER_SKILL, monsterId))
                .as("đang hệ Hoả: chiêu Thuỷ phải bị từ chối")
                .isFalse();

        world.enqueueCharacterElement(CHARACTER_ID, Element.THUY);

        assertThat(cast(WATER_SKILL, monsterId))
                .as("vừa sang hệ Thuỷ giữa phiên: chiêu Thuỷ phải tung được ngay")
                .isTrue();
        assertThat(cast(FIRE_SKILL, monsterId))
                .as("đã rời hệ Hoả: chiêu Hoả không còn tung được")
                .isFalse();
    }

    /** Lệnh lặp cùng hệ, và lệnh cho nhân vật không ở World này, đều không làm gì — và không làm hỏng tick. */
    @Test
    void shouldIgnoreRepeatedAndAbsentCommands() {
        long monsterId = attachBesideMonster(Element.HOA);

        world.enqueueCharacterElement(CHARACTER_ID, Element.HOA);
        world.enqueueCharacterElement(ABSENT_CHARACTER_ID, Element.THUY);

        assertThat(cast(WATER_SKILL, monsterId)).isFalse();
        assertThat(cast(FIRE_SKILL, monsterId)).isTrue();
    }

    /** Hệ đổi lúc vắng mặt không được bám vào lần attach sau — lần đó đọc hệ từ DB. */
    @Test
    void shouldNotLeakIntoTheNextAttach_whenTheCharacterHasLeft() {
        long monsterId = attachBesideMonster(Element.HOA);
        world.detachCharacter(CHARACTER_ID);

        world.enqueueCharacterElement(CHARACTER_ID, Element.THUY);
        world.attachCharacter(CHARACTER_ID, SPAWN_X, SPAWN_Y, statsOf(Element.HOA));

        assertThat(cast(WATER_SKILL, monsterId)).isFalse();
    }

    private long attachBesideMonster(Element element) {
        world.start();
        world.attachCharacter(CHARACTER_ID, SPAWN_X, SPAWN_Y, statsOf(element));
        awaitTrue(() -> !sink.monsterIds.isEmpty());
        return sink.monsterIds.get(0);
    }

    private static CharacterBaseStats statsOf(Element element) {
        return new CharacterBaseStats(10, 10, 10, 10, element, 1);
    }

    /**
     * Gửi chiêu rồi chờ tick thread xử lý XONG lệnh đó (hai mốc chặn, xem {@code CharacterLevelSyncTest}).
     *
     * @return {@code true} nếu world đã tung nó (trúng hay trượt đều tính)
     */
    private boolean cast(String skillCode, long monsterId) {
        sink.castSkillCodes.clear();
        world.useSkill(CHARACTER_ID, skillCode, monsterId);
        world.inboxBarrier().join();
        world.inboxBarrier().join();
        return sink.castSkillCodes.contains(skillCode);
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
