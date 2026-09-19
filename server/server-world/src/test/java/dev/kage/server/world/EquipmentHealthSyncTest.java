package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Đổi trang bị làm đổi MÁU TỐI ĐA thì client phải biết cả máu HIỆN TẠI mới.
 *
 * <p><b>Bug thật, nhìn thấy được, sửa 2026-09-19.</b> {@code WorldFacade.applyMaxHpBonus} dịch máu
 * hiện tại theo đúng độ chênh của máu tối đa (ADR-036/ADR-043: mặc đồ +HP không phải nút hồi máu,
 * gỡ đồ không được giết người chơi) — nhưng world KHÔNG nói con số mới ra ngoài, và
 * {@code EquipItemResponse} chỉ chở {@code max_hp}. Client
 * ({@code CombatHudController.onOwnMaxHpChanged}) đổi mỗi MẪU SỐ rồi {@code min(hp, max)}:
 *
 * <ul>
 *   <li>đang 50/100, MẶC bộ +106 máu ⇒ server 156/206, HUD vẽ <b>50/206</b> — thiếu 106;
 *   <li>đang 156/206, GỠ bộ đó ra ⇒ server 50/100, HUD vẽ <b>100/100</b> — người chơi tưởng mình
 *       đầy máu ngay trước trận.
 * </ul>
 *
 * <p>Sửa ở {@code server-world} chứ không ở gateway: {@code applyEquipmentBonus} chỉ xếp lệnh vào
 * {@code inbox} nên gateway không đọc được kết quả trong cùng lời gọi. Đường ống
 * {@code HealthChangedEvent} (ADR-046 D1) vốn đã là nơi DUY NHẤT máu đi ra ngoài world và client đã
 * đọc được nó từ trước — không opcode mới, không field mới.
 *
 * <p><b>Vì sao là lớp RIÊNG.</b> Hai ô này sinh ra trong {@link WorldFacadeTest} và đẩy lớp đó lên
 * {@code CyclomaticComplexity} 82 / trần 80 của PMD — đúng cái bẫy đã tách {@link SafeZoneShieldTest}
 * ra trước đó. Cách sửa đã thành luật của dự án: tách theo VÙNG NGHIỆP VỤ.
 *
 * <p>Sink riêng, chỉ ba tín hiệu lớp này thật sự hỏi ({@code onCharacterSpawned},
 * {@code onAttackResult}, {@code onHealthChanged}); phần còn lại của {@link WorldOutboundSink} là
 * no-op — cùng lựa chọn đã giải thích ở {@link SafeZoneShieldTest}.
 */
@Timeout(60)
class EquipmentHealthSyncTest {

    private static final CharacterBaseStats DEFAULT_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);

    /** Máu tối đa mà bộ đồ thử nghiệm cộng thêm. Đủ lớn để một đòn đánh không che mất độ chênh. */
    private static final int BONUS_HP = 58;

    private record AttackResult(long defender, boolean miss, int hp, boolean died) {}

    private record HealthChanged(long target, long entityId, int currentHp, int maxHp) {}

    private static final class RecordingSink implements WorldOutboundSink {
        final List<Long> spawned = new CopyOnWriteArrayList<>();
        final List<AttackResult> attackResults = new CopyOnWriteArrayList<>();
        final List<HealthChanged> healthChanges = new CopyOnWriteArrayList<>();

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
            spawned.add(spawnedCharacterId);
        }

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
            attackResults.add(new AttackResult(
                    outcome.targetEntityId(), outcome.miss(), outcome.targetCurrentHp(), outcome.targetDied()));
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
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {}

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}
    }

    @Test
    void shouldAnnounceShiftedHealth_whenEquipmentChangesMaxHp() {
        // Phải đánh cho THƯƠNG trước mới đo được: đủ máu thì phần dịch lên bị chặn ở max và hai bên
        // vẫn khớp, tức test sẽ XANH trên đúng cái bug nó phải bắt.
        RecordingSink sink = new RecordingSink();
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        try {
            world.start();
            world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
            world.attachCharacter(2L, 1f, 0f, DEFAULT_STATS);
            awaitTrue(() -> sink.spawned.size() >= 2);

            targetAndAttackUntil(world, sink, 2L);
            int wounded = woundedHpOf(sink, 2L);
            assertThat(wounded).isLessThan(CombatConstants.CHARACTER_MAX_HP);

            world.applyEquipmentBonus(2L, new EquipmentBonusSnapshot(0, 0, 0, BONUS_HP));
            awaitTrue(() -> maxHpOfLastChange(sink, 2L) == CombatConstants.CHARACTER_MAX_HP + BONUS_HP);
            assertThat(lastHealthChangeOf(sink, 2L).currentHp()).isEqualTo(wounded + BONUS_HP);

            // Gỡ ra = bonus về 0. Máu hiện tại phải TỤT lại đúng ngần ấy, và client phải được báo.
            world.applyEquipmentBonus(2L, new EquipmentBonusSnapshot(0, 0, 0, 0));
            awaitTrue(() -> maxHpOfLastChange(sink, 2L) == CombatConstants.CHARACTER_MAX_HP);
            assertThat(lastHealthChangeOf(sink, 2L).currentHp()).isEqualTo(wounded);
        } finally {
            world.stop();
        }
    }

    @Test
    void shouldStaySilent_whenEquipmentBonusLeavesHealthUnchanged() {
        // Nửa còn lại: chỉ phát khi máu THỰC SỰ đổi. +ATK/+DEF/+RES không đụng tới máu, và mỗi gói
        // thừa là đúng thứ ADR-042 đang muốn bớt.
        //
        // ⚠️ HAI nhân vật dù chỉ đo một: luồng của sink chạy theo NGƯỜI QUAN SÁT, và bản đầu của ô
        // này attach đúng một nhân vật rồi chờ `spawned` — chờ tới hết giờ. Cùng cái bẫy đã ghi cho
        // `onCharacterMoved`.
        RecordingSink sink = new RecordingSink();
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        try {
            world.start();
            world.attachCharacter(1L, 0f, 0f, DEFAULT_STATS);
            world.attachCharacter(2L, 1f, 0f, DEFAULT_STATS);
            awaitTrue(() -> sink.spawned.size() >= 2);

            world.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(200, 30, 30, 0));
            // Lệnh thứ hai đi SAU trên cùng hàng đợi: nó xong thì lệnh trên chắc chắn cũng xong.
            world.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(0, 0, 0, BONUS_HP));
            awaitTrue(() -> lastHealthChangeOf(sink, 1L) != null);

            // Đếm bản giao cho CHÍNH MÌNH: mỗi lần phát đẻ một dòng cho mỗi người quan sát, nên đếm
            // thẳng `healthChanges` sẽ ra 2 và nói sai về SỐ LẦN phát.
            assertThat(sink.healthChanges.stream().filter(h -> h.entityId() == 1L && h.target() == 1L))
                    .hasSize(1);
            assertThat(lastHealthChangeOf(sink, 1L).maxHp()).isEqualTo(CombatConstants.CHARACTER_MAX_HP + BONUS_HP);
        } finally {
            world.stop();
        }
    }

    /** Máu còn lại của {@code characterId} theo đòn SẠCH gần nhất, hoặc 0 nếu chưa trúng đòn nào. */
    private static int woundedHpOf(RecordingSink sink, long characterId) {
        return sink.attackResults.stream()
                .filter(a -> a.defender() == characterId && !a.miss() && !a.died())
                .reduce((first, second) -> second)
                .map(AttackResult::hp)
                .orElse(0);
    }

    private static HealthChanged lastHealthChangeOf(RecordingSink sink, long characterId) {
        return sink.healthChanges.stream()
                .filter(h -> h.entityId() == characterId)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    /** {@code -1} khi chưa có gói nào — để điều kiện chờ không phải kiểm null hai lần. */
    private static int maxHpOfLastChange(RecordingSink sink, long characterId) {
        HealthChanged last = lastHealthChangeOf(sink, characterId);
        return last == null ? -1 : last.maxHp();
    }

    /** Bấm đánh tới khi mục tiêu dính một đòn SẠCH chưa chết — cùng nhịp poll với {@link #awaitTrue}. */
    private static void targetAndAttackUntil(WorldFacade world, RecordingSink sink, long targetCharacterId) {
        world.setTarget(1L, targetCharacterId);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (woundedHpOf(sink, targetCharacterId) > 0) {
                return;
            }
            world.attack(1L);
            sleepQuietly();
        }
        throw new AssertionError("Không đòn sạch nào trúng " + targetCharacterId + " sau 5 giây vừa chờ vừa bấm");
    }

    private static void awaitTrue(Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            sleepQuietly();
        }
        throw new AssertionError("Điều kiện không đúng sau 5 giây chờ tick loop");
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted khi chờ tick loop", e);
        }
    }
}
