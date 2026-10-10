package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

import dev.kage.server.world.WorldFacade;

/**
 * Tra World mà một nhân vật ĐANG THẬT SỰ ở trong: hầm ngục nếu đã vào, trận Arena nếu đang đấu, khu
 * vực của world chính nếu không cái nào (Phase 8 lát cắt 1/4, ADR-026 + Phase 9 lát cắt 6).
 *
 * <p><b>Vì sao hỏi HAI lượt (2026-10-10).</b> Ba bảng định tuyến là ba khoá riêng, nên một lượt hỏi
 * không nguyên tử. Mọi lượt dời World đều ghi định tuyến MỚI trước rồi mới gỡ định tuyến cũ — nhưng
 * dời từ world chính VÀO hầm ngục/đấu trường thì định tuyến mới nằm ở bảng được hỏi TRƯỚC: lượt dời
 * chạy lọt giữa hai câu hỏi thì hầm ngục trả "không" (chưa ghi) rồi world chính cũng trả "không" (đã
 * gỡ) — một người đang chơi bình thường bị coi là không thuộc World nào, và gói di chuyển tới đúng
 * lúc đó làm kết nối của họ bị đóng. Hỏi lại một lượt là thấy định tuyến mới; người thật sự không ở
 * World nào (vừa rớt mạng) thì cả hai lượt đều rỗng.
 */
final class WorldRouteLookup {

    private final List<LongFunction<Optional<WorldFacade>>> routesInPriorityOrder;

    /** @param routesInPriorityOrder bảng định tuyến theo thứ tự hỏi — bảng đứng trước thắng */
    WorldRouteLookup(List<LongFunction<Optional<WorldFacade>>> routesInPriorityOrder) {
        this.routesInPriorityOrder = List.copyOf(routesInPriorityOrder);
    }

    /** Thứ tự production: hầm ngục → đấu trường → world chính. */
    static WorldRouteLookup of(WorldGateway worldGateway) {
        return new WorldRouteLookup(List.of(
                worldGateway.dungeonInstanceRegistry()::worldFacadeFor,
                worldGateway.arenaInstanceRegistry()::worldFacadeFor,
                worldGateway.mainWorldChannels()::worldFacadeFor));
    }

    /** World nhân vật đang ở; rỗng nếu không ở World nào (chưa vào, hoặc đã rời hẳn). */
    Optional<WorldFacade> find(long characterId) {
        Optional<WorldFacade> found = findOnce(characterId);
        return found.isPresent() ? found : findOnce(characterId);
    }

    private Optional<WorldFacade> findOnce(long characterId) {
        // Index loop (không for-each/stream) — chạy trên EventLoop cho mọi gói di chuyển.
        for (int i = 0; i < routesInPriorityOrder.size(); i++) {
            Optional<WorldFacade> world = routesInPriorityOrder.get(i).apply(characterId);
            if (world.isPresent()) {
                return world;
            }
        }
        return Optional.empty();
    }
}
