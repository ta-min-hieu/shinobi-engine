package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.server.world.component.AiParamsComponent;
import dev.kage.server.world.component.AiState;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.BossAiComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * ADR-046 D4 — quái về tới điểm spawn thì lành lại HẾT.
 *
 * <p>Đây không phải "cho đẹp": trước ADR-046, {@code tickReturn} đưa quái về đúng chỗ rồi chuyển
 * {@code IDLE} mà KHÔNG đụng máu, nên "đánh vài nhát → chạy ra khỏi leash → quay lại đánh tiếp con
 * máu thấp" là vô hạn. Lỗ hổng đó ngủ đông vì trước đây người chơi cũng không hồi máu được; cho
 * người chơi hồi máu (D2) mà không cho quái lành lại là biến nó thành chiến thuật tối ưu của MỌI
 * trận.
 *
 * <p>Test thẳng {@link AiSystem} với thời gian mô phỏng, cùng khuôn {@link CombatSystemTest}.
 */
class AiSystemHealOnReturnTest {

    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<BossAiComponent> BOSS_AI = ComponentMapper.getFor(BossAiComponent.class);

    private static final long MONSTER_ID = -1L;
    private static final int MAX_HP = 50;
    private static final float SPAWN_X = 10f;
    private static final float TICK_SECONDS = 1f / 60f;

    /** Quái đang trên đường VỀ, còn cách điểm spawn {@code distanceFromSpawn} mét, máu {@code hp}. */
    private KEntity buildReturningMonster(EcsWorld world, int hp, float distanceFromSpawn) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = SPAWN_X + distanceFromSpawn;
        position.y = 0f;
        entity.add(position);
        entity.add(new VelocityComponent());

        AiStateComponent ai = new AiStateComponent();
        ai.state = AiState.RETURN;
        ai.spawnX = SPAWN_X;
        ai.spawnY = 0f;
        entity.add(ai);
        entity.add(new AiParamsComponent(6f, 10f, 3f, 1.5f, 3f, 3f, false));
        entity.add(new TargetComponent());

        HealthComponent health = new HealthComponent();
        health.current = hp;
        health.max = MAX_HP;
        entity.add(health);

        MonsterIdComponent id = new MonsterIdComponent();
        id.monsterId = MONSTER_ID;
        entity.add(id);
        return entity;
    }

    private List<HealthChange> runUntilHome(EcsWorld world, AiSystem system, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            world.update(TICK_SECONDS);
            List<HealthChange> emitted = system.drainHealthChanges();
            if (!emitted.isEmpty()) {
                return emitted;
            }
        }
        return system.drainHealthChanges();
    }

    @Test
    void shouldHealToFullAndAnnounceIt_whenMonsterGetsBackToItsSpawnPoint() {
        EcsWorld world = new EcsWorld();
        // 0,1m — trong ngưỡng ARRIVAL_THRESHOLD_METERS (0,3m) nên tick đầu đã là "đã về tới nơi".
        // KHÔNG đặt xa hơn: test này chỉ nạp AiSystem, không có MovementSystem để tích luỹ vị trí,
        // nên con quái sẽ có vận tốc mà không bao giờ nhúc nhích.
        KEntity monster = buildReturningMonster(world, 7, 0.1f); // còn 7/50 máu
        AiSystem system = new AiSystem(List::of, id -> null);
        world.addSystem(system);

        List<HealthChange> emitted = runUntilHome(world, system, 10);

        assertThat(HEALTH.get(monster).current).isEqualTo(MAX_HP);
        assertThat(emitted).containsExactly(new HealthChange(MONSTER_ID, MAX_HP, MAX_HP));
    }

    @Test
    void shouldStaySilent_whenTheReturningMonsterWasNeverHurt() {
        // Quái đi tuần rồi quay về mà không ai đụng tới là chuyện thường xuyên. Phát một gói cho
        // mỗi lần như thế là đúng thứ ADR-042 đang muốn tránh.
        EcsWorld world = new EcsWorld();
        KEntity monster = buildReturningMonster(world, MAX_HP, 0.1f);
        AiSystem system = new AiSystem(List::of, id -> null);
        world.addSystem(system);

        List<HealthChange> emitted = runUntilHome(world, system, 10);

        assertThat(HEALTH.get(monster).current).isEqualTo(MAX_HP);
        assertThat(emitted).isEmpty();
    }

    /**
     * Về nhà là "trận đấu reset" (ADR-046 D4) — với boss, phải reset cả PHA chứ không chỉ máu.
     *
     * <p>Script boss khai {@code already_enraged} là "chỉ kích hoạt 1 lần/TRẬN". Trước bản sửa, boss
     * lành đầy máu nhưng {@code enraged} vẫn {@code true}: trận sau nó bỏ hẳn đòn enrage và tung
     * {@code heavy_strike} với hệ số của pha cuối ngay từ 70% máu — một con 100% máu đánh như con 30%.
     */
    @Test
    void shouldResetTheBossPhase_whenAnEnragedBossGetsBackToItsSpawnPoint() {
        EcsWorld world = new EcsWorld();
        KEntity boss = buildReturningMonster(world, 7, 0.1f);
        BossAiComponent bossAi = new BossAiComponent();
        bossAi.enraged = true;
        boss.add(bossAi);
        AiSystem system = new AiSystem(List::of, id -> null);
        world.addSystem(system);

        runUntilHome(world, system, 10);

        assertThat(HEALTH.get(boss).current).isEqualTo(MAX_HP);
        assertThat(BOSS_AI.get(boss).enraged).isFalse();
    }
}
