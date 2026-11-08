package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetPersonalStatsRequest;
import dev.kage.protocol.GetPersonalStatsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Thống kê cá nhân (ADR-223) — {@code /thongke} (hoặc {@code /stats}) in bảng số liệu cả đời vào khung chat. */
final class PersonalStatsCoordinator implements WindowFlow {

    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_MINUTE = 60L;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PersonalStatsCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!"/thongke".equals(command) && !"/stats".equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_PERSONAL_STATS_REQUEST_VALUE, GetPersonalStatsRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_PERSONAL_STATS_RESPONSE_VALUE) {
            return false;
        }
        GetPersonalStatsResponse stats = GetPersonalStatsResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, stats));
        return true;
    }

    /** Một dòng thống kê. Tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, GetPersonalStatsResponse stats) {
        long hours = stats.getPlaytimeSeconds() / SECONDS_PER_HOUR;
        long minutes = (stats.getPlaytimeSeconds() % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        return KageI18n.formatOrKey(
                i18n,
                "personal_stats.line",
                stats.getMonstersKilled(),
                stats.getDeaths(),
                stats.getPvpWins(),
                stats.getPvpLosses(),
                stats.getQuestsClaimed(),
                stats.getAchievementsUnlocked(),
                hours,
                minutes);
    }
}
