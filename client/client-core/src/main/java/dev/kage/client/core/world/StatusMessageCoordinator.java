package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetStatusMessageCommand;
import dev.kage.protocol.SetStatusMessageOutcome;
import dev.kage.protocol.SetStatusMessageResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lời nhắn trạng thái (ADR-335) — {@code /trangthai} xem, {@code /trangthai <chữ>} đặt, {@code /trangthai xoa} xoá
 * (hoặc {@code /status}). Bạn bè thấy lời nhắn cạnh tên mình trong danh sách bạn bè.
 */
final class StatusMessageCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    StatusMessageCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/trangthai".equals(command) && !"/status".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? words[1].strip() : "";
        SetStatusMessageCommand.Builder request = SetStatusMessageCommand.newBuilder();
        String folded = MentionWatch.fold(arg);
        if (arg.isEmpty()) {
            request.setQueryOnly(true);
        } else if (!"xoa".equals(folded) && !"clear".equals(folded)) {
            request.setMessage(arg);
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_STATUS_MESSAGE_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_STATUS_MESSAGE_RESPONSE_VALUE) {
            return false;
        }
        SetStatusMessageResponse response = SetStatusMessageResponse.parseFrom(frame.payload());
        if (response.getOutcome() == SetStatusMessageOutcome.SET_STATUS_MESSAGE_OUTCOME_INVALID_TEXT) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "status.invalid"));
        } else if (response.getMessage().isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "status.none"));
        } else {
            chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, "status.current", ChatController.sanitize(response.getMessage())));
        }
        return true;
    }
}
