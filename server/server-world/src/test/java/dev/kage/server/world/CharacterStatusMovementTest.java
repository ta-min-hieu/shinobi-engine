package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Hiệu ứng trạng thái phải tác động lên một nhân vật ĐANG CHẠY, không chỉ lên lệnh di chuyển kế tiếp
 * (sửa 2026-10-02).
 *
 * <p>Client chỉ gửi {@code MoveCommand} khi HƯỚNG đổi ({@code LocalIntentSender}), còn server trước
 * đây chỉ tính vận tốc nhân vật lúc nhận lệnh đó. Hệ quả: bị STUN giữa lúc giữ phím thì vẫn chạy
 * nguyên tốc độ suốt cơn choáng; bị SLOW giữa lúc chạy thì không chậm lại; buff tăng tốc hết hạn mà
 * vẫn giữ phím thì tốc độ được buff giữ MÃI. Quái không dính lỗi này vì {@code AiSystem} tính lại vận
 * tốc mỗi tick.
 *
 * <p>Đo vị trí bằng ack của {@code JumpCommand}: ack mang toạ độ thật, và nhảy không đụng tới vận tốc
 * ngang — khác {@code MoveCommand}, gửi lại nó là tự tay sửa đúng cái lỗi đang đo. World ở đây là kiểu
 * hầm ngục ({@code spawnOverworldContent=false}): không quái, không bệ nhảy chen vào phép đo.
 */
@Timeout(60)
class CharacterStatusMovementTest {

    private static final CharacterBaseStats LOI_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.LOI, 1);
    private static final CharacterBaseStats THUY_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.THUY, 1);
    private static final CharacterBaseStats PHONG_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.PHONG, 1);
    private static final CharacterBaseStats HOA_STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);

    private record Accepted(long characterId, int sequenceId, float x) {}

    private record SkillResult(String skillCode, long target, SkillEffectKind kind) {}

    private record Modifier(long characterId, float multiplier, boolean immobilized) {}

    private static final class RecordingSink implements WorldOutboundSink {
        final List<Accepted> accepted = new CopyOnWriteArrayList<>();
        final List<SkillResult> skillResults = new CopyOnWriteArrayList<>();
        final List<Long> spawnedTo = new CopyOnWriteArrayList<>();
        final List<Long> defeated = new CopyOnWriteArrayList<>();
        final List<Long> respawned = new CopyOnWriteArrayList<>();
        final List<Modifier> modifiers = new CopyOnWriteArrayList<>();

        @Override
        public void onMovementModifierChanged(long characterId, float statusSpeedMultiplier, boolean immobilized) {
            modifiers.add(new Modifier(characterId, statusSpeedMultiplier, immobilized));
        }

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot snapshot) {
            accepted.add(new Accepted(snapshot.characterId(), snapshot.sequenceId(), snapshot.x()));
        }

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
            skillResults.add(new SkillResult(outcome.skillCode(), outcome.targetEntityId(), outcome.effectKind()));
        }

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
            spawnedTo.add(targetCharacterId);
        }

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
            defeated.add(defeatedCharacterId);
        }

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {
            respawned.add(respawn.characterId());
        }

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
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {}
    }

    private final RecordingSink sink = new RecordingSink();
    private final AtomicInteger sequence = new AtomicInteger(1000);
    private WorldFacade world =
            new WorldFacade(sink, false, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());

    @AfterEach
    void stopWorld() {
        world.stop();
    }

    /** ADR-070 — phi kiếm +35% ⇒ ~6,75 m/s thay vì 5 m/s; và nó sống qua lần áp lại chỉ số trang bị. */
    @Test
    void flyingSwordShouldSpeedUpRunning_untilItIsTakenOff() {
        world.start();
        world.attachCharacter(new CharacterEntry(
                1L,
                0f,
                0f,
                HOA_STATS,
                new EquipmentBonusSnapshot(0, 0, 0, 0, EquipmentBonusSnapshot.Extras.speed(35)),
                SkillRankSnapshot.NONE));
        positionOf(1L);
        world.enqueueMoveCommand(1L, 1, 1f, 0f);

        sleepQuietly(300);
        float from = positionOf(1L);
        sleepQuietly(1000);
        float mounted = positionOf(1L) - from;
        world.applyEquipmentBonus(1L, EquipmentBonusSnapshot.NONE);
        sleepQuietly(300);
        float plainFrom = positionOf(1L);
        sleepQuietly(1000);
        float plain = positionOf(1L) - plainFrom;

        assertThat(mounted).isGreaterThan(6f);
        assertThat(plain).isLessThan(5.6f);
    }

    @Test
    void shouldStopARunningCharacter_whenItIsStunnedMidRun() {
        world.allowPlayerVersusPlayer();
        world.start();
        world.attachCharacter(1L, 0f, 0f, LOI_STATS);
        world.attachCharacter(2L, 3f, 0f, HOA_STATS);
        awaitTrue(() -> sink.spawnedTo.contains(1L) && sink.spawnedTo.contains(2L));

        world.enqueueMoveCommand(2L, 1, -1f, 0f); // giữ phím trái — client không gửi thêm lệnh nào
        world.useSkill(1L, "loi_dien_giat", 2L); // stun 2,5 giây
        awaitTrue(() -> hasSkillResult("loi_dien_giat", 2L, SkillEffectKind.DEBUFF));

        float stunnedFrom = positionOf(2L);
        sleepQuietly(1000);
        float stunnedTo = positionOf(2L);

        assertThat(Math.abs(stunnedTo - stunnedFrom)).isLessThan(0.5f); // không sửa: ~5 m

        // Hết choáng mà vẫn giữ phím ⇒ chạy tiếp, không bắt người chơi nhả ra bấm lại.
        sleepQuietly(2000);
        float resumedFrom = positionOf(2L);
        sleepQuietly(500);
        assertThat(resumedFrom - positionOf(2L)).isGreaterThan(1f);
    }

    /**
     * ADR-073: người bị choáng được báo "không đi được" lúc trúng và "đi lại được" lúc hết — đúng hai lần, chỉ cho
     * chính họ; người ra đòn không nhận gì.
     */
    @Test
    void shouldTellOnlyTheStunnedCharacter_whenTheStunLandsAndWhenItEnds() {
        world.allowPlayerVersusPlayer();
        world.start();
        world.attachCharacter(1L, 0f, 0f, LOI_STATS);
        world.attachCharacter(2L, 3f, 0f, HOA_STATS);
        awaitTrue(() -> sink.spawnedTo.contains(1L) && sink.spawnedTo.contains(2L));

        world.useSkill(1L, "loi_dien_giat", 2L); // stun 2,5 giây
        awaitTrue(() -> sink.modifiers.contains(new Modifier(2L, 1f, true)));
        awaitTrue(() -> sink.modifiers.contains(new Modifier(2L, 1f, false)));

        assertThat(sink.modifiers).containsExactly(new Modifier(2L, 1f, true), new Modifier(2L, 1f, false));
    }

    @Test
    void shouldSlowARunningCharacter_whenTheSlowLandsMidRun() {
        world.allowPlayerVersusPlayer();
        world.start();
        world.attachCharacter(1L, 0f, 0f, THUY_STATS);
        world.attachCharacter(2L, 3f, 0f, HOA_STATS);
        awaitTrue(() -> sink.spawnedTo.contains(1L) && sink.spawnedTo.contains(2L));

        world.enqueueMoveCommand(2L, 1, -1f, 0f);
        world.useSkill(1L, "thuy_phong_toa", 2L); // -40% trong 4 giây
        awaitTrue(() -> hasSkillResult("thuy_phong_toa", 2L, SkillEffectKind.DEBUFF));

        float from = positionOf(2L);
        sleepQuietly(1000);

        assertThat(from - positionOf(2L)).isLessThan(4f); // 3 m/s khi bị slow; không sửa: 5 m
    }

    @Test
    void shouldDropBackToNormalSpeed_whenAHasteBuffExpiresWhileStillRunning() {
        world.start();
        world.attachCharacter(1L, 0f, 0f, PHONG_STATS);
        positionOf(1L); // chờ attach xong — lệnh trong inbox chạy đúng thứ tự gửi

        world.useSkill(1L, "phong_gia_toc", 1L); // +50% trong 6 giây
        awaitTrue(() -> hasSkillResult("phong_gia_toc", 1L, SkillEffectKind.BUFF));
        world.enqueueMoveCommand(1L, 1, 1f, 0f);

        sleepQuietly(6500); // buff đã hết, phím vẫn giữ
        float from = positionOf(1L);
        sleepQuietly(1000);

        assertThat(positionOf(1L) - from).isLessThan(6.25f); // 5 m/s; không sửa: 7,5 m
    }

    /**
     * Chết rồi hồi sinh phải sạch mọi hiệu ứng — trước đây {@code respawnCharacter} hồi đầy máu/Khí
     * nhưng để nguyên slot {@code StatusEffectComponent}: hồi sinh vẫn còn slow/stun/khiên cũ.
     */
    @Test
    void shouldClearStatusEffects_whenACharacterRespawns() {
        world.stop();
        world = new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.trainingGroundWithElder());
        world.allowPlayerVersusPlayer();
        world.start();
        world.attachCharacter(1L, 0f, 0f, THUY_STATS);
        world.attachCharacter(2L, 1f, 0f, HOA_STATS);
        awaitTrue(() -> sink.spawnedTo.contains(1L) && sink.spawnedTo.contains(2L));
        world.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(1000, 0, 0, 0)); // 1 đòn trúng là chết

        world.useSkill(1L, "thuy_phong_toa", 2L); // -40% trong 4 giây
        awaitTrue(() -> hasSkillResult("thuy_phong_toa", 2L, SkillEffectKind.DEBUFF));
        world.setTarget(1L, 2L);
        // 20 giây chứ không phải 3 (sửa 2026-10-11): mỗi nhát thường trượt ~10% (hit 0,91 - dodge 0,015)
        // và cooldown là 1 giây, nên 3 giây chỉ đủ 2-3 nhát — máy tải mất một nhát là test đỏ oan.
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (!sink.respawned.contains(2L) && Instant.now().isBefore(deadline)) {
            world.attack(1L);
            sleepQuietly(50);
        }
        assertThat(sink.respawned).contains(2L);

        world.enqueueMoveCommand(2L, 2, 1f, 0f);
        float from = positionOf(2L);
        sleepQuietly(500);

        assertThat(positionOf(2L) - from).isGreaterThan(2f); // 5 m/s × 0,5 s; còn slow: 1,5 m
    }

    private boolean hasSkillResult(String skillCode, long target, SkillEffectKind kind) {
        return sink.skillResults.stream()
                .anyMatch(r -> skillCode.equals(r.skillCode()) && r.target() == target && r.kind() == kind);
    }

    /** Toạ độ x thật của nhân vật, đọc qua ack của một {@code JumpCommand}. */
    private float positionOf(long characterId) {
        int sequenceId = sequence.incrementAndGet();
        world.enqueueJumpCommand(characterId, sequenceId);
        awaitTrue(() ->
                sink.accepted.stream().anyMatch(a -> a.characterId() == characterId && a.sequenceId() == sequenceId));
        return sink.accepted.stream()
                .filter(a -> a.characterId() == characterId && a.sequenceId() == sequenceId)
                .findFirst()
                .orElseThrow()
                .x();
    }

    private static void awaitTrue(Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            sleepQuietly(10);
        }
        throw new AssertionError("Điều kiện không đúng sau 5 giây chờ tick loop");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted khi chờ tick loop chạy thật", e);
        }
    }
}
