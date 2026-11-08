package dev.kage.client.core.world;

import java.util.Locale;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DeleteFinishedMailCommand;
import dev.kage.protocol.DeleteFinishedMailResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PinMailCommand;
import dev.kage.protocol.PinMailResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Dọn hộp thư — {@code /xoathu} (hoặc {@code /cleanmail}) xoá mọi thư đã nhận / không đính kèm, trừ thư ghim (ADR-216);
 * mở một lá thư rồi {@code /ghimthu} (hoặc {@code /pinmail}) để ghim, {@code /boghim} ({@code /unpinmail}) để bỏ
 * (ADR-217). Thư ghim nằm đầu hộp thư, tiêu đề có dấu {@code [*]}.
 */
final class MailCleanupCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private LongSupplier selectedMail = () -> 0L;

    MailCleanupCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối lá thư đang mở đọc ({@code MailCoordinator}). */
    void useSelection(LongSupplier selectedMail) {
        this.selectedMail = selectedMail;
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "/xoathu", "/cleanmail" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_DELETE_FINISHED_MAIL_COMMAND_VALUE,
                        DeleteFinishedMailCommand.getDefaultInstance());
            case "/ghimthu", "/pinmail" -> pin(true);
            case "/boghim", "/unpinmail" -> pin(false);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void pin(boolean pinned) {
        long mailId = selectedMail.getAsLong();
        if (mailId == 0L) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "mail_pin.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_PIN_MAIL_COMMAND_VALUE,
                PinMailCommand.newBuilder().setMailId(mailId).setPinned(pinned).build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_DELETE_FINISHED_MAIL_RESPONSE_VALUE) {
            int deleted = DeleteFinishedMailResponse.parseFrom(frame.payload()).getDeleted();
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "mail_clean.done", deleted));
            sender.listMail();
            return true;
        }
        if (opcode == Opcode.OPCODE_PIN_MAIL_RESPONSE_VALUE) {
            PinMailResponse response = PinMailResponse.parseFrom(frame.payload());
            String key = !response.getOk() ? "mail_pin.failed" : response.getPinned() ? "mail_pin.on" : "mail_pin.off";
            chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
            sender.listMail();
            return true;
        }
        return false;
    }
}
