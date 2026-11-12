package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BossKillEntry;
import dev.kage.protocol.GetBossKillsRequest;
import dev.kage.protocol.GetBossKillsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Lịch sử boss đã hạ (ADR-296) — {@code /bossdaha} (hoặc {@code /bosskills}): 20 lần gần nhất. */
final class BossKillsCoordinator implements WindowFlow {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BossKillsCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/bossdaha".equals(command) && !"/bosskills".equals(command)) {
            return false;
        }
        sender.sendReadRequest(Opcode.OPCODE_GET_BOSS_KILLS_REQUEST_VALUE, GetBossKillsRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_BOSS_KILLS_RESPONSE_VALUE) {
            return false;
        }
        GetBossKillsResponse response = GetBossKillsResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "bosskills.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "bosskills.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (BossKillEntry entry : response.getEntriesList()) {
            String when =
                    TIME.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone));
            String boss = KageI18n.textOrKey(i18n, "hud.monster." + entry.getBossCode());
            chat.showSystemNotice(
                    entry.getRank() > 0
                            ? KageI18n.formatOrKey(
                                    i18n, "bosskills.ranked", when, boss, entry.getRank(), entry.getDamage())
                            : KageI18n.formatOrKey(i18n, "bosskills.line", when, boss));
        }
        return true;
    }
}
