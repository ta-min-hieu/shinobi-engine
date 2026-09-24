package dev.kage.server.gateway;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;

/**
 * Một cú đổi World của MỘT nhân vật — nơi DUY NHẤT giữ thứ tự của ADR-051 D2, để hầm ngục và đấu
 * trường không phải tự nhớ lại nó mỗi nơi một kiểu.
 *
 * <p><b>Thứ tự bắt buộc: detach khỏi World cũ → ghi {@code WorldChangedEvent} vào kênh → xếp hàng
 * attach vào World mới.</b> Client dọn sạch thực thể khi nhận event; nếu event tới SAU spawn đầu
 * tiên của World mới, cú dọn xoá mất boss/đồng đội vừa nhận và server không phát lại (tập "đang
 * thấy" phía server đã có họ) — cùng lý do ADR-047 D3. Event ghi trên CÙNG luồng, TRƯỚC khi lệnh
 * attach vào hộp thư World ⇒ happens-before với mọi gói tick thread của World mới ghi sau đó.
 *
 * <p>Lệnh detach khỏi World cũ chỉ được XẾP HÀNG — World cũ có thể còn phát nốt vài gói của tick
 * đang chạy dở sau event. Đó là giới hạn có sẵn (đã có từ trước ADR-051), không phải thứ lớp này
 * gây ra.
 */
final class WorldTransfer {

    private final ConnectedCharacterRegistry registry;

    WorldTransfer(ConnectedCharacterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Dời {@code characterId} sang World mới, đúng thứ tự của javadoc lớp.
     *
     * @param detachFromOldWorld không làm gì nếu caller đã detach từ trước (vd. chốt trận đấu gỡ cả
     *     hai người khỏi World đấu trường rồi mới đưa từng người về)
     */
    void move(long characterId, Runnable detachFromOldWorld, WorldChangedEvent event, Runnable attachToNewWorld) {
        detachFromOldWorld.run();
        registry.sendToCharacterIfOnline(characterId, Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE, 0, event.toByteArray());
        attachToNewWorld.run();
    }

    /**
     * @param maxHp máu mà lệnh attach vào World mới sẽ đặt — attach luôn cho máu đầy nên
     *     {@code current_hp = max_hp}
     */
    static WorldChangedEvent event(WorldKind kind, WorldChangeReason reason, PositionSnapshot at, int maxHp) {
        return WorldChangedEvent.newBuilder()
                .setWorldKind(kind)
                .setReason(reason)
                .setX(at.x())
                .setY(at.y())
                .setCurrentHp(maxHp)
                .setMaxHp(maxHp)
                .build();
    }
}
