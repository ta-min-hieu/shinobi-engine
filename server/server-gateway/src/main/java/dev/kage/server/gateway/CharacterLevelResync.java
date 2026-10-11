package dev.kage.server.gateway;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.server.world.WorldFacade;

/**
 * Đọc lại cấp từ DB và báo cho World nhân vật VỪA được dời vào (quyết định chủ dự án 2026-10-11) — bịt
 * khe "lên cấp đúng lúc đang dời World" mà {@link CharacterLevelSync} đã ghi là giới hạn.
 *
 * <p><b>Khe đó.</b> Lệnh attach vào World mới mang cấp đọc từ DB TRƯỚC khi dời. Một cú lên cấp commit
 * sau lần đọc đó nhưng trước khi nhân vật có mặt ở World mới thì đi lạc: lệnh nâng cấp của nó tới World
 * cũ (đã rời) hoặc tới World mới khi thực thể chưa được gắn — cả hai đều bị bỏ qua. World mới giữ cấp
 * cũ tới lần lên cấp / dời World / đăng nhập kế.
 *
 * <p><b>Vì sao đọc lại SAU khi dời là đủ.</b> Lệnh này xếp vào hàng đợi của World mới SAU lệnh attach,
 * nên chạy khi thực thể đã có mặt. Cú lên cấp nào commit trước lần đọc này thì lần đọc thấy; cú nào
 * commit sau thì lúc đó định tuyến đã trỏ về World mới và {@link CharacterLevelSync} tự đưa tới nơi.
 * World chỉ NÂNG cấp, nên lệnh thừa (cấp không đổi) không làm gì.
 *
 * <p>Chạm DB — chỉ gọi trên luồng blocking, NGOÀI mọi khoá của registry.
 */
final class CharacterLevelResync {

    private CharacterLevelResync() {}

    /** Nhân vật đã bị xoá khỏi DB giữa chừng thì không có gì để báo. */
    static void afterMoveInto(WorldFacade world, CharacterFacade characterFacade, long characterId) {
        characterFacade
                .findById(characterId)
                .map(Character::level)
                .ifPresent(level -> world.enqueueCharacterLevel(characterId, level));
    }
}
