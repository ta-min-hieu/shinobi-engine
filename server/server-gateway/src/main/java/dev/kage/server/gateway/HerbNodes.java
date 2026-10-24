package dev.kage.server.gateway;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.protocol.GatherHerbOutcome;
import dev.kage.world.domain.HerbNode;

/**
 * Trạng thái bụi linh thảo (ADR-098 D2) — RAM, dùng chung mọi kết nối: bụi nào đang hồi tới bao giờ, ai đang hái bụi
 * nào. Mất khi khởi động lại = mọi bụi sẵn sàng. Mọi hàm {@code synchronized} — vài bụi, vài lệnh mỗi phút, không
 * phải đường nóng.
 */
final class HerbNodes {

    /** Khoảng cách tối đa tới bụi để hái (m). */
    static final float REACH_METERS = 2.5f;

    /** Một bụi để liệt kê. */
    record View(HerbNode node, boolean ready, long secondsUntilReady) {}

    private final Map<Long, HerbNode> nodes = new LinkedHashMap<>();
    private final Map<Long, Instant> readyAt = new HashMap<>();
    private final Map<Long, Long> nodeByGatherer = new HashMap<>();
    private final Map<Long, Long> gathererByNode = new HashMap<>();

    /** Nạp danh sách bụi — một lần lúc khởi động. */
    synchronized void load(List<HerbNode> loaded) {
        nodes.clear();
        loaded.forEach(node -> nodes.put(node.id(), node));
    }

    synchronized List<View> list(Instant now) {
        return nodes.values().stream()
                .map(node -> {
                    Instant ready = readyAt.getOrDefault(node.id(), Instant.MIN);
                    boolean free = !ready.isAfter(now) && !gathererByNode.containsKey(node.id());
                    long wait = ready.isAfter(now) ? ready.getEpochSecond() - now.getEpochSecond() : 0L;
                    return new View(node, free, wait);
                })
                .toList();
    }

    synchronized Optional<HerbNode> node(long nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /**
     * Giữ bụi cho {@code characterId} nếu được: bụi có, đủ gần ({@code distance}), đang sẵn, không ai hái, người này
     * không đang hái bụi khác.
     */
    synchronized GatherHerbOutcome tryStart(long characterId, long nodeId, float distance, Instant now) {
        HerbNode node = nodes.get(nodeId);
        if (node == null) {
            return GatherHerbOutcome.GATHER_HERB_OUTCOME_UNKNOWN_NODE;
        }
        if (nodeByGatherer.containsKey(characterId)) {
            return GatherHerbOutcome.GATHER_HERB_OUTCOME_ALREADY_GATHERING;
        }
        if (distance > REACH_METERS) {
            return GatherHerbOutcome.GATHER_HERB_OUTCOME_TOO_FAR;
        }
        if (readyAt.getOrDefault(nodeId, Instant.MIN).isAfter(now) || gathererByNode.containsKey(nodeId)) {
            return GatherHerbOutcome.GATHER_HERB_OUTCOME_NOT_READY;
        }
        nodeByGatherer.put(characterId, nodeId);
        gathererByNode.put(nodeId, characterId);
        return GatherHerbOutcome.GATHER_HERB_OUTCOME_STARTED;
    }

    /** Nhả bụi; {@code gathered} ⇒ đặt hồi {@code respawn_seconds} kể từ {@code now}. */
    synchronized void finish(long characterId, long nodeId, boolean gathered, Instant now) {
        nodeByGatherer.remove(characterId, nodeId);
        gathererByNode.remove(nodeId, characterId);
        HerbNode node = nodes.get(nodeId);
        if (gathered && node != null) {
            readyAt.put(nodeId, now.plusSeconds(node.respawnSeconds()));
        }
    }
}
