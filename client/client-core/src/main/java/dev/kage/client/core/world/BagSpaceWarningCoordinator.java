package dev.kage.client.core.world;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cảnh báo sắp đầy túi (ADR-246) — mỗi lần nhận danh sách túi, còn ≤ {@value #WARN_FREE_SLOTS} ô trống thì nhắc một
 * dòng (kèm gợi ý {@code /tubanrac}); nhắc một lần cho tới khi túi rộng ra lại. Đọc cùng gói 508 với túi đồ
 * ({@code PanelHub} đưa gói cho mọi luồng).
 */
final class BagSpaceWarningCoordinator implements WindowFlow {

    static final int WARN_FREE_SLOTS = 2;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private boolean warned;

    BagSpaceWarningCoordinator(ChatCoordinator chat) {
        this.chat = chat;
    }

    /** Số ô trống; sức chứa chưa biết (0) ⇒ {@link Integer#MAX_VALUE} (không cảnh báo). Tĩnh để test. */
    static int freeSlots(int used, int capacity) {
        return capacity <= 0 ? Integer.MAX_VALUE : Math.max(0, capacity - used);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE) {
            return false;
        }
        ListInventoryResponse response = ListInventoryResponse.parseFrom(frame.payload());
        int free = freeSlots(response.getItemsCount(), response.getCapacity());
        if (free > WARN_FREE_SLOTS) {
            warned = false;
        } else if (!warned) {
            warned = true;
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "bagspace.notice", free));
        }
        return true;
    }
}
