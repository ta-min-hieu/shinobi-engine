package dev.kage.client.core.world;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.MarkMailReadResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thư chưa đọc (ADR-314) — server vừa xác nhận đã đọc một thư ⇒ hỏi lại danh sách để dấu "(mới)" biến mất. Lệnh
 * đánh dấu do {@link MailCoordinator} gửi khi mở một thư chưa đọc.
 */
final class MailReadCoordinator implements WindowFlow {

    private final WorldCommandSender sender;

    MailReadCoordinator(WorldCommandSender sender) {
        this.sender = sender;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_MARK_MAIL_READ_RESPONSE_VALUE) {
            return false;
        }
        MarkMailReadResponse.parseFrom(frame.payload());
        sender.listMail();
        return true;
    }
}
