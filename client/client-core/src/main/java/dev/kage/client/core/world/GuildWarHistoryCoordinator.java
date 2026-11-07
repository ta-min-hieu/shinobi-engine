package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetGuildWarHistoryRequest;
import dev.kage.protocol.GetGuildWarHistoryResponse;
import dev.kage.protocol.GuildWarHistoryEntry;
import dev.kage.protocol.GuildWarResult;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Lịch sử bang chiến (ADR-208) — {@code /lichsuchien} (hoặc {@code /warhistory}) in các trận đã chốt gần nhất của bang
 * mình vào khung chat, mới nhất trước.
 */
final class GuildWarHistoryCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/lichsuchien";
    private static final String COMMAND_EN = "/warhistory";
    private static final String RESULT_PREFIX = "GUILD_WAR_RESULT_";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildWarHistoryCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
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
                Opcode.OPCODE_GET_GUILD_WAR_HISTORY_REQUEST_VALUE, GetGuildWarHistoryRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_GUILD_WAR_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetGuildWarHistoryResponse response = GetGuildWarHistoryResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guild_war_history.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "guild_war_history.title"));
        for (GuildWarHistoryEntry entry : response.getEntriesList()) {
            chat.showSystemNotice(describe(i18n, entry, ZoneId.systemDefault()));
        }
        return true;
    }

    /** Một dòng: ngày, kết quả, đối thủ, tỷ số (mình trước). Tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, GuildWarHistoryEntry entry, ZoneId zone) {
        String result = entry.getResult() == GuildWarResult.UNRECOGNIZED
                ? "unspecified"
                : entry.getResult().name().substring(RESULT_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.formatOrKey(
                i18n,
                entry.getDeclaredByUs() ? "guild_war_history.line_attack" : "guild_war_history.line_defend",
                DATE.format(Instant.ofEpochSecond(entry.getEndedEpochSeconds()).atZone(zone)),
                KageI18n.textOrKey(i18n, "guild_war_history.result." + result),
                entry.getOpponentGuildName(),
                entry.getOurKills(),
                entry.getTheirKills());
    }
}
