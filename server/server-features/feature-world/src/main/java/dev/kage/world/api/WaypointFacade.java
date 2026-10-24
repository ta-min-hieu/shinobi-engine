package dev.kage.world.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.world.domain.Waypoint;
import dev.kage.world.domain.WaypointPort;

/**
 * Điểm truyền tống (ADR-100). Danh sách điểm nạp một lần; điểm đã mở của từng nhân vật giữ trong bộ nhớ đệm (luồng mở
 * điểm quét mỗi 5 giây — không chạm DB nếu không có gì mới). Chạm DB — chỉ gọi trên Virtual Thread.
 */
public final class WaypointFacade {

    /** Mã vật phẩm Truyền Tống Phù. */
    public static final String TALISMAN_CODE = "teleport_talisman";

    /** Composition không có điểm truyền tống (test). */
    public static final WaypointFacade DISABLED = new WaypointFacade(new WaypointPort() {
        @Override
        public List<Waypoint> listWaypoints() {
            return List.of();
        }

        @Override
        public Set<String> unlockedBy(long characterId) {
            return Set.of();
        }

        @Override
        public boolean unlock(long characterId, String waypointCode) {
            return false;
        }

        @Override
        public boolean consumeTalisman(long characterId, String talismanCode) {
            return false;
        }

        @Override
        public void refundTalisman(long characterId, String talismanCode) {
            // không có túi đồ nào
        }
    });

    private final WaypointPort port;
    private final Map<Long, Set<String>> unlocked = new ConcurrentHashMap<>();
    private volatile List<Waypoint> waypoints;

    /** Dựng ở composition root. */
    public WaypointFacade(WaypointPort port) {
        this.port = port;
    }

    /** Mọi điểm (nạp lần đầu gọi). */
    public List<Waypoint> waypoints() {
        List<Waypoint> loaded = waypoints;
        if (loaded == null) {
            loaded = List.copyOf(port.listWaypoints());
            waypoints = loaded;
        }
        return loaded;
    }

    /** Điểm theo mã. */
    public Optional<Waypoint> find(String code) {
        return waypoints().stream().filter(w -> w.code().equals(code)).findFirst();
    }

    /** Điểm này đã mở với nhân vật chưa (kể cả điểm mở sẵn). */
    public boolean isOpen(long characterId, Waypoint waypoint) {
        return waypoint.alwaysOpen() || unlockedSet(characterId).contains(waypoint.code());
    }

    /**
     * Nhân vật đang đứng ở {@code x}: mở mọi điểm chưa mở trong bán kính.
     *
     * @return điểm VỪA mở lần này (để báo)
     */
    public List<Waypoint> visit(long characterId, float x) {
        Set<String> known = unlockedSet(characterId);
        List<Waypoint> opened = waypoints().stream()
                .filter(w -> !w.alwaysOpen() && !known.contains(w.code()) && w.unlocksAt(x))
                .filter(w -> port.unlock(characterId, w.code()))
                .toList();
        opened.forEach(w -> known.add(w.code()));
        return opened;
    }

    /** Quên bộ nhớ đệm khi nhân vật rời world. */
    public void forget(long characterId) {
        unlocked.remove(characterId);
    }

    /** Trừ một Truyền Tống Phù; {@code false} nếu không có. */
    public boolean consumeTalisman(long characterId) {
        return port.consumeTalisman(characterId, TALISMAN_CODE);
    }

    /** Trả lại một phù (dời hỏng sau khi đã trừ). */
    public void refundTalisman(long characterId) {
        port.refundTalisman(characterId, TALISMAN_CODE);
    }

    private Set<String> unlockedSet(long characterId) {
        return unlocked.computeIfAbsent(characterId, id -> {
            Set<String> set = ConcurrentHashMap.newKeySet();
            set.addAll(port.unlockedBy(id));
            return set;
        });
    }
}
