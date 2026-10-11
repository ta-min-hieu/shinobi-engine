package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * Boss mất mục tiêu thì nhắm người khác đang đánh nó, thay vì về nhà hồi đầy (README dòng 37i, chủ dự
 * án chốt 2026-10-11). Trước luật này, một thành viên gục giữa trận boss là cả nhóm mất trắng công
 * đánh: boss quay về điểm spawn và lành đầy máu dù đồng đội vẫn đang đứng đánh.
 */
class BossRetargetingTest {

    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);

    private static final float TICK_SECONDS = 1f / 60f;
    private static final long FIRST_TARGET = 1L;
    private static final long TEAMMATE = 2L;

    private final EcsWorld world = new EcsWorld();
    private final Map<Long, KEntity> charactersById = new HashMap<>();

    @Test
    void shouldTurnOnTheTeammate_whenTheBossTargetDies() {
        KEntity boss = buildMonster(true);
        KEntity firstTarget = buildCharacter(FIRST_TARGET);
        KEntity teammate = buildCharacter(TEAMMATE);
        startAi();

        Retaliation.onAttacked(boss, teammate); // đồng đội ra đòn trong lúc boss đang đánh người kia
        HEALTH.get(firstTarget).current = 0;
        world.update(TICK_SECONDS);

        assertThat(TARGET.get(boss).targetEntityId).isEqualTo(TEAMMATE);
        assertThat(AI_STATE.get(boss).state).isEqualTo(AiState.CHASE);
    }

    /** Đối chứng: không còn ai đánh thì boss vẫn về nhà như cũ (ADR-046 D4). */
    @Test
    void shouldStillGoHome_whenNobodyElseWasFightingTheBoss() {
        KEntity boss = buildMonster(true);
        KEntity firstTarget = buildCharacter(FIRST_TARGET);
        buildCharacter(TEAMMATE); // đứng đó nhưng chưa từng ra đòn
        startAi();

        HEALTH.get(firstTarget).current = 0;
        world.update(TICK_SECONDS);

        assertThat(TARGET.get(boss).targetEntityId).isZero();
        assertThat(AI_STATE.get(boss).state).isEqualTo(AiState.RETURN);
    }

    /** Người đã thôi đánh từ lâu không kéo boss theo: trí nhớ chỉ dài {@code OTHER_ATTACKER_MEMORY_SECONDS}. */
    @Test
    void shouldStillGoHome_whenTheTeammateStoppedFightingLongAgo() {
        KEntity boss = buildMonster(true);
        KEntity firstTarget = buildCharacter(FIRST_TARGET);
        KEntity teammate = buildCharacter(TEAMMATE);
        startAi();

        Retaliation.onAttacked(boss, teammate);
        AI_STATE.get(boss).secondsSinceOtherAttack = BossConstants.OTHER_ATTACKER_MEMORY_SECONDS + 1f;
        HEALTH.get(firstTarget).current = 0;
        world.update(TICK_SECONDS);

        assertThat(AI_STATE.get(boss).state).isEqualTo(AiState.RETURN);
    }

    /** Đồng đội cũng đã gục thì không có ai để nhắm. */
    @Test
    void shouldStillGoHome_whenTheTeammateIsDeadToo() {
        KEntity boss = buildMonster(true);
        KEntity firstTarget = buildCharacter(FIRST_TARGET);
        KEntity teammate = buildCharacter(TEAMMATE);
        startAi();

        Retaliation.onAttacked(boss, teammate);
        HEALTH.get(firstTarget).current = 0;
        HEALTH.get(teammate).current = 0;
        world.update(TICK_SECONDS);

        assertThat(AI_STATE.get(boss).state).isEqualTo(AiState.RETURN);
    }

    /** Chủ dự án chỉ quyết cho BOSS — quái thường giữ nguyên hành vi. */
    @Test
    void shouldNotApplyToOrdinaryMonsters() {
        KEntity monster = buildMonster(false);
        KEntity firstTarget = buildCharacter(FIRST_TARGET);
        KEntity teammate = buildCharacter(TEAMMATE);
        startAi();

        Retaliation.onAttacked(monster, teammate);
        HEALTH.get(firstTarget).current = 0;
        world.update(TICK_SECONDS);

        assertThat(TARGET.get(monster).targetEntityId).isZero();
        assertThat(AI_STATE.get(monster).state).isEqualTo(AiState.RETURN);
    }

    private void startAi() {
        world.addSystem(new AiSystem(() -> List.copyOf(charactersById.values()), charactersById::get));
    }

    /** Con quái đang ĐÁNH {@link #FIRST_TARGET}, đứng ngay tại điểm spawn của nó. */
    private KEntity buildMonster(boolean boss) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        entity.add(new VelocityComponent());
        AiStateComponent ai = new AiStateComponent();
        ai.state = AiState.ATTACK;
        entity.add(ai);
        entity.add(new AiParamsComponent(6f, 20f, 3f, 1.5f, 0f, 3f, true));
        TargetComponent target = new TargetComponent();
        target.targetEntityId = FIRST_TARGET;
        entity.add(target);
        HealthComponent health = new HealthComponent();
        health.current = 100;
        health.max = 320;
        entity.add(health);
        MonsterIdComponent id = new MonsterIdComponent();
        id.monsterId = -1L;
        entity.add(id);
        if (boss) {
            entity.add(new BossAiComponent());
        }
        return entity;
    }

    private KEntity buildCharacter(long characterId) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = 1f;
        entity.add(position);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = characterId;
        entity.add(id);
        HealthComponent health = new HealthComponent();
        health.current = 100;
        health.max = 100;
        entity.add(health);
        charactersById.put(characterId, entity);
        return entity;
    }
}
