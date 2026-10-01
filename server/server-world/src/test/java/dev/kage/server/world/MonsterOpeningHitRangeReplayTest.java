package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.TriggerZoneMembershipComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.Aabb;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.engine.ecs.system.NamedZone;
import dev.kage.engine.ecs.system.TriggerZoneSystem;
import dev.kage.server.world.component.AiParamsComponent;
import dev.kage.server.world.component.AiState;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * Kiểm sống V50 (2026-10-01, {@code n_beast1}): {@code wasteland_beast} (aggro 3) ra một nhát lúc
 * t=704ms khi người chơi còn ĐANG ĐI BỘ ra khỏi trạm {@code waypost_wasteland_gate} [165,5; 170,5].
 * Đọc thô nhật ký probe, gói vị trí cuối cùng của hai bên trước nhát đó cách nhau hơn 2m:
 * người chơi {@code MoveResponse x=170.67}, quái {@code MonsterMovedEvent (172.80, -0.92)} ⇒ 2,32m.
 * Câu hỏi: server có đánh NGOÀI tầm không?
 *
 * <p>Ô này dựng lại đúng lượt đó bằng các System THẬT, đúng thứ tự trong {@code WorldFacade}
 * (AI → Movement → TriggerZone → Combat), với toạ độ lấy từ nhật ký: người chơi xuất phát 169, đi
 * +x 5 m/s; quái đi tuần 1,5 m/s từ đúng chỗ probe thấy nó lúc spawn (172,48; -0,72). Không chỉnh
 * tay số nào cho khớp — vậy mà quỹ đạo dựng lại trùng nhật ký tới 0,01m (khẳng định bên dưới).
 *
 * <p>Kết luận nó khoá: <b>server đánh TRONG tầm</b> (≤ 2m, đo bằng vị trí sau di chuyển của chính
 * tick đó). Chỗ "ngoài tầm" chỉ có trong mắt probe, vì hai nguồn vị trí của nó đều CŨ HƠN tick
 * ra đòn: {@code MoveResponse} chỉ có khi probe gửi lệnh (4 tick/lần) và mang vị trí TRƯỚC khi di
 * chuyển của tick nhận lệnh; {@code MonsterMovedEvent} chỉ phát mỗi
 * {@link WorldConstants#AOI_SYNC_INTERVAL_TICKS} tick. Thêm trục y (quái lệch -0,9m) mà bộ đếm
 * chỉ nhìn x là bỏ sót.
 */
class MonsterOpeningHitRangeReplayTest {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    private static final long CHARACTER_ID = 8006L;
    private static final long MONSTER_ID = -16L;
    private static final float TICK_SECONDS = WorldConstants.FIXED_DT;
    private static final int TICKS_TO_REPLAY = 40;

    /** Probe gửi {@code MoveCommand} mỗi ~66ms trong nhật ký — 4 tick. */
    private static final int PROBE_MOVE_EVERY_TICKS = 4;

    // Toạ độ từ nhật ký n_beast1 (V50 seed: spawn 175, trạm waypost_wasteland_gate).
    private static final float PLAYER_START_X = 169f;
    private static final float MONSTER_START_X = 172.461f;
    private static final float MONSTER_START_Y = -0.708f;
    // Hướng đi tuần đọc từ chuỗi MonsterMovedEvent 388→640ms: (+0,32; -0,19) đã chuẩn hoá.
    private static final float PATROL_DIR_X = 0.861f;
    private static final float PATROL_DIR_Y = -0.509f;

    private record TickSample(int tick, float playerX, float monsterX, float monsterY) {}

    @Test
    void shouldLandTheOpeningHitInsideTwoMeters_eventhoughTheProbesLastPacketsSayMore() {
        EcsWorld world = new EcsWorld();
        KEntity player = buildWalkingPlayer(world);
        KEntity monster = buildPatrollingWasteland(world);
        Map<Long, KEntity> byId = Map.of(CHARACTER_ID, player, MONSTER_ID, monster);

        CombatSystem combat = new CombatSystem(byId::get, () -> false);
        world.addSystem(new AiSystem(() -> List.of(player), byId::get));
        world.addSystem(new MovementSystem());
        TriggerZoneSystem zones = new TriggerZoneSystem();
        zones.setZones(List.of(new NamedZone("waypost_wasteland_gate", new Aabb(165.5f, -10f, 170.5f, 10f))));
        world.addSystem(zones);
        world.addSystem(combat);

        TickSample lastMoveResponse = null; // vị trí người chơi TRƯỚC di chuyển, lúc drain lệnh
        TickSample lastAoiSync = null; // vị trí quái SAU di chuyển, mỗi AOI_SYNC_INTERVAL_TICKS
        TickSample hit = null;
        TickSample probeViewAtHit = null;
        for (int tick = 0; tick < TICKS_TO_REPLAY && hit == null; tick++) {
            if (tick % PROBE_MOVE_EVERY_TICKS == 0) {
                lastMoveResponse = sample(tick, player, monster);
            }
            world.update(TICK_SECONDS);
            List<CombatSystem.AttackOutcome> outcomes = combat.drainResults();
            if (!outcomes.isEmpty()) {
                assertThat(outcomes)
                        .singleElement()
                        .extracting(CombatSystem.AttackOutcome::attackerEntityId)
                        .isEqualTo(MONSTER_ID);
                hit = sample(tick, player, monster);
                probeViewAtHit = new TickSample(
                        tick, lastMoveResponse.playerX(), lastAoiSync.monsterX(), lastAoiSync.monsterY());
            }
            // Pha AoI khớp nhật ký: 640ms = sau tick 18, 703ms = sau tick 21.
            if (tick % WorldConstants.AOI_SYNC_INTERVAL_TICKS == 0) {
                lastAoiSync = sample(tick, player, monster);
            }
        }

        assertThat(hit)
                .as("quái phải ra nhát mở màn trong lúc người chơi đi ngang")
                .isNotNull();
        assertThat(distance(hit))
                .as("server: khoảng cách THẬT lúc ra đòn (vị trí sau di chuyển của chính tick đó)")
                .isLessThanOrEqualTo(CombatConstants.ATTACK_RANGE_METERS);
        assertThat(distance(probeViewAtHit))
                .as("probe: gói vị trí cuối cùng nó có lúc nhận AttackResultEvent — cũ hơn tick ra đòn")
                .isGreaterThan(CombatConstants.ATTACK_RANGE_METERS)
                .isCloseTo(2.32, within(0.02));

        // Bản dựng lại khớp nhật ký: người chơi giữa hai MoveResponse 170.67 và 171.00; quái đứng
        // lại đúng chỗ MonsterMovedEvent 770ms báo (172.71; -0.88) sau khi đánh.
        assertThat(hit.playerX()).isBetween(170.67f, 171.01f);
        assertThat(hit.monsterX()).isCloseTo(172.71f, within(0.02f));
        assertThat(hit.monsterY()).isCloseTo(-0.88f, within(0.02f));
        assertThat(probeViewAtHit.playerX()).isCloseTo(170.67f, within(0.01f));
        assertThat(probeViewAtHit.monsterX()).isCloseTo(172.80f, within(0.02f));
        assertThat(probeViewAtHit.monsterY()).isCloseTo(-0.92f, within(0.02f));
    }

    private static TickSample sample(int tick, KEntity player, KEntity monster) {
        PositionComponent p = POSITION.get(player);
        PositionComponent m = POSITION.get(monster);
        return new TickSample(tick, p.x, m.x, m.y);
    }

    private static double distance(TickSample s) {
        // Người chơi luôn ở y = 0 (ADR-033: khoá trục ngang).
        return Math.hypot((double) s.monsterX() - s.playerX(), s.monsterY());
    }

    private static KEntity buildWalkingPlayer(EcsWorld world) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = PLAYER_START_X;
        position.grounded = true;
        entity.add(position);
        VelocityComponent velocity = new VelocityComponent();
        velocity.x = WorldConstants.MOVE_SPEED_MPS; // đúng như handleMoveIntent với dirX = +1
        entity.add(velocity);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = CHARACTER_ID;
        entity.add(id);
        HealthComponent health = new HealthComponent();
        health.current = 206;
        health.max = 206;
        entity.add(health);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.element = Element.NONE;
        entity.add(stats);
        entity.add(new StatusEffectComponent());
        entity.add(new TriggerZoneMembershipComponent());
        entity.add(new CombatRecencyComponent()); // chưa từng ra đòn ⇒ vùng an toàn che (ADR-050)
        return entity;
    }

    private static KEntity buildPatrollingWasteland(EcsWorld world) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = MONSTER_START_X;
        position.y = MONSTER_START_Y;
        position.grounded = true;
        entity.add(position);
        entity.add(new VelocityComponent());
        AiStateComponent ai = new AiStateComponent();
        ai.state = AiState.PATROL;
        ai.spawnX = 175f;
        ai.spawnY = 0f;
        ai.patrolTargetX = MONSTER_START_X + (PATROL_DIR_X * 3f);
        ai.patrolTargetY = MONSTER_START_Y + (PATROL_DIR_Y * 3f);
        entity.add(ai);
        // wasteland_beast: aggro 3 (V48), leash/chase/patrol mặc định V25 (10 / 3 / 1,5), retaliates.
        entity.add(new AiParamsComponent(3f, 10f, 3f, 1.5f, 3f, 3f, true));
        entity.add(new TargetComponent());
        entity.add(new AttackCooldownComponent());
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.atk = 36;
        stats.hitPercent = 0.94f;
        stats.element = Element.NONE;
        entity.add(stats);
        HealthComponent health = new HealthComponent();
        health.current = 289;
        health.max = 289;
        entity.add(health);
        MonsterIdComponent id = new MonsterIdComponent();
        id.monsterId = MONSTER_ID;
        entity.add(id);
        entity.add(new StatusEffectComponent());
        return entity;
    }
}
