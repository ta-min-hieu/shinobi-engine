package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.HealthComponent;

/**
 * ADR-046 D2 — hồi máu ngoài chiến đấu. Test THẲNG {@link HealthRegenSystem} với thời gian mô phỏng
 * (60 tick = 1 giây) chứ không qua tick loop thật: đợi 8 giây thời gian THẬT trong một test là vừa
 * chậm vừa flaky, mà thứ cần verify ở đây là công thức, không phải lịch tick (khuôn
 * {@link CombatSystemTest}).
 *
 * <p>Con số 118/38 lấy từ playtest thật 2026-09-06: nhân vật cấp 6 mặc trọn bộ "Tạp Dịch" có 118
 * máu tối đa, và hạ xong con thạch vệ ĐẦU của quest 3 thì còn đúng 38 — chính tình huống đã buộc
 * ADR-046 ra đời (trận thứ hai là chết chắc).
 */
class HealthRegenSystemTest {

    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);

    private static final long CHARACTER_ID = 1L;
    private static final int MAX_HP = 118;
    private static final int HP_AFTER_FIRST_FIGHT = 38;
    private static final float TICK_SECONDS = 1f / 60f;

    private KEntity buildCharacter(EcsWorld world, int currentHp, float secondsSinceCombat) {
        KEntity entity = world.createEntity();
        HealthComponent health = new HealthComponent();
        health.current = currentHp;
        health.max = MAX_HP;
        entity.add(health);
        CombatRecencyComponent recency = new CombatRecencyComponent();
        recency.secondsSinceCombat = secondsSinceCombat;
        entity.add(recency);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = CHARACTER_ID;
        entity.add(id);
        return entity;
    }

    /** Chạy {@code seconds} giây mô phỏng, gom mọi {@link HealthChange} phát ra dọc đường. */
    private List<HealthChange> run(EcsWorld world, HealthRegenSystem system, float seconds) {
        List<HealthChange> emitted = new ArrayList<>();
        int ticks = Math.round(seconds / TICK_SECONDS);
        for (int tick = 0; tick < ticks; tick++) {
            world.update(TICK_SECONDS);
            emitted.addAll(system.drainResults());
        }
        return emitted;
    }

    @Test
    void shouldNotRegen_whileStillInsideTheOutOfCombatDelay() {
        // Vừa đánh nhau xong (secondsSinceCombat = 0). Bảy giây sau vẫn chưa được hồi giọt nào —
        // nếu không có cửa sổ chờ này thì máu tự đầy GIỮA trận và mọi trận đấu mất hết ý nghĩa.
        EcsWorld world = new EcsWorld();
        KEntity character = buildCharacter(world, HP_AFTER_FIRST_FIGHT, 0f);
        HealthRegenSystem system = new HealthRegenSystem();
        world.addSystem(system);

        List<HealthChange> emitted = run(world, system, CombatConstants.REGEN_DELAY_SECONDS - 1f);

        assertThat(emitted).isEmpty();
        assertThat(health(character).current).isEqualTo(HP_AFTER_FIRST_FIGHT);
    }

    @Test
    void shouldRegenBackToFull_afterTheDelay() {
        EcsWorld world = new EcsWorld();
        KEntity character = buildCharacter(world, HP_AFTER_FIRST_FIGHT, 0f);
        HealthRegenSystem system = new HealthRegenSystem();
        world.addSystem(system);

        // 8s chờ + 80 máu ÷ (3% × 118 = 3,54 máu/giây) ≈ 22,6s hồi. Cho 35s để không phụ thuộc
        // vào chỗ làm tròn phần lẻ ở tick cuối.
        List<HealthChange> emitted = run(world, system, 35f);

        assertThat(health(character).current).isEqualTo(MAX_HP);
        assertThat(emitted).isNotEmpty();
        HealthChange last = emitted.get(emitted.size() - 1);
        assertThat(last.entityId()).isEqualTo(CHARACTER_ID);
        assertThat(last.currentHp()).isEqualTo(MAX_HP);
        assertThat(last.maxHp()).isEqualTo(MAX_HP);
    }

    /**
     * Cam kết với ADR-042 (đang muốn GIẢM số gói): hồi máu chạy mỗi tick nhưng chỉ BÁO nhiều nhất
     * 1 lần/giây. Không có trần này thì một người bị thương đẻ 60 gói/giây một mình.
     */
    @Test
    void shouldEmitAtMostOnePacketPerSecond_whileRegenerating() {
        EcsWorld world = new EcsWorld();
        buildCharacter(world, HP_AFTER_FIRST_FIGHT, 0f);
        HealthRegenSystem system = new HealthRegenSystem();
        world.addSystem(system);

        List<HealthChange> emitted = run(world, system, 35f);

        // ~23 giây hồi máu ⇒ ~23 gói (cộng/trừ một cho gói "vừa đầy máu" ở nhịp cuối). Cái quan
        // trọng là nó KHÔNG phải hàng nghìn.
        assertThat(emitted.size()).isBetween(20, 26);
    }

    @Test
    void shouldEmitNothingAndStayFull_whenAlreadyAtMaxHp() {
        // Trạng thái THƯỜNG của người chơi. Nhánh này phải im lặng tuyệt đối, nếu không thì mỗi
        // người online đẻ một gói mỗi giây suốt phiên chơi.
        EcsWorld world = new EcsWorld();
        KEntity character = buildCharacter(world, MAX_HP, 0f);
        HealthRegenSystem system = new HealthRegenSystem();
        world.addSystem(system);

        List<HealthChange> emitted = run(world, system, 35f);

        assertThat(emitted).isEmpty();
        assertThat(health(character).current).isEqualTo(MAX_HP);
    }

    @Test
    void shouldNotResurrect_whenHpIsZero() {
        // Hồi sinh là việc của WorldFacade.respawnCharacter (có vị trí, có Khí, có gói riêng). Nếu
        // System này hồi máu cho xác chết thì nhân vật đứng dậy tại chỗ với 3 máu và không ai báo
        // cho client — hai đường quyền lực cho cùng một việc.
        EcsWorld world = new EcsWorld();
        KEntity character = buildCharacter(world, 0, 0f);
        HealthRegenSystem system = new HealthRegenSystem();
        world.addSystem(system);

        List<HealthChange> emitted = run(world, system, 35f);

        assertThat(emitted).isEmpty();
        assertThat(health(character).current).isZero();
    }

    /**
     * Phần lẻ phải TÍCH LUỸ. Ở 60Hz mỗi tick chỉ hồi ~0,06 máu; cộng thẳng vào một {@code int} rồi
     * làm tròn xuống thì kết quả là 0 mỗi tick và máu KHÔNG BAO GIỜ hồi — đúng cái bẫy mà
     * {@code CombatRecencyComponent.healAccumulator} sinh ra để tránh.
     */
    @Test
    void shouldAccumulateFractionalHealing_insteadOfTruncatingEveryTick() {
        EcsWorld world = new EcsWorld();
        KEntity character = buildCharacter(world, HP_AFTER_FIRST_FIGHT, CombatConstants.REGEN_DELAY_SECONDS);
        HealthRegenSystem system = new HealthRegenSystem();
        world.addSystem(system);

        run(world, system, 1f);

        // 3% × 118 = 3,54 máu/giây ⇒ sau đúng 1 giây phải là 41 (38 + 3), không phải 38.
        assertThat(health(character).current).isEqualTo(HP_AFTER_FIRST_FIGHT + 3);
    }

    private static HealthComponent health(KEntity entity) {
        return HEALTH.get(entity);
    }
}
