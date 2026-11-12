package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DuelHistoryEntry;
import dev.kage.protocol.GetDuelHistoryRequest;
import dev.kage.protocol.GetDuelHistoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Lịch sử đấu tay đôi (ADR-280) — {@code /lichsudau} (hoặc {@code /duels}): 10 trận gần nhất. */
final class DuelHistoryCoordinator implements WindowFlow {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    DuelHistoryCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!"/lichsudau".equals(command) && !"/duels".equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_DUEL_HISTORY_REQUEST_VALUE, GetDuelHistoryRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_DUEL_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetDuelHistoryResponse response = GetDuelHistoryResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "duels.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "duels.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (DuelHistoryEntry entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    entry.getWon() ? "duels.won" : "duels.lost",
                    TIME.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone)),
                    entry.getOpponentName(),
                    entry.getRatingAfter()));
        }
        return true;
    }
}
