package dev.kage.client.core.world;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.wire.PacketCodec;

/**
 * Lưới an toàn theo TỪNG gói đến của client (quyết định chủ dự án 2026-10-11).
 *
 * <p>Gói đến được xử lý ngay trong vòng lặp của libGDX, mà libGDX không bắt gì: một gói làm ném ngoại
 * lệ ở bất kỳ luồng xử lý nào (một giá trị enum lạ, một id chưa từng thấy, một trạng thái không ai
 * lường) là SẬP cả client. Cách cũ — sửa từng chỗ ném khi nó lộ ra — nghĩa là lỗi kế tiếp vẫn là một
 * lần sập trên máy người chơi.
 *
 * <p>Ở đây một gói ném thì bị BỎ kèm một dòng ERROR có opcode, và vòng lặp đi tiếp sang gói sau. Trạng
 * thái của màn chơi có thể lệch vì gói đó không được áp — vẫn tốt hơn mất cả phiên, và dòng log là thứ
 * để lần ra chỗ ném mà sửa cho đúng.
 *
 * <p><b>Đây là chỗ DUY NHẤT trong client được bắt {@link RuntimeException} tổng quát</b> (ngoại lệ có
 * chủ đích của luật /CLAUDE.md §11, do chủ dự án cho phép). Đừng chép khuôn này sang chỗ khác: bắt
 * tổng quát ở giữa một luồng nghiệp vụ là nuốt lỗi; ở ranh giới "một gói từ mạng" thì nó là cô lập lỗi.
 */
final class InboundPacketGuard {

    private static final Logger LOG = LoggerFactory.getLogger(InboundPacketGuard.class);

    private int droppedPackets;

    /**
     * Chạy {@code handler} cho {@code frame}; nếu nó ném thì ghi log và bỏ gói.
     *
     * @return {@code false} nếu gói bị bỏ vì handler ném
     */
    // PMD AvoidCatchingGenericException: đúng ý — xem javadoc lớp.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    boolean handle(PacketCodec.Frame frame, Consumer<PacketCodec.Frame> handler) {
        try {
            handler.accept(frame);
            return true;
        } catch (RuntimeException e) {
            droppedPackets++;
            LOG.error(
                    "Bỏ một gói vì xử lý nó ném lỗi (client KHÔNG sập): opcode={}, payloadBytes={}",
                    frame.header().opcode(),
                    frame.payload().length,
                    e);
            return false;
        }
    }

    /** Số gói đã bị bỏ từ đầu phiên — để test và chẩn đoán. */
    int droppedPackets() {
        return droppedPackets;
    }
}
