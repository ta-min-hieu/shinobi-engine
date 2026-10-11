package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.scripting.ScriptExecutionException;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CharacterLevelComponent;
import dev.kage.server.world.component.ChiComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.SkillCooldownComponent;
import dev.kage.server.world.component.StatusEffectComponent;

/**
 * Script skill lỗi LÚC CHẠY (sửa 2026-10-02). SCRIPTING.md §7: script lỗi → huỷ + log. Trước đó:
 *
 * <ul>
 *   <li>Khí và cooldown bị trừ TRƯỚC khi chạy script, rồi script ném lỗi thì nhánh dự phòng
 *       "damage, value=0" vẫn gây {@code MIN_DAMAGE_FLOOR} = 1 máu — kể cả với skill hồi máu nhắm
 *       ĐỒNG ĐỘI. Người chơi mất Khí, mất cooldown, đồng đội mất máu.
 *   <li>Script trả SAI HÌNH DẠNG (thiếu {@code effect}, sai kiểu) ném {@code NullPointerException}/
 *       {@code ClassCastException} — không phải {@code ScriptExecutionException} nên lọt khỏi
 *       {@code tryCast}, mất trọn tick của cả World.
 * </ul>
 *
 * <p>Cả hai script lỗi ở đây đều QUA được bước validate lúc khởi động (ctx giả có
 * {@code target_hp_percent = 1.0}) và chỉ rẽ sang nhánh hỏng khi mục tiêu đã mất máu — đúng kiểu lỗi
 * validate lúc khởi động không bắt được.
 */
class SkillSystemScriptFailureTest {

    private static final ComponentMapper<ChiComponent> CHI = ComponentMapper.getFor(ChiComponent.class);
    private static final ComponentMapper<SkillCooldownComponent> COOLDOWN =
            ComponentMapper.getFor(SkillCooldownComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);

    private static final long CASTER_ID = 1L;
    private static final long MONSTER_ID = -1L;
    private static final String SKILL = "hoa_tia_lua";
    private static final String VALID_SCRIPT = "({ effect: 'damage', value: 1 })";

    private static final String THROWS_WHEN_TARGET_HURT =
            "(function () { if (ctx.target_hp_percent < 1) { throw new Error('boom'); }"
                    + " return { effect: 'damage', value: 1 }; })()";
    private static final String MALFORMED_WHEN_TARGET_HURT =
            "(function () { if (ctx.target_hp_percent < 1) { return { value: 3 }; }"
                    + " return { effect: 'damage', value: 1 }; })()";

    private static KEntity buildCaster(EcsWorld world) {
        return buildCaster(world, Element.HOA);
    }

    private static KEntity buildCaster(EcsWorld world, Element element) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = CASTER_ID;
        entity.add(id);
        entity.add(health(100, 100));
        entity.add(stats(element));
        entity.add(new SkillCooldownComponent());
        entity.add(new StatusEffectComponent());
        CharacterLevelComponent level = new CharacterLevelComponent();
        level.level = 1;
        entity.add(level);
        ChiComponent chi = new ChiComponent();
        chi.current = 100;
        chi.max = 100;
        entity.add(chi);
        return entity;
    }

    private static KEntity buildHurtMonster(EcsWorld world) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = 1f;
        entity.add(position);
        entity.add(health(40, 100)); // < 100% máu ⇒ script rẽ sang nhánh hỏng.
        entity.add(stats(Element.NONE));
        entity.add(new StatusEffectComponent());
        return entity;
    }

    private static HealthComponent health(int current, int max) {
        HealthComponent health = new HealthComponent();
        health.current = current;
        health.max = max;
        return health;
    }

    private static CombatStatsComponent stats(Element element) {
        CombatStatsComponent stats = new CombatStatsComponent();
        stats.skillDmg = 25;
        stats.hitPercent = 1f;
        stats.critDamageMultiplier = 1.5f;
        stats.element = element;
        return stats;
    }

    private static Function<SkillDefinition, String> scriptsWith(String brokenScript) {
        return definition -> SKILL.equals(definition.code()) ? brokenScript : VALID_SCRIPT;
    }

    @Test
    void shouldSpendNoChiNorCooldownAndDealNoDamage_whenTheSkillScriptThrowsAtRuntime() {
        assertCastRejectedCleanly(THROWS_WHEN_TARGET_HURT);
    }

    @Test
    void shouldRejectTheCastInsteadOfKillingTheTick_whenTheSkillScriptReturnsAMalformedResult() {
        assertCastRejectedCleanly(MALFORMED_WHEN_TARGET_HURT);
    }

    /** Đối chứng: cùng dựng như trên nhưng mục tiêu ĐẦY máu ⇒ script đi nhánh lành ⇒ cast thật. */
    @Test
    void shouldCastNormally_whenTheSameScriptTakesItsHealthyBranch() {
        EcsWorld world = new EcsWorld();
        KEntity caster = buildCaster(world);
        KEntity monster = buildHurtMonster(world);
        HEALTH.get(monster).current = 100;
        Map<Long, KEntity> byId = Map.of(CASTER_ID, caster, MONSTER_ID, monster);
        SkillSystem system = new SkillSystem(byId::get, () -> false, scriptsWith(MALFORMED_WHEN_TARGET_HURT));

        system.tryCast(CASTER_ID, SKILL, MONSTER_ID);

        assertThat(system.drainResults()).hasSize(1);
        assertThat(CHI.get(caster).current).isLessThan(100);
    }

    /**
     * Lượng hồi cực lớn: {@code Math.round} bão hoà ở {@code Integer.MAX_VALUE}, cộng thẳng vào máu
     * hiện tại kiểu int thì tràn thành số âm ⇒ skill hồi máu GIẾT đồng đội.
     */
    @Test
    void shouldCapAtMaxHp_whenAHealIsLargerThanAnIntCanHold() {
        EcsWorld world = new EcsWorld();
        KEntity caster = buildCaster(world, Element.THUY);
        HEALTH.get(caster).current = 60;
        Map<Long, KEntity> byId = Map.of(CASTER_ID, caster);
        SkillSystem system = new SkillSystem(
                byId::get,
                () -> false,
                definition ->
                        "thuy_hoi_phuc".equals(definition.code()) ? "({ effect: 'heal', value: 1e12 })" : VALID_SCRIPT);

        system.tryCast(CASTER_ID, "thuy_hoi_phuc", CASTER_ID);

        assertThat(HEALTH.get(caster).current).isEqualTo(100);
    }

    /**
     * Lượt chạy thử lúc dựng World có trần thời gian RIÊNG, rộng hơn 500ms của cast trên tick (chủ dự
     * án chốt 5 giây, 2026-10-11): 500ms từng cắt nhầm script hợp lệ khi máy tải nặng, và dựng World
     * hỏng là người chơi bị từ chối vào phó bản. Nó vẫn phải là một chốt chống treo — script treo
     * thật (regex backtracking, một câu lệnh duy nhất nên trần câu lệnh không thấy) bị cắt, và thông
     * báo mang đúng trần đó.
     */
    @Test
    void shouldCutAHangingScriptAtTheValidationBudget_whenBuildingTheSystem() {
        String hangingScript = "/^(a+)+\\1b$/.test('a'.repeat(40) + 'c'); ({ effect: 'damage', value: 1 })";
        Map<Long, KEntity> nobody = Map.of();

        assertThatThrownBy(() -> new SkillSystem(nobody::get, () -> false, scriptsWith(hangingScript)))
                .isInstanceOf(ScriptExecutionException.class)
                .hasMessageContaining(SkillConstants.SCRIPT_VALIDATION_TIMEOUT.toMillis() + "ms");
    }

    private static void assertCastRejectedCleanly(String brokenScript) {
        EcsWorld world = new EcsWorld();
        KEntity caster = buildCaster(world);
        KEntity monster = buildHurtMonster(world);
        Map<Long, KEntity> byId = Map.of(CASTER_ID, caster, MONSTER_ID, monster);
        SkillSystem system = new SkillSystem(byId::get, () -> false, scriptsWith(brokenScript));

        system.tryCast(CASTER_ID, SKILL, MONSTER_ID);

        assertThat(system.drainResults()).isEmpty();
        assertThat(CHI.get(caster).current).isEqualTo(100);
        assertThat(COOLDOWN.get(caster).remainingSeconds).containsOnly(0f);
        assertThat(HEALTH.get(monster).current).isEqualTo(40);
    }
}
