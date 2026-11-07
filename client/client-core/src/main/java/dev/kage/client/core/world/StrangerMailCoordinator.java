package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetStrangerMailBlockCommand;
import dev.kage.protocol.SetStrangerMailBlockResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chặn thư người lạ (ADR-212) — {@code /chanthu bat|tat} (hoặc {@code /blockmail on|off}); không kèm gì = hỏi trạng
 * thái. Bật thì chỉ bạn bè gửi thư cho mình được; thư hệ thống vẫn tới.
 */
final class StrangerMailCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/chanthu";
    private static final String COMMAND_EN = "/blockmail";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    StrangerMailCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        SetStrangerMailBlockCommand.Builder request = SetStrangerMailBlockCommand.newBuilder();
        if (words.length == 1) {
            request.setQueryOnly(true);
        } else {
            String arg = words[1].strip().toLowerCase(Locale.ROOT);
            if ("bat".equals(arg) || "on".equals(arg)) {
                request.setEnabled(true);
            } else if ("tat".equals(arg) || "off".equals(arg)) {
                request.setEnabled(false);
            } else {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "stranger_mail.usage"));
                return true;
            }
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_STRANGER_MAIL_BLOCK_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_STRANGER_MAIL_BLOCK_RESPONSE_VALUE) {
            return false;
        }
        boolean enabled =
                SetStrangerMailBlockResponse.parseFrom(frame.payload()).getEnabled();
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "stranger_mail.on" : "stranger_mail.off"));
        return true;
    }
}
