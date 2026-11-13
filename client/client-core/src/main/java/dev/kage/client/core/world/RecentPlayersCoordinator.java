package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetRecentPlayersRequest;
import dev.kage.protocol.GetRecentPlayersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RecentPlayer;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Người vừa chơi cùng (ADR-329) — {@code /vuachoi} (hoặc {@code /recent}): mười người gần nhất từng chung nhóm, kèm
 * cấp, đang online không và lần gần nhất — để kết bạn ({@code /ketban}) hoặc mời lại.
 */
final class RecentPlayersCoordinator implements WindowFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    RecentPlayersCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/vuachoi".equals(command) && !"/recent".equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_RECENT_PLAYERS_REQUEST_VALUE, GetRecentPlayersRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_RECENT_PLAYERS_RESPONSE_VALUE) {
            return false;
        }
        GetRecentPlayersResponse response = GetRecentPlayersResponse.parseFrom(frame.payload());
        if (response.getPlayersCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "recent.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "recent.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (RecentPlayer player : response.getPlayersList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    player.getOnline() ? "recent.line_online" : "recent.line",
                    player.getName(),
                    player.getLevel(),
                    DATE.format(
                            Instant.ofEpochSecond(player.getMetAtEpochSeconds()).atZone(zone))));
        }
        return true;
    }
}
