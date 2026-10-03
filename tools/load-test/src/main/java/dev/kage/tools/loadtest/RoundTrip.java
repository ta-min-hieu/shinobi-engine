package dev.kage.tools.loadtest;

import java.io.IOException;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chờ phản hồi của ĐÚNG một lệnh đã gửi — ghép theo cặp ({@code opcode}, {@code sequenceId}), không
 * chỉ theo opcode.
 *
 * <p><b>Bug đo thật, sửa 2026-10-03:</b> bản cũ ({@code GameConnection.readUntilOpcode}) dừng ở
 * frame ĐẦU TIÊN mang opcode cần chờ. Nhưng mỗi vòng {@link SimulatedPlayer} kết thúc bằng một
 * {@code MoveCommand} dừng bắn-rồi-quên và server ack CẢ lệnh đó ({@code WorldFacade.handleMoveIntent}
 * luôn gọi {@code onMoveAccepted}). Ack ấy nằm sẵn trong socket suốt lúc ngủ, nên lượt Move kế tiếp
 * đọc trúng ack CŨ và đo ra thời gian giải mã vài frame đã đệm — gần 0, bất kể server chậm cỡ nào.
 * Server lặp lại {@code sequenceId} của lệnh ở mọi phản hồi (Move/Pong/Chat/Sell/Error), nên ghép
 * theo nó là đủ và không cần đổi giao thức.
 */
final class RoundTrip {

    /** Kết quả một lượt hỏi-đáp mà server CÓ trả lời. */
    enum Outcome {
        /** Phản hồi đúng opcode mong đợi. */
        RESPONDED,
        /** {@code ErrorResponse} mang đúng {@code sequenceId} — server từ chối có lý do. */
        REJECTED
    }

    /** Đọc hết ngân sách frame mà không thấy phản hồi của lệnh — tách khỏi lỗi socket thường. */
    static final class ResponseMissingException extends IOException {
        private static final long serialVersionUID = 1L;

        ResponseMissingException(String message) {
            super(message);
        }
    }

    private RoundTrip() {}

    /**
     * Đọc tới khi gặp phản hồi của lệnh {@code sequenceId}, bỏ qua sự kiện server đẩy xen giữa VÀ
     * phản hồi của lệnh khác (kể cả cùng opcode).
     *
     * @throws java.net.SocketTimeoutException nếu một lượt đọc hết giờ
     * @throws ResponseMissingException nếu đọc {@code maxFramesToSkip} frame vẫn chưa thấy
     * @throws IOException nếu socket chết hoặc frame hỏng
     */
    static Outcome await(FrameChannel channel, int responseOpcode, int sequenceId, int maxFramesToSkip)
            throws IOException {
        for (int attempt = 0; attempt < maxFramesToSkip; attempt++) {
            PacketCodec.Frame frame = channel.readFrame();
            if (frame.header().sequenceId() != sequenceId) {
                continue;
            }
            int opcode = frame.header().opcode();
            if (opcode == responseOpcode) {
                return Outcome.RESPONDED;
            }
            if (opcode == Opcode.OPCODE_ERROR_VALUE) {
                return Outcome.REJECTED;
            }
        }
        throw new ResponseMissingException("Không thấy phản hồi opcode " + responseOpcode + " cho sequenceId "
                + sequenceId + " sau " + maxFramesToSkip + " frame");
    }
}
