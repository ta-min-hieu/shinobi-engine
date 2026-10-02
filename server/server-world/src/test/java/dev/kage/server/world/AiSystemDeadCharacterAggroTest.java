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
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * Vòng quét aggro không được chọn một cái XÁC.
 *
 * <p>World hầm ngục/Arena không tự hồi sinh: nhân vật chết nằm lại 0 máu và vẫn ở trong
 * {@code entitiesByCharacterId} ({@code WorldFacade.respawnOrHaltCorpse}). Trước bản sửa, vòng quét
 * chọn người GẦN NHẤT mà không hỏi còn sống hay không, nên:
 *
 * <ul>
 *   <li>cái xác đứng gần hơn sẽ CHE người còn sống ở xa hơn — con quái không bao giờ thấy họ;
 *   <li>con quái lật IDLE → CHASE (cái xác) → RETURN (vì mục tiêu đã chết) → IDLE … mãi mãi, và
 *       mỗi lần "về tới nhà" lại lành đầy máu (ADR-046 D4) ngay trong lúc người còn sống đang đánh nó.
 * </ul>
 */
class AiSystemDeadCharacterAggroTest {

    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);

    private static final float TICK_SECONDS = 1f / 60f;
    private static final float AGGRO_RADIUS = 6f;
    private static final long DEAD_CHARACTER_ID = 1L;
    private static final long ALIVE_CHARACTER_ID = 2L;

    private static KEntity buildIdleMonster(EcsWorld world) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        entity.add(new VelocityComponent());
        entity.add(new AiStateComponent());
        entity.add(new AiParamsComponent(AGGRO_RADIUS, 20f, 3f, 1.5f, 0f, 3f, false));
        entity.add(new TargetComponent());
        HealthComponent health = new HealthComponent();
        health.current = 50;
        health.max = 50;
        entity.add(health);
        MonsterIdComponent id = new MonsterIdComponent();
        id.monsterId = -1L;
        entity.add(id);
        return entity;
    }

    private static KEntity buildCharacter(EcsWorld world, long characterId, float x, int hp) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = x;
        entity.add(position);
        CharacterIdComponent id = new CharacterIdComponent();
        id.characterId = characterId;
        entity.add(id);
        HealthComponent health = new HealthComponent();
        health.current = hp;
        health.max = 100;
        entity.add(health);
        return entity;
    }

    @Test
    void shouldAggroTheLivingCharacter_whenADeadOneLiesCloser() {
        EcsWorld world = new EcsWorld();
        KEntity monster = buildIdleMonster(world);
        KEntity corpse = buildCharacter(world, DEAD_CHARACTER_ID, 1f, 0);
        KEntity alive = buildCharacter(world, ALIVE_CHARACTER_ID, 4f, 100);
        Map<Long, KEntity> byId = new HashMap<>();
        byId.put(DEAD_CHARACTER_ID, corpse);
        byId.put(ALIVE_CHARACTER_ID, alive);
        world.addSystem(new AiSystem(() -> List.of(corpse, alive), byId::get));

        world.update(TICK_SECONDS);

        assertThat(TARGET.get(monster).targetEntityId).isEqualTo(ALIVE_CHARACTER_ID);
        assertThat(AI_STATE.get(monster).state).isEqualTo(AiState.CHASE);
    }

    @Test
    void shouldNotFlipBetweenChaseAndReturn_whenTheOnlyCharacterInRangeIsDead() {
        EcsWorld world = new EcsWorld();
        KEntity monster = buildIdleMonster(world);
        KEntity corpse = buildCharacter(world, DEAD_CHARACTER_ID, 1f, 0);
        world.addSystem(new AiSystem(() -> List.of(corpse), id -> id == DEAD_CHARACTER_ID ? corpse : null));

        for (int tick = 0; tick < 10; tick++) {
            world.update(TICK_SECONDS);
            assertThat(AI_STATE.get(monster).state)
                    .as("tick %d — quái không được đuổi theo cái xác", tick)
                    .isIn(AiState.IDLE, AiState.PATROL);
            assertThat(TARGET.get(monster).targetEntityId).isZero();
        }
    }
}
