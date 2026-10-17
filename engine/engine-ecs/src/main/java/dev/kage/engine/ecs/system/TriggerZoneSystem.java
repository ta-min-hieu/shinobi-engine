package dev.kage.engine.ecs.system;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.TriggerZoneMembershipComponent;

/**
 * Phát hiện entity vào/ra vùng {@link Aabb} có tên ({@link NamedZone}) — KHÔNG chặn di chuyển
 * (khác {@link TerrainCollisionSystem}). Dùng cho vùng kích hoạt/vùng an toàn/vùng tương tác
 * (PHYSICS.md §7) — chưa có consumer thật nào wire vào lúc viết (không có zone thật, không có
 * entity nào mang {@link TriggerZoneMembershipComponent}), xây trước cơ chế để dùng sau, đúng tinh
 * thần {@link TerrainCollisionSystem} (obstacle rỗng mặc định).
 *
 * <p>Chỉ entity CÓ {@link TriggerZoneMembershipComponent} mới được theo dõi (opt-in — Family yêu
 * cầu cả {@link PositionComponent} lẫn component này).
 */
public final class TriggerZoneSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<TriggerZoneMembershipComponent> MEMBERSHIP =
            ComponentMapper.getFor(TriggerZoneMembershipComponent.class);

    private final BiConsumer<KEntity, String> onZoneEntered;
    private final BiConsumer<KEntity, String> onZoneExited;
    private List<NamedZone> zones = List.of();
    private Set<String> zoneIds = Set.of();
    // Tăng mỗi lần setZones — xem TriggerZoneMembershipComponent#zonesVersionSeen.
    private int zonesVersion;

    public TriggerZoneSystem() {
        this((entity, zoneId) -> {}, (entity, zoneId) -> {});
    }

    /** Callback gọi NGAY trong tick — không dùng cho việc chậm (chờ I/O, DB) (SERVER.md §2). */
    public TriggerZoneSystem(BiConsumer<KEntity, String> onZoneEntered, BiConsumer<KEntity, String> onZoneExited) {
        super(Family.all(PositionComponent.class, TriggerZoneMembershipComponent.class)
                .get());
        this.onZoneEntered = onZoneEntered;
        this.onZoneExited = onZoneExited;
    }

    /**
     * Thay toàn bộ zone hiện tại. Copy phòng thủ (Điều 43), giống {@link TerrainCollisionSystem#setObstacles}.
     *
     * @throws IllegalArgumentException nếu hai zone trùng {@code zoneId} — trạng thái "đang ở trong"
     *     được nhớ THEO {@code zoneId}, nên đứng trong một zone mà ngoài zone kia thì bị báo vào/ra
     *     luân phiên MỖI TICK (quyết định chủ dự án 2026-10-14, README dòng 46(ii)); chặn lúc nạp, không
     *     để chạy sai im lặng
     */
    public void setZones(List<NamedZone> zones) {
        Set<String> seen = new HashSet<>();
        for (NamedZone zone : zones) {
            if (!seen.add(zone.zoneId())) {
                throw new IllegalArgumentException("Hai trigger zone trùng zoneId: " + zone.zoneId());
            }
        }
        this.zones = List.copyOf(zones);
        this.zoneIds = Set.copyOf(seen);
        zonesVersion++;
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        TriggerZoneMembershipComponent membership = MEMBERSHIP.get(entity);
        if (membership.zonesVersionSeen != zonesVersion) {
            leaveRemovedZones(entity, membership);
        }
        if (zones.isEmpty()) {
            return;
        }
        PositionComponent position = POSITION.get(entity);
        // Index loop (không for-each) — tránh Iterator allocation trong game loop (Điều 31).
        for (int i = 0; i < zones.size(); i++) {
            NamedZone zone = zones.get(i);
            boolean inside = zone.bounds().contains(position.x, position.y);
            boolean wasInside = membership.occupiedZoneIds.contains(zone.zoneId());
            if (inside && !wasInside) {
                membership.occupiedZoneIds.add(zone.zoneId());
                onZoneEntered.accept(entity, zone.zoneId());
            } else if (!inside && wasInside) {
                membership.occupiedZoneIds.remove(zone.zoneId());
                onZoneExited.accept(entity, zone.zoneId());
            }
        }
    }

    /**
     * Mã của vùng không còn trong danh sách sau một {@link #setZones} — báo "ra" và xoá (dòng 72(b),
     * chủ dự án duyệt 2026-10-17). Trước đó mã đó nằm lại mãi: vòng lặp chính chỉ duyệt vùng HIỆN CÓ,
     * nên không bao giờ thấy entity "ra" khỏi một vùng đã bị gỡ — mà {@code AiSystem} đọc
     * {@code occupiedZoneIds.isEmpty()} để chặn aggro, tức người đứng trong vùng an toàn bị gỡ được
     * che chở VĨNH VIỄN. Chạy đúng một lần mỗi entity sau mỗi lần đổi danh sách — không chạy mỗi tick.
     */
    private void leaveRemovedZones(KEntity entity, TriggerZoneMembershipComponent membership) {
        membership.zonesVersionSeen = zonesVersion;
        if (membership.occupiedZoneIds.isEmpty()) {
            return;
        }
        membership.occupiedZoneIds.removeIf(zoneId -> {
            if (zoneIds.contains(zoneId)) {
                return false;
            }
            onZoneExited.accept(entity, zoneId);
            return true;
        });
    }
}
