package dev.kage.engine.ecs.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.TriggerZoneMembershipComponent;

class TriggerZoneSystemTest {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<TriggerZoneMembershipComponent> MEMBERSHIP =
            ComponentMapper.getFor(TriggerZoneMembershipComponent.class);

    private static KEntity trackedEntity(EcsWorld world, float startX, float startY) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        entity.add(new TriggerZoneMembershipComponent());
        POSITION.get(entity).x = startX;
        POSITION.get(entity).y = startY;
        return entity;
    }

    /**
     * Dòng 72(b) (2026-10-17): vùng bị gỡ khỏi danh sách trong lúc có người đứng trong đó ⇒ người đó
     * được báo "ra" và mã vùng không nằm lại — trước đó nằm lại mãi và chặn aggro vĩnh viễn.
     */
    @Test
    void shouldExitAndForgetAZone_whenItIsRemovedWhileTheEntityStandsInside() {
        EcsWorld world = new EcsWorld();
        List<String> exited = new ArrayList<>();
        TriggerZoneSystem system = new TriggerZoneSystem((e, zoneId) -> {}, (e, zoneId) -> exited.add(zoneId));
        system.setZones(List.of(
                new NamedZone("old_safe_area", new Aabb(0f, 0f, 10f, 10f)),
                new NamedZone("kept_area", new Aabb(0f, 0f, 10f, 10f))));
        world.addSystem(system);
        KEntity entity = trackedEntity(world, 5f, 5f);
        world.update(1f);

        system.setZones(List.of(new NamedZone("kept_area", new Aabb(0f, 0f, 10f, 10f))));
        world.update(1f);
        world.update(1f);

        assertThat(exited).as("báo ra đúng một lần").containsExactly("old_safe_area");
        assertThat(MEMBERSHIP.get(entity).occupiedZoneIds).containsExactly("kept_area");
    }

    @Test
    void shouldExitEveryZone_whenTheZoneListBecomesEmpty() {
        EcsWorld world = new EcsWorld();
        List<String> exited = new ArrayList<>();
        TriggerZoneSystem system = new TriggerZoneSystem((e, zoneId) -> {}, (e, zoneId) -> exited.add(zoneId));
        system.setZones(List.of(new NamedZone("town_safe_area", new Aabb(0f, 0f, 10f, 10f))));
        world.addSystem(system);
        KEntity entity = trackedEntity(world, 5f, 5f);
        world.update(1f);

        system.setZones(List.of());
        world.update(1f);

        assertThat(exited).containsExactly("town_safe_area");
        assertThat(MEMBERSHIP.get(entity).occupiedZoneIds).isEmpty();
    }

    @Test
    void shouldNotFireCallback_whenZoneListIsEmpty() {
        EcsWorld world = new EcsWorld();
        List<String> entered = new ArrayList<>();
        TriggerZoneSystem system = new TriggerZoneSystem((e, zoneId) -> entered.add(zoneId), (e, zoneId) -> {});
        world.addSystem(system);
        trackedEntity(world, 5f, 5f);

        world.update(1f);

        assertThat(entered).isEmpty();
    }

    @Test
    void shouldFireOnZoneEntered_whenEntityStartsInsideZone() {
        EcsWorld world = new EcsWorld();
        List<String> entered = new ArrayList<>();
        TriggerZoneSystem system = new TriggerZoneSystem((e, zoneId) -> entered.add(zoneId), (e, zoneId) -> {});
        system.setZones(List.of(new NamedZone("town_safe_area", new Aabb(0f, 0f, 10f, 10f))));
        world.addSystem(system);
        KEntity entity = trackedEntity(world, 5f, 5f);

        world.update(1f);

        assertThat(entered).containsExactly("town_safe_area");
        assertThat(MEMBERSHIP.get(entity).occupiedZoneIds).containsExactly("town_safe_area");
    }

    @Test
    void shouldNotFireOnZoneEnteredAgain_whenEntityStaysInsideZoneAcrossTicks() {
        EcsWorld world = new EcsWorld();
        List<String> entered = new ArrayList<>();
        TriggerZoneSystem system = new TriggerZoneSystem((e, zoneId) -> entered.add(zoneId), (e, zoneId) -> {});
        system.setZones(List.of(new NamedZone("town_safe_area", new Aabb(0f, 0f, 10f, 10f))));
        world.addSystem(system);
        trackedEntity(world, 5f, 5f);

        world.update(1f);
        world.update(1f);
        world.update(1f);

        assertThat(entered).containsExactly("town_safe_area");
    }

    @Test
    void shouldFireOnZoneExited_whenEntityMovesOutOfZone() {
        EcsWorld world = new EcsWorld();
        List<String> exited = new ArrayList<>();
        TriggerZoneSystem system = new TriggerZoneSystem((e, zoneId) -> {}, (e, zoneId) -> exited.add(zoneId));
        system.setZones(List.of(new NamedZone("town_safe_area", new Aabb(0f, 0f, 10f, 10f))));
        world.addSystem(system);
        KEntity entity = trackedEntity(world, 5f, 5f);
        world.update(1f); // vào vùng trước

        POSITION.get(entity).x = 100f; // ra khỏi vùng
        world.update(1f);

        assertThat(exited).containsExactly("town_safe_area");
        assertThat(MEMBERSHIP.get(entity).occupiedZoneIds).isEmpty();
    }

    @Test
    void shouldNotTrackEntity_whenMissingMembershipComponent() {
        EcsWorld world = new EcsWorld();
        List<String> entered = new ArrayList<>();
        TriggerZoneSystem system = new TriggerZoneSystem((e, zoneId) -> entered.add(zoneId), (e, zoneId) -> {});
        system.setZones(List.of(new NamedZone("town_safe_area", new Aabb(0f, 0f, 10f, 10f))));
        world.addSystem(system);
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        POSITION.get(entity).x = 5f;
        POSITION.get(entity).y = 5f;

        world.update(1f);

        assertThat(entered).isEmpty();
    }

    /**
     * Trạng thái "đang ở trong" nhớ theo {@code zoneId}: hai zone trùng id thì đứng trong một, ngoài cái
     * kia là bị báo vào/ra luân phiên mỗi tick. Chặn lúc nạp (README dòng 46(ii), 2026-10-14).
     */
    @Test
    void shouldRejectZones_whenTwoShareAZoneId() {
        TriggerZoneSystem system = new TriggerZoneSystem();
        List<NamedZone> duplicated = List.of(
                new NamedZone("town_safe_area", new Aabb(0f, 0f, 10f, 10f)),
                new NamedZone("town_safe_area", new Aabb(20f, 0f, 10f, 10f)));

        assertThatThrownBy(() -> system.setZones(duplicated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("town_safe_area");
    }
}
