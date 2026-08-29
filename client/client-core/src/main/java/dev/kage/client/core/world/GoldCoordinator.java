package dev.kage.client.core.world;

import java.util.function.LongConsumer;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.GoldChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Số vàng của CHÍNH người chơi (ADR-035, {@code GOLD_CHANGED_EVENT} = 209). KHÔNG có cửa sổ riêng —
 * túi đồ và cửa hàng đều hiện con số này, nên nó sống ở một chỗ chứ không nhân đôi.
 *
 * <p><b>Server là nguồn chân lý tuyệt đối.</b> {@code gold} trong gói là số dư TUYỆT ĐỐI sau thay
 * đổi, không phải delta — client KHÔNG bao giờ tự cộng trừ theo giá món hàng nó vừa bấm mua. Mất một
 * gói giữa chừng thì gói kế tự sửa lại, không tích luỹ sai lệch. Server gửi một gói ngay sau khi vào
 * world nên cửa hàng có số ngay, không phải chờ giao dịch đầu tiên.
 *
 * <p>{@code delta} và {@code reason_key} thuần để báo cho người chơi ("-500 · Mua vật phẩm");
 * {@code reason_key} là khoá i18n do server chọn, client tra thẳng chứ không tự đặt tên lại.
 */
final class GoldCoordinator implements WindowFlow {

    /** Chưa nhận gói nào. Phân biệt được với 0 vàng thật qua {@link #hasAmount()}. */
    private long gold;

    private boolean received;

    private LongConsumer onGoldChanged = amount -> {};

    private GoldNoticeListener onGoldNotice = (delta, reasonKey) -> {};

    /** Nhận số dư mới mỗi lần vàng đổi, kể cả gói đầu tiên lúc vào world. */
    void setOnGoldChanged(LongConsumer listener) {
        this.onGoldChanged = listener;
    }

    /** Nhận phần "vì sao" của mỗi lần đổi, để hiện "-500 · Mua vật phẩm" cho người chơi. */
    void setOnGoldNotice(GoldNoticeListener listener) {
        this.onGoldNotice = listener;
    }

    /** Số dư hiện tại theo server. Là 0 khi chưa nhận gói nào — hỏi {@link #hasAmount()} để phân biệt. */
    long amount() {
        return gold;
    }

    /** Đã nhận được ít nhất một gói vàng hay chưa — giao diện chưa có số thì hiện "—", không hiện 0. */
    boolean hasAmount() {
        return received;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE) {
            return false;
        }
        GoldChangedEvent event = GoldChangedEvent.parseFrom(frame.payload());
        gold = event.getGold();
        received = true;
        onGoldChanged.accept(gold);
        // delta == 0 là gói chào lúc vào world (server chỉ báo số dư), không có gì để nói ra.
        if (event.getDelta() != 0L) {
            onGoldNotice.onGoldChanged(event.getDelta(), event.getReasonKey());
        }
        return true;
    }

    /** Số vàng là trạng thái của PHIÊN chơi — rời màn hình là quên, gói chào lần sau sẽ nói lại. */
    @Override
    public void reset() {
        gold = 0L;
        received = false;
    }

    /** Vì sao vàng vừa đổi — tách khỏi {@link LongConsumer} vì cần hai tham số. */
    @FunctionalInterface
    interface GoldNoticeListener {

        /**
         * Báo một lần vàng vừa đổi, kèm lý do.
         *
         * @param delta chênh lệch của chính lần này, âm là tiêu
         * @param reasonKey khoá i18n do SERVER chọn (vd {@code gold.reason.buy_item}) — tra thẳng,
         *     không tự đặt tên lại
         */
        void onGoldChanged(long delta, String reasonKey);
    }
}
