package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetInspectNoticeCommand;
import dev.kage.protocol.SetInspectNoticeResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Báo khi bị soi trang bị (ADR-330) — {@code /baosoi} xem trạng thái, {@code /baosoi bat|tat} (hoặc {@code
 * /inspectnotice on|off}). Mặc định bật; dòng báo do server gửi.
 */
final class InspectNoticeCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    InspectNoticeCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/baosoi".equals(command) && !"/inspectnotice".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? MentionWatch.fold(words[1].strip()) : "";
        SetInspectNoticeCommand.Builder request = SetInspectNoticeCommand.newBuilder();
        switch (arg) {
            case "bat", "on" -> request.setEnabled(true);
            case "tat", "off" -> request.setEnabled(false);
            default -> request.setQueryOnly(true);
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_INSPECT_NOTICE_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_INSPECT_NOTICE_RESPONSE_VALUE) {
            return false;
        }
        boolean enabled = SetInspectNoticeResponse.parseFrom(frame.payload()).getEnabled();
        chat.showSystemNotice(KageI18n.textOrKey(i18n, enabled ? "inspectnotice.on" : "inspectnotice.off"));
        return true;
    }
}
