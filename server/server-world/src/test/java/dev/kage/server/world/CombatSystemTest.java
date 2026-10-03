package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * ADR-033 Decision #4 — nhân vật đang lơ lửng (grounded=false) né TUYỆT ĐỐI đòn đánh thường, bất
 * kể hit%/dodge% (xem {@code CombatSystem.resolveAndRecordAttack}). Test trực tiếp
 * {@link CombatSystem} (không qua {@code WorldFacade}/tick loop thật) để tránh phụ thuộc thời gian
 * cửa sổ nhảy (~0.7s, xem {@code WorldConstants.JUMP_VELOCITY_MPS}/{@code GRAVITY_MPS2}) trùng đúng
 * lúc quái hết cooldown đánh (1s) — rất dễ flaky nếu test qua tick loop thật.
 */
class CombatSystemTest {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<AttackCooldownComponent> COOLDOWN =
            ComponentMapper.getFor(AttackCooldownComponent.class);
    private static final ComponentMapper<CombatRecencyComponent> RECENCY =
            ComponentMapper.getFor(CombatRecencyComponent.class);

    private static final long ATTACKER_ID = 1L;
    private static final long DEFENDER_ID = 2L;

    /** ÂM — quái/NPC dùng không gian id âm (combat.proto), khác nhân vật luôn dương. */
    private static final long MONSTER_ATTACKER_ID = -1L;

    // Người thủ ở đây mang CharacterIdComponent — lớp này kiểm CƠ CHẾ đòn đánh (cooldown/tầm/né),
    // không kiểm luật PvP, nên bật PvP. Luật PvP: PlayerVersusPlayerRuleTest.
    private static boolean pvpAllowed() {
        return true;
    }

    private KEntity buildAttacker(EcsWorld world) {
        KEntity entity = buildAttackerWithoutIdentity(world);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = ATTACKER_ID;
        entity.add(id);
        return entity;
    }

    /** Y hệt {@link #buildAttacker} nhưng mang {@link MonsterIdComponent} — quái vẫn TỰ đánh. */
    private KEntity buildMonsterAttacker(EcsWorld world) {
        KEntity entity = buildAttackerWithoutIdentity(world);
        MonsterIdComponent id = new MonsterIdComponent();
        id.monsterId = MONSTER_ATTACKER_ID;
        entity.add(id);
        return entity;
    }

    /**
     * Phần chung: chính {@code CharacterIdComponent} vs {@code MonsterIdComponent} là thứ quyết
     * định có tự đánh hay không (2026-08-09), nên hai builder chỉ được khác nhau đúng ở đó —
     * {@code KEntity} không có {@code remove()} nên phải tách hàm chứ không gỡ component ra sau.
     */
    private KEntity buildAttackerWithoutIdentity(EcsWorld world) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        AttackCooldownComponent cooldown = new AttackCooldownComponent();
        cooldown.remainingSeconds = 0f;
        entity.add(cooldown);
        TargetComponent target = new TargetComponent();
        target.targetEntityId = DEFENDER_ID;
        entity.add(target);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.atk = 50; // đủ cao để chắc chắn damage > 0 khi trúng (defender.def = 0).
        stats.hitPercent = 1f;
        stats.critPercent = 0f;
        stats.critDamageMultiplier = 1.5f;
        stats.element = Element.NONE;
        entity.add(stats);
        entity.add(new StatusEffectComponent());
        return entity;
    }

    private KEntity buildDefender(EcsWorld world, boolean grounded) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        POSITION.get(entity).grounded = grounded;
        HealthComponent health = new HealthComponent();
        health.current = 100;
        health.max = 100;
        entity.add(health);
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.def = 0;
        stats.dodgePercent = 0f;
        stats.element = Element.NONE;
        entity.add(stats);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = DEFENDER_ID;
        entity.add(id);
        entity.add(new StatusEffectComponent());
        return entity;
    }

    /**
     * Chính là điều chủ dự án phàn nàn 2026-08-09: "mới chọn vào chưa nhấn nút đánh mà quái đã mất
     * hp". Nhân vật KHÔNG được tự đánh dù đã có mục tiêu, trong tầm, hết cooldown.
     */
    @Test
    void shouldNotAttack_whenCharacterHasTargetButNeverRequestedAttack() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildAttacker(world);
        KEntity defender = buildDefender(world, true);
        Map<Long, KEntity> byId = Map.of(ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get, CombatSystemTest::pvpAllowed);
        world.addSystem(combatSystem);

        for (int tick = 0; tick < 300; tick++) { // 5 giây ở 60Hz — thừa sức qua nhiều lần cooldown
            world.update(1f / 60f);
        }

        assertThat(combatSystem.drainResults()).isEmpty();
        assertThat(HEALTH.get(defender).current).isEqualTo(100);
    }

    /**
     * Mặt kia của cùng một quyết định: QUÁI vẫn tự đánh. Bỏ auto cho cả hai phía là không con nào
     * đánh người chơi nữa — người chơi bất tử, không phải điều chủ dự án yêu cầu.
     */
    @Test
    void shouldStillAutoAttack_whenAttackerIsMonster() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildMonsterAttacker(world);
        KEntity defender = buildDefender(world, true);
        Map<Long, KEntity> byId = Map.of(MONSTER_ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get, CombatSystemTest::pvpAllowed);
        world.addSystem(combatSystem);

        world.update(0.016f);

        assertThat(combatSystem.drainResults()).hasSize(1);
    }

    /**
     * Đòn 1 giây phải cách nhau ĐÚNG 60 tick. Trước {@code COOLDOWN_READY_EPSILON_SECONDS} là 61:
     * trừ {@code 1f/60f} kiểu float 60 lần còn dư ~2,8e-7 &gt; 0 (đo 2026-10-01).
     */
    @Test
    void shouldAttackEverySixtyTicks_despiteFloatCooldownResidue() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildMonsterAttacker(world);
        KEntity defender = buildDefender(world, true);
        HEALTH.get(defender).max = 1_000_000;
        HEALTH.get(defender).current = 1_000_000;
        Map<Long, KEntity> byId = Map.of(MONSTER_ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get, CombatSystemTest::pvpAllowed);
        world.addSystem(combatSystem);

        List<Integer> attackTicks = new ArrayList<>();
        for (int tick = 0; tick < 300; tick++) {
            world.update(WorldConstants.FIXED_DT);
            if (!combatSystem.drainResults().isEmpty()) {
                attackTicks.add(tick);
            }
        }

        assertThat(attackTicks).containsExactly(0, 60, 120, 180, 240);
    }

    @Test
    void shouldAlwaysMissAutoAttack_whenDefenderAirborne() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildAttacker(world);
        KEntity defender = buildDefender(world, false); // grounded=false — đang lơ lửng (ADR-033)
        Map<Long, KEntity> byId = Map.of(ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get, CombatSystemTest::pvpAllowed);
        world.addSystem(combatSystem);

        // Người tấn công là NHÂN VẬT nên không tự đánh nữa (2026-08-09) — phải yêu cầu tường minh,
        // đúng đường mà AttackCommand đi qua. world.update() vẫn cần chạy để cooldown đếm lùi.
        world.update(0.016f);
        combatSystem.requestAttack(attacker);

        List<CombatSystem.AttackOutcome> results = combatSystem.drainResults();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).miss()).isTrue();
        assertThat(results.get(0).damage()).isZero();
        assertThat(HEALTH.get(defender).current).isEqualTo(100); // HP không đổi — đòn không hề trừ.
    }

    @Test
    void shouldResolveNormally_whenDefenderGrounded() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildAttacker(world);
        KEntity defender = buildDefender(world, true); // grounded=true — hành vi cũ, không đổi
        Map<Long, KEntity> byId = Map.of(ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get, CombatSystemTest::pvpAllowed);
        world.addSystem(combatSystem);

        world.update(0.016f);
        combatSystem.requestAttack(attacker); // xem test trên — nhân vật chỉ đánh khi được yêu cầu

        List<CombatSystem.AttackOutcome> results = combatSystem.drainResults();
        assertThat(results).hasSize(1);
        // hitPercent=1, dodgePercent=0 ở buildDefender/buildAttacker — đòn LUÔN trúng khi grounded.
        assertThat(results.get(0).miss()).isFalse();
        assertThat(results.get(0).damage()).isGreaterThan(0);
    }

    /**
     * Kiểm sống V50 (2026-10-01): ở 5/10 lượt {@code gameProbe}, nhát đầu của người chơi tới trễ
     * ~1,15–1,3 s sau lúc "đi bộ xong". Đây là cơ chế: {@code AttackCommand} là lệnh MỘT LẦN — gửi
     * lúc mục tiêu còn ngoài tầm 2m (con quái vẫn đang đuổi tới) thì bị bỏ IM LẶNG, và KHÔNG được
     * giữ lại để tự bắn khi mục tiêu vào tầm. Người gửi phải gửi lại; probe gửi lại sau đúng
     * {@code attackIntervalMillis}, client thật (giữ phím) sau ~1 giây.
     *
     * <p>Ô này khoá hành vi ĐÃ CHỦ ĐÍCH ({@code CombatSystem.tryAttackNow}: "ngoài tầm — chờ, KHÔNG
     * reset cooldown, không tích luỹ đòn") để ai muốn đổi sang "nhớ ý định đánh" phải đổi nó một cách
     * có ý thức — đó là quyết định thiết kế, không phải sửa lỗi.
     */
    @Test
    void shouldDropAttackRequestSilently_whenTargetStillOutOfRange_andNotReplayItWhenTargetCloses() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildAttacker(world);
        KEntity defender = buildDefender(world, true);
        POSITION.get(defender).x = CombatConstants.ATTACK_RANGE_METERS + 0.3f; // con quái còn đang đuổi tới
        Map<Long, KEntity> byId = Map.of(ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get, CombatSystemTest::pvpAllowed);
        world.addSystem(combatSystem);
        world.update(1f / 60f);

        combatSystem.requestAttack(attacker); // lệnh tới khi còn ngoài tầm
        assertThat(combatSystem.drainResults())
                .as("ngoài tầm: bỏ im lặng, không có kết quả nào")
                .isEmpty();
        assertThat(COOLDOWN.get(attacker).remainingSeconds)
                .as("lệnh bị bỏ không tiêu cooldown")
                .isLessThanOrEqualTo(0f);

        POSITION.get(defender).x = CombatConstants.ATTACK_RANGE_METERS - 0.1f; // một tick sau nó đã vào tầm
        for (int tick = 0; tick < 60; tick++) { // 1 giây — đủ để thấy lệnh cũ KHÔNG tự bắn lại
            world.update(1f / 60f);
        }
        assertThat(combatSystem.drainResults())
                .as("ý định đánh không được nhớ lại")
                .isEmpty();
        assertThat(HEALTH.get(defender).current).isEqualTo(100);

        combatSystem.requestAttack(attacker); // chỉ lệnh MỚI mới ra đòn
        assertThat(combatSystem.drainResults()).hasSize(1);
    }

    /**
     * ADR-046 D2 — một đòn đánh phải đẩy CẢ HAI bên ra khỏi trạng thái "ngoài chiến đấu". Chỉ đánh
     * dấu bên chịu đòn là kẻ tấn công vừa đánh vừa tự hồi máu; chỉ đánh dấu kẻ tấn công là người bị
     * đánh hồi máu ngay giữa lúc bị đánh.
     */
    @Test
    void shouldMarkBothSidesInCombat_soNeitherRegeneratesRightAfterATrade() {
        EcsWorld world = new EcsWorld();
        KEntity attacker = buildAttacker(world);
        KEntity defender = buildDefender(world, true);
        attacker.add(new CombatRecencyComponent());
        defender.add(new CombatRecencyComponent());
        RECENCY.get(attacker).secondsSinceCombat = 999f;
        RECENCY.get(defender).secondsSinceCombat = 999f;
        Map<Long, KEntity> byId = Map.of(ATTACKER_ID, attacker, DEFENDER_ID, defender);
        CombatSystem combatSystem = new CombatSystem(byId::get, CombatSystemTest::pvpAllowed);
        world.addSystem(combatSystem);

        world.update(0.016f);
        combatSystem.requestAttack(attacker);

        assertThat(RECENCY.get(attacker).secondsSinceCombat).isZero();
        assertThat(RECENCY.get(defender).secondsSinceCombat).isZero();
    }
}
