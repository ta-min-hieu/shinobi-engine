package dev.kage.server.world;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Bất biến AoI của bộ kiểm thuộc tính, kiểm ngay sau mỗi tick có sync
 * ({@link WorldConstants#AOI_SYNC_INTERVAL_TICKS}): với mỗi nhân vật đang gắn, sổ "đang thấy" dựng từ
 * SỰ KIỆN ({@link InvariantCheckingSink#seenBy}) phải khớp khoảng cách THẬT — trong
 * {@code R − ε} thì phải thấy, ngoài {@code R + ε} thì không được thấy. Cặp nhân vật–nhân vật tự
 * kiểm cả hai chiều vì vòng lặp đi qua mọi người quan sát.
 *
 * <p>Vị trí đọc sau tick — đúng vị trí mà sync của chính tick đó đã dùng (sync là bước cuối của tick).
 */
final class AreaOfInterestInvariant {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final double EPSILON = 0.01;

    private final String prefix;
    private final long observerId;
    private final PositionComponent from;
    private final Set<Long> seen;
    private final List<String> violations;

    private AreaOfInterestInvariant(
            long tickNumber, Map.Entry<Long, KEntity> observer, Set<Long> seen, List<String> violations) {
        this.prefix = "tick " + tickNumber + ": AoI của " + observer.getKey();
        this.observerId = observer.getKey();
        this.from = POSITION.get(observer.getValue());
        this.seen = seen;
        this.violations = violations;
    }

    static boolean isSyncTick(long tickNumber) {
        return tickNumber % WorldConstants.AOI_SYNC_INTERVAL_TICKS == 0;
    }

    static void check(long tickNumber, WorldFacade world, InvariantCheckingSink sink) {
        for (Map.Entry<Long, KEntity> observer : world.characterEntitiesView().entrySet()) {
            AreaOfInterestInvariant one = new AreaOfInterestInvariant(
                    tickNumber, observer, sink.seenBy(observer.getKey()), sink.violations());
            one.checkGroup(world.characterEntitiesView());
            one.checkGroup(world.monsterEntitiesView());
            one.checkGroup(world.npcEntitiesView());
        }
    }

    /**
     * Mọi tick (không chỉ tick sync): client không được còn giữ một entity đã biến khỏi world — quái
     * chết và người rời world được báo despawn NGAY trong tick đó, không chờ sync.
     */
    static void checkNoStaleEntries(long tickNumber, WorldFacade world, InvariantCheckingSink sink) {
        for (long observerId : world.characterEntitiesView().keySet()) {
            for (long entityId : sink.seenBy(observerId)) {
                boolean exists = world.characterEntitiesView().containsKey(entityId)
                        || world.monsterEntitiesView().containsKey(entityId)
                        || world.npcEntitiesView().containsKey(entityId);
                if (!exists) {
                    sink.violations()
                            .add("tick " + tickNumber + ": " + observerId + " vẫn giữ " + entityId
                                    + " — entity đó đã rời world mà không có despawn");
                }
            }
        }
    }

    private void checkGroup(Map<Long, KEntity> group) {
        for (Map.Entry<Long, KEntity> other : group.entrySet()) {
            if (other.getKey() == observerId) {
                continue;
            }
            PositionComponent to = POSITION.get(other.getValue());
            double distance = Math.hypot((double) from.x - to.x, (double) from.y - to.y);
            boolean sees = seen.contains(other.getKey());
            if (distance <= WorldConstants.AOI_RADIUS_METERS - EPSILON && !sees) {
                violations.add(prefix + " KHÔNG thấy " + other.getKey() + " ở cách " + distance + "m");
            } else if (distance > WorldConstants.AOI_RADIUS_METERS + EPSILON && sees) {
                violations.add(prefix + " VẪN thấy " + other.getKey() + " ở cách " + distance + "m");
            }
        }
    }
}
