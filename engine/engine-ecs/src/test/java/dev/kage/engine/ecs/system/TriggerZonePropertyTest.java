package dev.kage.engine.ecs.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.TriggerZoneMembershipComponent;

/**
 * Bộ kiểm dựa trên MÔ HÌNH cho {@link TriggerZoneSystem} (2026-10-10): entity dịch chuyển ngẫu nhiên
 * (kể cả nhảy cóc thẳng vào giữa vùng, đứng đúng trên biên) giữa các vùng chồng lấn có mã KHÁC NHAU;
 * entity sinh ra và bị gỡ giữa chừng. Sau MỖI tick, với mỗi entity được theo dõi:
 *
 * <ul>
 *   <li>tập vùng đang đứng ({@code occupiedZoneIds}) = đúng tập vùng chứa vị trí hiện tại;
 *   <li>sự kiện vào/ra của tick = đúng hiệu giữa tập tick này và tập tick trước, mỗi cặp một lần;
 *   <li>entity không mang {@link TriggerZoneMembershipComponent} không bao giờ sinh sự kiện.
 * </ul>
 *
 * <p>Chạy lại một seed đỏ: {@code -Dkage.ecsFuzz.seed=<seed> -Dkage.ecsFuzz.seeds=1}.
 */
class TriggerZonePropertyTest {

    private static final long BASE_SEED = Long.getLong("kage.ecsFuzz.seed", 0x20E5L);
    private static final int SEEDS = Integer.getInteger("kage.ecsFuzz.seeds", 200);
    private static final int TICKS_PER_SEED = 400;
    private static final float FIXED_DT = 1f / 60f;
    private static final float WORLD_HALF_SIZE = 12f;
    private static final float GRID = 0.5f;

    /** Một entity được theo dõi, kèm tập vùng nó đứng trong ở tick trước theo mô hình. */
    private static final class Tracked {
        final KEntity entity;
        final PositionComponent position = new PositionComponent();
        final TriggerZoneMembershipComponent membership = new TriggerZoneMembershipComponent();
        Set<String> previousZones = new HashSet<>();

        Tracked(KEntity entity) {
            this.entity = entity;
        }
    }

    private final List<NamedZone> zones = new ArrayList<>();
    private final List<Tracked> tracked = new ArrayList<>();
    private final Map<Long, Set<String>> entered = new HashMap<>();
    private final Map<Long, Set<String>> exited = new HashMap<>();
    private final List<String> duplicateEvents = new ArrayList<>();
    private int enterEvents;
    private int exitEvents;

    @Test
    @Timeout(60)
    void shouldReportExactlyTheZoneChanges_underRandomMovement() {
        for (int i = 0; i < SEEDS; i++) {
            runSeed(BASE_SEED + i);
        }
        System.out.println("[zoneFuzz] sự kiện vào=" + enterEvents + " ra=" + exitEvents);
        assertThat(enterEvents).as("phải có lượt vào vùng").isPositive();
        assertThat(exitEvents).as("phải có lượt ra vùng").isPositive();
    }

    private void runSeed(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        zones.clear();
        tracked.clear();
        int zoneCount = 1 + random.nextInt(5);
        for (int n = 0; n < zoneCount; n++) {
            float minX = coordinate(random);
            float minY = coordinate(random);
            zones.add(new NamedZone(
                    "zone-" + n, new Aabb(minX, minY, minX + 1f + random.nextInt(10), minY + 1f + random.nextInt(10))));
        }
        EcsWorld world = new EcsWorld();
        TriggerZoneSystem system = new TriggerZoneSystem(
                (entity, zoneId) -> record(entered, entity, zoneId),
                (entity, zoneId) -> record(exited, entity, zoneId));
        system.setZones(zones);
        world.addSystem(system);
        PositionComponent untrackedPosition = new PositionComponent();
        KEntity untracked = world.createEntity().add(untrackedPosition);

        for (int tick = 0; tick < TICKS_PER_SEED; tick++) {
            churn(random, world);
            untrackedPosition.x = coordinate(random);
            untrackedPosition.y = coordinate(random);
            for (Tracked each : tracked) {
                move(random, each.position);
            }
            entered.clear();
            exited.clear();

            world.update(FIXED_DT);

            String description = "seed=" + seed + " tick=" + tick + " vùng=" + zones;
            assertThat(duplicateEvents)
                    .as("sự kiện lặp trong một tick: " + description)
                    .isEmpty();
            assertThat(entered.containsKey(untracked.id()) || exited.containsKey(untracked.id()))
                    .as("entity không đăng ký vẫn sinh sự kiện: " + description)
                    .isFalse();
            for (Tracked each : tracked) {
                check(each, description);
            }
        }
    }

    private void record(Map<Long, Set<String>> events, KEntity entity, String zoneId) {
        if (!events.computeIfAbsent(entity.id(), id -> new HashSet<>()).add(zoneId)) {
            duplicateEvents.add("#" + entity.id() + " " + zoneId);
        }
    }

    /** Sinh thêm hoặc gỡ bớt entity được theo dõi — entity mới phải nhận sự kiện "vào" ngay tick đầu. */
    private void churn(SplittableRandom random, EcsWorld world) {
        if (tracked.size() < 6 && random.nextInt(10) == 0) {
            Tracked fresh = new Tracked(world.createEntity());
            fresh.entity.add(fresh.position).add(fresh.membership);
            fresh.position.x = coordinate(random);
            fresh.position.y = coordinate(random);
            tracked.add(fresh);
        } else if (!tracked.isEmpty() && random.nextInt(40) == 0) {
            world.removeEntity(tracked.remove(random.nextInt(tracked.size())).entity);
        }
    }

    private static void move(SplittableRandom random, PositionComponent position) {
        switch (random.nextInt(4)) {
            case 0 -> {
                position.x = coordinate(random);
                position.y = coordinate(random);
            }
            case 1 -> position.x += (random.nextFloat() - 0.5f) * 2f;
            case 2 -> position.y += (random.nextFloat() - 0.5f) * 2f;
            default -> {
                // đứng yên
            }
        }
    }

    private void check(Tracked each, String description) {
        Set<String> expectedZones = new HashSet<>();
        for (NamedZone zone : zones) {
            if (zone.bounds().contains(each.position.x, each.position.y)) {
                expectedZones.add(zone.zoneId());
            }
        }
        Set<String> expectedEntered = new HashSet<>(expectedZones);
        expectedEntered.removeAll(each.previousZones);
        Set<String> expectedExited = new HashSet<>(each.previousZones);
        expectedExited.removeAll(expectedZones);
        String who = "#" + each.entity.id() + " tại (" + each.position.x + "," + each.position.y + ") " + description;

        assertThat(each.membership.occupiedZoneIds)
                .as("tập vùng đang đứng: " + who)
                .isEqualTo(expectedZones);
        assertThat(entered.getOrDefault(each.entity.id(), Set.of()))
                .as("sự kiện vào: " + who)
                .isEqualTo(expectedEntered);
        assertThat(exited.getOrDefault(each.entity.id(), Set.of()))
                .as("sự kiện ra: " + who)
                .isEqualTo(expectedExited);
        enterEvents += expectedEntered.size();
        exitEvents += expectedExited.size();
        each.previousZones = expectedZones;
    }

    private static float coordinate(SplittableRandom random) {
        float value = (random.nextFloat() * 2f - 1f) * WORLD_HALF_SIZE;
        return random.nextBoolean() ? Math.round(value / GRID) * GRID : value;
    }
}
