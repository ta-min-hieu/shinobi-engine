package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterComponent;
import dev.kage.server.world.component.NpcComponent;

/**
 * Lưới AoI ({@link AreaOfInterestTracker}) chỉ thăm các cặp nằm trong 3×3 ô quanh nhân vật. Điều đó
 * ĐỦ để phát hiện "vào tầm" (trong tầm ⇒ chắc chắn là ô lân cận), nhưng KHÔNG đủ để phát hiện "ra
 * khỏi tầm": một cặp đang thấy nhau có thể ra ngoài 3×3 ô ngay ở lần sync kế tiếp — đi bộ qua đúng
 * mép ô, hoặc hồi sinh dịch chuyển về (0,0). Cặp không được thăm thì không bao giờ được báo
 * despawn: client giữ một bóng ma đứng yên tại chỗ cũ.
 *
 * <p>Gọi thẳng {@link AreaOfInterestTracker#sync} (không qua {@link WorldFacade}) để đặt toạ độ
 * chính xác tới từng mét — đúng kiểu đo mà một world chạy thật không cho phép.
 */
class AreaOfInterestBoundaryTest {

    private static final long CHARACTER_A = 1L;
    private static final long CHARACTER_B = 2L;
    private static final long MONSTER = -1L;
    private static final long NPC = -2L;
    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    private record Despawn(long observer, long despawned) {}

    private static final class DespawnSink implements WorldOutboundSink {
        final List<Despawn> characterDespawns = new ArrayList<>();
        final List<Despawn> monsterDespawns = new ArrayList<>();
        final List<Despawn> npcDespawns = new ArrayList<>();
        final List<Despawn> monsterSpawns = new ArrayList<>();

        @Override
        public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {
            characterDespawns.add(new Despawn(targetCharacterId, despawnedCharacterId));
        }

        @Override
        public void onMonsterDespawned(long targetCharacterId, long monsterId) {
            monsterDespawns.add(new Despawn(targetCharacterId, monsterId));
        }

        @Override
        public void onNpcDespawned(long targetCharacterId, long npcId) {
            npcDespawns.add(new Despawn(targetCharacterId, npcId));
        }

        @Override
        public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {
            monsterSpawns.add(new Despawn(targetCharacterId, monster.monsterId()));
        }

        @Override
        public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot accepted) {}

        @Override
        public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {}

        @Override
        public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {}

        @Override
        public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {}

        @Override
        public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {}

        @Override
        public void onNpcSpawned(long targetCharacterId, WorldOutboundSink.NpcSnapshot npc) {}

        @Override
        public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {}

        @Override
        public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {}

        @Override
        public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {}

        @Override
        public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {}

        @Override
        public void onHealthChanged(long targetCharacterId, WorldOutboundSink.HealthSnapshot health) {}

        @Override
        public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {}

        @Override
        public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {}
    }

    private final EcsWorld world = new EcsWorld();
    private final DespawnSink sink = new DespawnSink();
    private final AreaOfInterestTracker tracker = new AreaOfInterestTracker(sink);
    private final Map<Long, KEntity> characters = new HashMap<>();
    private final Map<Long, KEntity> monsters = new HashMap<>();
    private final Map<Long, KEntity> npcs = new HashMap<>();

    private void sync() {
        tracker.sync(characters, monsters, npcs);
    }

    private KEntity character(long characterId, float x) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = x;
        entity.add(position);
        characters.put(characterId, entity);
        tracker.onAttach(characterId);
        return entity;
    }

    private void monster(long monsterId, float x) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = x;
        entity.add(position);
        HealthComponent health = new HealthComponent();
        health.current = 10;
        health.max = 10;
        entity.add(health);
        MonsterComponent monster = new MonsterComponent();
        monster.monsterCode = "test_monster";
        entity.add(monster);
        monsters.put(monsterId, entity);
    }

    private void npc(long npcId, float x) {
        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        position.x = x;
        entity.add(position);
        NpcComponent npc = new NpcComponent();
        npc.npcCode = "test_npc";
        entity.add(npc);
        npcs.put(npcId, entity);
    }

    private static void moveTo(KEntity entity, float x) {
        POSITION.get(entity).x = x;
    }

    /**
     * Hai người đứng cách nhau đúng 15m (thấy nhau), người thứ hai bước thêm MỘT mét. Ô lưới cạnh
     * 15m: 14,5 ở ô 0, 30,5 ở ô 2 — cặp này không còn là "ô lân cận" của nhau nữa nên vòng sync cũ
     * không bao giờ thăm lại nó.
     */
    @Test
    void shouldDespawnBothWays_whenTwoCharactersWalkApartAcrossTwoGridCells() {
        character(CHARACTER_A, 14.5f);
        KEntity b = character(CHARACTER_B, 29.5f);
        sync();
        assertThat(sink.characterDespawns).isEmpty();

        moveTo(b, 30.5f);
        sync();

        assertThat(sink.characterDespawns)
                .containsExactlyInAnyOrder(
                        new Despawn(CHARACTER_A, CHARACTER_B), new Despawn(CHARACTER_B, CHARACTER_A));
    }

    /** Hồi sinh dịch chuyển nhân vật về (0,0) — quái và NPC ở chỗ chết phải biến khỏi màn hình người đó. */
    @Test
    void shouldDespawnMonsterAndNpc_whenTheObserverIsTeleportedFarAway() {
        KEntity a = character(CHARACTER_A, 50f);
        monster(MONSTER, 52f);
        npc(NPC, 48f);
        sync();
        assertThat(sink.monsterSpawns).containsExactly(new Despawn(CHARACTER_A, MONSTER));

        moveTo(a, 0f);
        sync();

        assertThat(sink.monsterDespawns).containsExactly(new Despawn(CHARACTER_A, MONSTER));
        assertThat(sink.npcDespawns).containsExactly(new Despawn(CHARACTER_A, NPC));
    }

    /**
     * Hệ quả của bóng ma: con quái đã bị coi là "đang thấy" thì lúc người chơi quay lại sẽ không bao
     * giờ được gửi lại {@code MonsterSpawnedEvent}. Phải được gửi lại đúng một lần.
     */
    @Test
    void shouldRespawnMonsterForObserver_whenComingBackAfterTeleport() {
        KEntity a = character(CHARACTER_A, 50f);
        monster(MONSTER, 52f);
        sync();
        moveTo(a, 0f);
        sync();
        moveTo(a, 50f);
        sync();

        assertThat(sink.monsterSpawns)
                .containsExactly(new Despawn(CHARACTER_A, MONSTER), new Despawn(CHARACTER_A, MONSTER));
    }
}
