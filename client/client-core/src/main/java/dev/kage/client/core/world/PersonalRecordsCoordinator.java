package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetPersonalRecordsRequest;
import dev.kage.protocol.GetPersonalRecordsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Kỷ lục cá nhân (ADR-240) — {@code /kyluc} (hoặc {@code /records}): đòn mạnh nhất, chuỗi hạ quái dài nhất và chuỗi
 * hiện tại. Phá kỷ lục thì server tự báo một dòng chat hệ thống.
 */
final class PersonalRecordsCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/kyluc";
    private static final String COMMAND_EN = "/records";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PersonalRecordsCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_PERSONAL_RECORDS_REQUEST_VALUE, GetPersonalRecordsRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_PERSONAL_RECORDS_RESPONSE_VALUE) {
            return false;
        }
        GetPersonalRecordsResponse response = GetPersonalRecordsResponse.parseFrom(frame.payload());
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "records.line",
                response.getHighestHit(),
                response.getLongestKillStreak(),
                response.getCurrentKillStreak()));
        return true;
    }
}
