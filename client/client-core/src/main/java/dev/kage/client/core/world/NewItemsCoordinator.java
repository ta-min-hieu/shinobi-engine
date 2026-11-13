package dev.kage.client.core.world;

import java.util.function.BooleanSupplier;

import com.badlogic.gdx.utils.LongArray;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nhãn "mới" cho đồ vừa nhặt (ADR-326) — mẫu vật phẩm vừa vào túi được đánh dấu "(mới)" trong túi đồ cho tới khi người
 * chơi mở túi xem rồi đóng lại. Theo dõi đóng/mở bằng mỗi gói đến (không cần nhịp khung riêng), không cấp phát.
 */
final class NewItemsCoordinator implements WindowFlow {

    private final LongArray fresh = new LongArray();
    private BooleanSupplier bagOpen = () -> false;
    private boolean seenOpen;

    /** Nối trạng thái mở túi. */
    void useBag(BooleanSupplier open) {
        this.bagOpen = open;
    }

    /** Mẫu này có đang mang nhãn "mới" không. */
    boolean isNew(long itemTemplateId) {
        return fresh.contains(itemTemplateId);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        trackBag();
        if (frame.header().opcode() == Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE) {
            long template = ItemGrantedEvent.parseFrom(frame.payload()).getItemTemplateId();
            if (!fresh.contains(template)) {
                fresh.add(template);
            }
        }
        return false; // không giành gói — túi đồ vẫn xử lý như thường
    }

    /** Túi vừa đóng lại sau khi đã mở ⇒ người chơi đã xem ⇒ bỏ hết nhãn. */
    private void trackBag() {
        if (bagOpen.getAsBoolean()) {
            seenOpen = true;
        } else if (seenOpen) {
            seenOpen = false;
            fresh.clear();
        }
    }
}
