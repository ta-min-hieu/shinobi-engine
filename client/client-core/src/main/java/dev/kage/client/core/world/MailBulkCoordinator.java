package dev.kage.client.core.world;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ClaimAllMailResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nhận hết thư (ADR-167) — nút nằm trong hòm thư ({@link MailCoordinator}); lớp này nhận kết quả, in một dòng hệ thống
 * và hỏi lại hộp thư + túi đồ.
 */
final class MailBulkCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    MailBulkCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_CLAIM_ALL_MAIL_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(ClaimAllMailResponse.parseFrom(frame.payload())));
        sender.listMail();
        sender.listInventory();
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tách ra để test không cần màn hình. */
    String describe(ClaimAllMailResponse response) {
        String summary = KageI18n.formatOrKey(
                i18n,
                "mail.claim_all.done",
                response.getClaimed(),
                response.getGold(),
                response.getItems(),
                response.getDeleted());
        return response.getBagFull() ? summary + " " + KageI18n.textOrKey(i18n, "mail.claim_all.bag_full") : summary;
    }
}
