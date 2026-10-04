package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.SkillCooldownComponent;

/**
 * Ca XÁC ĐỊNH rút gọn từ những vi phạm mà {@link WorldSimulationPropertyTest} tìm ra (2026-10-04) —
 * mỗi ca là chuỗi lệnh ngắn nhất còn tái hiện, tick tay qua
 * {@link WorldFacade#runOneTickOnCallerThread()}.
 */
class WorldSimulationRegressionTest {

    private static final CharacterBaseStats STATS = new CharacterBaseStats(10, 10, 10, 10, Element.HOA, 1);
    private static final ComponentMapper<AttackCooldownComponent> ATTACK_COOLDOWN =
            ComponentMapper.getFor(AttackCooldownComponent.class);
    private static final ComponentMapper<SkillCooldownComponent> SKILL_COOLDOWN =
            ComponentMapper.getFor(SkillCooldownComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);

    private final InvariantCheckingSink sink = new InvariantCheckingSink();

    private WorldFacade newWorld(boolean overworld) {
        WorldFacade world = new WorldFacade(sink, overworld);
        sink.bind(world);
        return world;
    }

    private static void tick(WorldFacade world, int count) {
        for (int i = 0; i < count; i++) {
            world.runOneTickOnCallerThread();
        }
    }

    /**
     * Cooldown đánh thường bị trừ MỖI tick không chặn dưới — đứng yên một phút là {@code -60} giây,
     * trôi mãi về âm (vô hại về luật vì "sẵn sàng" là {@code ≤ ε}, nhưng component nói sai nghĩa
     * "còn bao nhiêu giây", và float trôi tới mức trừ không còn đổi giá trị sau ~6 ngày đứng yên).
     */
    @Test
    void shouldNeverLetCooldownsGoNegative_whenIdle() {
        WorldFacade world = newWorld(false);
        sink.onSessionStarted(1L);
        world.attachCharacter(1L, 0f, 0f, STATS);

        tick(world, 2);

        KEntity entity = world.characterEntitiesView().get(1L);
        assertThat(ATTACK_COOLDOWN.get(entity).remainingSeconds).isZero();
        assertThat(SKILL_COOLDOWN.get(entity).remainingSeconds).containsOnly(0f);
    }

    /**
     * Quái/boss đánh nhân vật ⇒ {@code handleCombatResults}/{@code handleBossResults} thêm "người
     * đánh" vào tập người nhận vô điều kiện, nên sink nhận {@code onAttackResult(-1, …)} — một
     * {@code targetCharacterId} ÂM, không phải client nào. Gateway tự bỏ qua id lạ nên không ai thấy
     * gì, nhưng hợp đồng {@link WorldOutboundSink} nói người nhận là NHÂN VẬT (seed 24301, DUNGEON).
     */
    @Test
    void shouldOnlyAddressCharacters_whenAMonsterHitsSomeone() {
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.withStandingSentry(true));
        sink.bind(world);
        sink.onSessionStarted(1L);
        world.attachCharacter(1L, 2f, 0f, STATS);
        tick(world, 1);
        long sentry = world.monsterEntitiesView().keySet().iterator().next();
        world.setTarget(1L, sentry);
        world.attack(1L); // con canh gác đánh trả (retaliates) ⇒ đòn của QUÁI lên nhân vật

        tick(world, 3 * 60);

        assertThat(sink.eventCounts()).containsKey("attackResult");
        assertThat(sink.violations()).isEmpty();
    }

    /**
     * {@code AreaOfInterestTracker.onDetach} duyệt TOÀN BỘ tập "đang thấy" của người rời đi — gồm cả
     * quái và NPC — và phát {@code onCharacterDespawned(quái, người rời)} cho từng id, tức "báo cho con
     * quái rằng nhân vật biến mất" (seed 24301, DUNGEON: rời world cạnh boss).
     */
    @Test
    void shouldNotTellMonstersOrNpcsThatACharacterLeft() {
        WorldFacade world = new WorldFacade(
                sink,
                true,
                TickMetricsPort.NO_OP,
                new WorldContentCatalog(
                        TestMonsterCatalogs.standingSentry(false),
                        TestWorldContents.townElderOnly(),
                        SafeZoneCatalogSnapshot.EMPTY));
        sink.bind(world);
        sink.onSessionStarted(1L);
        world.attachCharacter(1L, 0f, 0f, STATS); // thấy con canh gác (x=3) lẫn trưởng lão (x=-5)
        tick(world, WorldConstants.AOI_SYNC_INTERVAL_TICKS);
        assertThat(sink.seenBy(1L)).hasSize(2);

        world.detachCharacter(1L);
        tick(world, 1);

        assertThat(sink.violations()).isEmpty();
    }

    /**
     * {@code handleHealCharacter} cộng {@code current + amount} kiểu {@code int} — lượng hồi đủ lớn
     * tràn số thành ÂM, {@code min(max, âm)} giữ nguyên số âm: uống thuốc làm máu thành
     * {@code -2147483450} (seed 24303, OVERWORLD). Lượng hồi đến từ cấu hình vật phẩm qua gateway, không
     * từ client — nhưng world không được để một số cấu hình sai giết người chơi bằng cách hồi máu.
     */
    @Test
    void shouldCapHealingAtMaxHp_evenWhenTheAmountWouldOverflow() {
        WorldFacade world = new WorldFacade(sink, false);
        world.allowPlayerVersusPlayer();
        sink.bind(world);
        sink.onSessionStarted(1L);
        sink.onSessionStarted(2L);
        CharacterBaseStats sureHitter = new CharacterBaseStats(1, 100, 1, 1, Element.HOA, 1);
        CharacterBaseStats neverDodges = new CharacterBaseStats(1, 0, 1, 1, Element.HOA, 1);
        world.attachCharacter(new CharacterEntry(1L, 0f, 0f, neverDodges, new EquipmentBonusSnapshot(0, 0, 0, 1000)));
        world.attachCharacter(CharacterEntry.withoutEquipment(2L, 1f, 0f, sureHitter));
        world.setTarget(2L, 1L);
        world.attack(2L);
        tick(world, 1);
        KEntity victim = world.characterEntitiesView().get(1L);
        assertThat(HEALTH.get(victim).current).isLessThan(HEALTH.get(victim).max);

        world.enqueueHealCharacter(1L, Integer.MAX_VALUE);
        tick(world, 1);

        assertThat(HEALTH.get(victim).current).isEqualTo(HEALTH.get(victim).max);
        assertThat(sink.violations()).isEmpty();
    }

    /**
     * Đòn đánh thường/skill được GIẢI ngay lúc rút lệnh khỏi hàng đợi, nhưng kết quả chỉ PHÁT sau
     * {@code ecsWorld.update()}. Lệnh rời world của nạn nhân nằm SAU lệnh đánh trong cùng hàng đợi thì
     * được xử lý ở giữa: người xem nhận despawn của nạn nhân rồi mới nhận "đòn trúng nạn nhân", và
     * chính nạn nhân — đã rời — vẫn được gửi kết quả. Với gateway, đó là gói của phiên CŨ phát ra sau
     * khi {@code inboxBarrier} đã xong — đúng thứ mốc chặn ấy hứa chặn (seed 24302, ARENA).
     */
    @Test
    void shouldDeliverAHitBeforeTheVictimLeaves_whenBothAreQueuedInTheSameTick() {
        WorldFacade world = new WorldFacade(sink, false);
        world.allowPlayerVersusPlayer();
        sink.bind(world);
        sink.onSessionStarted(1L);
        sink.onSessionStarted(2L);
        sink.onSessionStarted(3L);
        world.attachCharacter(1L, 0f, 0f, new CharacterBaseStats(1, 0, 1, 1, Element.HOA, 1));
        world.attachCharacter(2L, 1f, 0f, new CharacterBaseStats(1, 100, 1, 1, Element.HOA, 1));
        world.attachCharacter(3L, 5f, 0f, STATS); // người đứng xem
        world.setTarget(2L, 1L);
        tick(world, WorldConstants.AOI_SYNC_INTERVAL_TICKS);

        world.attack(2L);
        world.detachCharacter(1L);
        tick(world, 1);

        assertThat(sink.eventCounts()).containsKey("attackResult");
        assertThat(sink.violations()).isEmpty();
    }

    /**
     * Cùng họ với ca trên, đường máu (ADR-046 D5 + sửa 2026-09-19): uống đan hoặc đổi trang bị rồi rời
     * world trong cùng tick ⇒ {@code HealthChangedEvent} của lệnh đó phát cho người ĐÃ rời (seed
     * 24301, DUNGEON).
     */
    @Test
    void shouldDeliverAHealthChangeBeforeTheCharacterLeaves_whenBothAreQueuedInTheSameTick() {
        WorldFacade world = new WorldFacade(sink, false);
        sink.bind(world);
        sink.onSessionStarted(1L);
        world.attachCharacter(1L, 0f, 0f, STATS);
        tick(world, 1);
        world.applyEquipmentBonus(1L, new EquipmentBonusSnapshot(0, 0, 0, 50)); // 100/100 → 150/150
        world.detachCharacter(1L);
        tick(world, 1);

        assertThat(sink.eventCounts()).containsKey("healthChanged");
        assertThat(sink.violations()).isEmpty();
    }

    /**
     * Skill và đánh thường đều giải lúc rút lệnh, nhưng kết quả phát theo LOẠI (đánh thường trước,
     * skill sau) chứ không theo thứ tự lệnh. Skill của A trúng con quái, rồi đòn thường của B hạ nó
     * trong cùng tick ⇒ người xem nhận despawn của con quái (từ đòn hạ gục) rồi mới nhận "skill của A
     * trúng nó" (seed 24315, OVERWORLD).
     */
    @Test
    void shouldEmitHitsInCommandOrder_whenASkillLandsBeforeTheKillingBlowInTheSameTick() {
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.withStandingSentry(false));
        sink.bind(world);
        sink.onSessionStarted(1L);
        sink.onSessionStarted(2L);
        world.attachCharacter(1L, 1f, 0f, STATS); // Hỏa — fireball tầm 5m
        world.attachCharacter(2L, 2f, 0f, new CharacterBaseStats(5000, 100, 1, 1, Element.HOA, 1));
        tick(world, WorldConstants.AOI_SYNC_INTERVAL_TICKS);
        long sentry = world.monsterEntitiesView().keySet().iterator().next();
        world.setTarget(2L, sentry);

        world.useSkill(1L, SkillConstants.FIREBALL_SKILL_CODE, sentry);
        world.attack(2L); // một đòn là chết
        tick(world, 1);

        assertThat(sink.eventCounts()).containsKeys("skillResult.DAMAGE", "monsterKilled");
        assertThat(sink.violations()).isEmpty();
    }

    /** Hết cooldown vẫn đánh được đúng lúc như trước — kẹp ở 0 không được làm chậm đòn kế. */
    @Test
    void shouldStillAttackOncePerInterval_afterClampingCooldown() {
        WorldFacade world =
                new WorldFacade(sink, true, TickMetricsPort.NO_OP, TestWorldContents.withStandingSentry(false));
        sink.bind(world);
        sink.onSessionStarted(1L);
        world.attachCharacter(1L, 2f, 0f, STATS); // con canh gác đứng ở x=3 — trong tầm đánh 2m
        tick(world, 1);
        long sentry = world.monsterEntitiesView().keySet().iterator().next();
        world.setTarget(1L, sentry);
        tick(world, 1);

        int attacks = 0;
        for (int i = 0; i < 4 * 60; i++) {
            world.attack(1L);
            tick(world, 1);
            attacks = sink.eventCounts().getOrDefault("attackResult", 0);
        }

        // 4 giây giữ nút đánh, chu kỳ 1 giây: đòn ở tick 0, 60, 120, 180 — đúng 4 đòn.
        assertThat(attacks).isEqualTo(4);
    }
}
