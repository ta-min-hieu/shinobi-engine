package dev.kage.server.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.AiParamsComponent;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.ChiComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.SkillCooldownComponent;
import dev.kage.server.world.component.StatusEffect;
import dev.kage.server.world.component.StatusEffectComponent;

/**
 * Bất biến TRẠNG THÁI kiểm sau mỗi tick của bộ kiểm thuộc tính — đọc thẳng entity qua các view chỉ
 * đọc của {@link WorldFacade} (khe kiểm thử package-private), ghi vi phạm vào danh sách chung.
 *
 * <p>Server không có biên bản đồ theo trục X (không hằng số nào kẹp {@code x}; dải nền đất
 * {@code [-55, 210]} chỉ sống ở client — xem {@code DemoPlatforms}). Nên "trong biên hợp lệ" ở đây là
 * những ràng buộc mà server THẬT SỰ gánh: nhân vật trên đường chơi {@code y = 0} (ADR-033), độ cao
 * {@code z} trong tầm với của nhảy + bệ, không dịch chuyển nhanh hơn tốc độ chạy tối đa (trừ hồi sinh),
 * quái không ra khỏi vòng xích (leash) quanh điểm spawn.
 */
final class WorldStateInvariants {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ChiComponent> CHI = ComponentMapper.getFor(ChiComponent.class);
    private static final ComponentMapper<AttackCooldownComponent> ATTACK_COOLDOWN =
            ComponentMapper.getFor(AttackCooldownComponent.class);
    private static final ComponentMapper<SkillCooldownComponent> SKILL_COOLDOWN =
            ComponentMapper.getFor(SkillCooldownComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS =
            ComponentMapper.getFor(StatusEffectComponent.class);
    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<AiParamsComponent> AI_PARAMS = ComponentMapper.getFor(AiParamsComponent.class);

    /** Bệ cao nhất 2,2m + đỉnh nhảy thường 1,225m + nhảy đôi 1,6m ≈ 5,03m (WorldConstants) — nới lên 6. */
    private static final float MAX_Z = 6f;
    /** Gia Tốc (Phong) +50% là buff tốc độ lớn nhất — nới lên gấp đôi tốc độ chạy. */
    private static final float MAX_STEP_PER_TICK = WorldConstants.MOVE_SPEED_MPS * 2f * WorldConstants.FIXED_DT;

    private static final float POSITION_EPSILON = 1e-3f;
    /** Một tick đuổi vượt leash (leash kiểm TRƯỚC khi bước) + sai số. */
    private static final float LEASH_SLACK = 0.25f;

    /** ADR-117 — thời gian chờ hồi sinh tính bằng tick, +1 đệm. */
    private static final int MAX_CORPSE_TICKS = Math.round(PendingRevivals.WAIT_SECONDS * 60f) + 1;

    private final Map<Long, Integer> deadTicks = new HashMap<>();
    private final WorldFacade world;
    private final WorldFuzzScenario scenario;
    private final InvariantCheckingSink sink;
    private final List<String> violations;
    private final Map<Long, KEntity> previousEntity = new HashMap<>();
    private final Map<Long, Float> previousX = new HashMap<>();
    private final Map<Long, KEntity> deadAtPreviousTick = new HashMap<>();
    private int bossesRequested;
    private long tick;

    WorldStateInvariants(WorldFacade world, WorldFuzzScenario scenario, InvariantCheckingSink sink) {
        this.world = world;
        this.scenario = scenario;
        this.sink = sink;
        this.violations = sink.violations();
        this.bossesRequested = scenario == WorldFuzzScenario.DUNGEON ? 1 : 0;
    }

    void onBossRequested() {
        bossesRequested++;
    }

    /** Kiểm mọi bất biến trạng thái sau tick {@code tickNumber}, rồi chụp lại để so ở tick sau. */
    void check(long tickNumber, Set<Long> attachedModel) {
        this.tick = tickNumber;
        checkPopulation(attachedModel);
        checkDeadDidNotAct();
        for (Map.Entry<Long, KEntity> entry : world.characterEntitiesView().entrySet()) {
            checkCharacter(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Long, KEntity> entry : world.monsterEntitiesView().entrySet()) {
            checkMonster(entry.getKey(), entry.getValue());
        }
        remember();
    }

    // ---------------------------------------------------------------------------------------------

    private void violation(String message) {
        violations.add("tick " + tick + ": " + message);
    }

    private void checkPopulation(Set<Long> attachedModel) {
        Map<Long, KEntity> characters = world.characterEntitiesView();
        if (!characters.keySet().equals(attachedModel)) {
            violation("nhân vật trong world " + characters.keySet() + " ≠ đang gắn " + attachedModel);
        }
        int monsters = world.monsterEntitiesView().size();
        int npcs = world.npcEntitiesView().size();
        int expected = characters.size() + monsters + npcs;
        if (world.ecsEntityCount() != expected) {
            violation("entity mồ côi: EcsWorld có " + world.ecsEntityCount() + ", ba map cộng lại " + expected);
        }
        if (monsters > scenario.spawnPointCount() + bossesRequested) {
            violation("quái sống " + monsters + " > điểm spawn " + scenario.spawnPointCount() + " + boss "
                    + bossesRequested);
        }
        if (npcs != scenario.npcCount()) {
            violation("NPC " + npcs + " ≠ " + scenario.npcCount());
        }
    }

    /** Ai nằm 0 máu SUỐT từ cuối tick trước (cùng entity) thì tick này không được ra đòn. */
    private void checkDeadDidNotAct() {
        for (long attacker : sink.attackersThisTick()) {
            KEntity corpse = deadAtPreviousTick.get(attacker);
            if (corpse != null
                    && Objects.equals(corpse, world.characterEntitiesView().get(attacker))) {
                violation("nhân vật " + attacker + " đã chết từ tick trước mà vẫn ra đòn");
            }
        }
    }

    private void checkCharacter(long characterId, KEntity entity) {
        PositionComponent position = POSITION.get(entity);
        if (!Float.isFinite(position.x) || position.y != 0f || !(position.z >= 0f && position.z <= MAX_Z)) {
            violation("nhân vật " + characterId + " ngoài biên: " + describe(position));
        }
        checkHealthAndChi(characterId, entity);
        checkCooldowns(characterId, entity);
        checkStep(characterId, entity, position);
    }

    /** ADR-117 — world chính cho xác nằm chờ chọn cách hồi sinh, nhưng KHÔNG quá thời gian chờ (+1 tick đệm). */
    private void checkCorpseTime(long characterId, HealthComponent health) {
        if (!health.isDead()) {
            deadTicks.remove(characterId);
            return;
        }
        int deadFor = deadTicks.merge(characterId, 1, Integer::sum);
        if (scenario.respawnsCharacters() && deadFor > MAX_CORPSE_TICKS) {
            violation("nhân vật " + characterId + " nằm 0 máu ở world chính quá " + MAX_CORPSE_TICKS + " tick");
        }
    }

    private void checkHealthAndChi(long characterId, KEntity entity) {
        HealthComponent health = HEALTH.get(entity);
        if (health.max < 1 || health.current < 0 || health.current > health.max) {
            violation("nhân vật " + characterId + " máu " + health.current + "/" + health.max);
        }
        checkCorpseTime(characterId, health);
        ChiComponent chi = CHI.get(entity);
        if (chi.current < 0 || chi.current > chi.max) {
            violation("nhân vật " + characterId + " khí " + chi.current + "/" + chi.max);
        }
    }

    private void checkCooldowns(long id, KEntity entity) {
        float attack = ATTACK_COOLDOWN.get(entity).remainingSeconds;
        if (!(attack >= 0f && attack <= CombatConstants.ATTACK_INTERVAL_SECONDS)) {
            violation("entity " + id + " cooldown đánh thường " + attack);
        }
        if (SKILL_COOLDOWN.has(entity)) {
            for (float remaining : SKILL_COOLDOWN.get(entity).remainingSeconds) {
                if (!(remaining >= 0f && Float.isFinite(remaining))) {
                    violation("nhân vật " + id + " cooldown skill " + remaining);
                }
            }
        }
        checkStatusEffects(id, entity);
    }

    private void checkStatusEffects(long id, KEntity entity) {
        for (StatusEffect effect : STATUS.get(entity).slots) {
            if (effect != null && !(effect.remainingSeconds > 0f && Float.isFinite(effect.magnitude))) {
                violation("entity " + id + " hiệu ứng hết hạn/không hữu hạn còn nằm lại: " + effect.type);
            }
        }
    }

    /** Không bước xa hơn tốc độ chạy tối đa; xác (cùng entity, chết từ tick trước) không trượt ngang. */
    private void checkStep(long characterId, KEntity entity, PositionComponent position) {
        if (!Objects.equals(previousEntity.get(characterId), entity)
                || sink.respawnedThisTick().contains(characterId)) {
            return;
        }
        float step = Math.abs(position.x - previousX.get(characterId));
        if (step > MAX_STEP_PER_TICK + POSITION_EPSILON) {
            violation("nhân vật " + characterId + " dịch " + step + "m trong một tick");
        }
        if (Objects.equals(deadAtPreviousTick.get(characterId), entity)
                && HEALTH.get(entity).isDead()
                && step != 0f) {
            violation("xác nhân vật " + characterId + " trượt " + step + "m");
        }
    }

    private void checkMonster(long monsterId, KEntity entity) {
        PositionComponent position = POSITION.get(entity);
        HealthComponent health = HEALTH.get(entity);
        if (!Float.isFinite(position.x) || !Float.isFinite(position.y)) {
            violation("quái " + monsterId + " toạ độ không hữu hạn: " + describe(position));
        }
        if (health.current <= 0 || health.current > health.max) {
            violation("quái " + monsterId + " còn trong world với máu " + health.current + "/" + health.max);
        }
        AiStateComponent ai = AI_STATE.get(entity);
        AiParamsComponent params = AI_PARAMS.get(entity);
        double fromSpawn = Math.hypot(position.x - ai.spawnX, position.y - ai.spawnY);
        double limit = Math.max(params.leashRadiusMeters(), params.patrolRadiusMeters()) + LEASH_SLACK;
        if (fromSpawn > limit) {
            violation("quái " + monsterId + " cách điểm spawn " + fromSpawn + "m > " + limit);
        }
        checkCooldowns(monsterId, entity);
    }

    private void remember() {
        previousEntity.clear();
        previousX.clear();
        deadAtPreviousTick.clear();
        for (Map.Entry<Long, KEntity> entry : world.characterEntitiesView().entrySet()) {
            previousEntity.put(entry.getKey(), entry.getValue());
            previousX.put(entry.getKey(), POSITION.get(entry.getValue()).x);
            if (HEALTH.get(entry.getValue()).isDead()) {
                deadAtPreviousTick.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String describe(PositionComponent position) {
        return "(" + position.x + ", " + position.y + ", z=" + position.z + ")";
    }
}
