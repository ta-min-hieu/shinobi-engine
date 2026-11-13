package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetNameHistoryRequest;
import dev.kage.protocol.GetNameHistoryResponse;
import dev.kage.protocol.OldName;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Tra tên cũ (ADR-343) — {@code /tencu <tên>} (hoặc {@code /oldnames}): tên hiện tại và các tên trước đây. */
final class NameHistoryCoordinator implements WindowFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    NameHistoryCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/tencu".equals(command) && !"/oldnames".equals(command)) {
            return false;
        }
        if (words.length < 2) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "oldnames.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_NAME_HISTORY_REQUEST_VALUE,
                GetNameHistoryRequest.newBuilder().setName(words[1].strip()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_NAME_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetNameHistoryResponse response = GetNameHistoryResponse.parseFrom(frame.payload());
        if (!response.getFound()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "oldnames.not_found"));
        } else if (response.getOldNamesCount() == 0) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "oldnames.none", response.getCurrentName()));
        } else {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "oldnames.header", response.getCurrentName()));
            ZoneId zone = ZoneId.systemDefault();
            for (OldName old : response.getOldNamesList()) {
                chat.showSystemNotice(KageI18n.formatOrKey(
                        i18n,
                        "oldnames.line",
                        old.getName(),
                        DATE.format(Instant.ofEpochSecond(old.getChangedAtEpochSeconds())
                                .atZone(zone))));
            }
        }
        return true;
    }
}
