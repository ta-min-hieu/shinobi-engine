package dev.kage.client.core.screen;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tìm gói trả lời của ĐÚNG yêu cầu đang chờ ở màn đăng nhập/chọn nhân vật — khớp cả opcode lẫn
 * {@code sequence_id} (server luôn trả lại {@code sequence_id} của yêu cầu, điều
 * {@code SessionRecovery} vốn đã dựa vào).
 *
 * <p>Chỉ khớp opcode là chưa đủ, vì mỗi bước có hạn 5 giây: hết hạn thì màn hình mở khoá cho người
 * chơi bấm lại, nhưng câu trả lời của lần bấm TRƯỚC vẫn đang trên đường về. Bản cũ lấy câu trả lời
 * muộn đó làm câu trả lời cho lần bấm mới — chọn nhân vật B mà vào world bằng gói của nhân vật A, hoặc
 * một {@code ErrorResponse} muộn của lần tạo nhân vật đã hết giờ làm hỏng lần chọn đang chờ
 * ({@code PendingReplyTest}).
 */
final class PendingReply {

    private PendingReply() {}

    /**
     * Rút gói từ {@code inbound} cho tới khi gặp câu trả lời (hoặc {@code ErrorResponse}) mang đúng
     * {@code sequenceId}. Gói không khớp bị bỏ — chúng là câu trả lời muộn của yêu cầu cũ hoặc gói
     * không thuộc màn này (PONG). Gói ĐẰNG SAU câu trả lời thì để nguyên trong hàng đợi: gói world tới
     * ngay sau {@code SelectCharacterResponse} là của màn world sắp dựng.
     *
     * @return gói trả lời — opcode là {@code replyOpcode} hoặc {@code OPCODE_ERROR}; rỗng khi chưa tới
     */
    static Optional<PacketCodec.Frame> poll(Supplier<PacketCodec.Frame> inbound, int sequenceId, int replyOpcode) {
        return poll(inbound, sequenceId, replyOpcode, frame -> {});
    }

    /**
     * Như {@link #poll(Supplier, int, int)} nhưng gói không khớp được trao cho {@code skipped} thay vì
     * vứt — dùng lúc chờ {@code SelectCharacterResponse}: server gắn nhân vật vào world TRƯỚC khi trả
     * lời (2026-10-14, docs/README.md dòng 73(c)), nên gói world có thể tới trước câu trả lời.
     */
    static Optional<PacketCodec.Frame> poll(
            Supplier<PacketCodec.Frame> inbound, int sequenceId, int replyOpcode, Consumer<PacketCodec.Frame> skipped) {
        PacketCodec.Frame frame;
        while ((frame = inbound.get()) != null) {
            int opcode = frame.header().opcode();
            if (frame.header().sequenceId() == sequenceId
                    && (opcode == replyOpcode || opcode == Opcode.OPCODE_ERROR_VALUE)) {
                return Optional.of(frame);
            }
            skipped.accept(frame);
        }
        return Optional.empty();
    }
}
