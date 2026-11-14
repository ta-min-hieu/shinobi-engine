package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetGuildSeniorityRequest;
import dev.kage.protocol.GetGuildSeniorityResponse;
import dev.kage.protocol.GetLifetimeRankingRequest;
import dev.kage.protocol.GetLifetimeRankingResponse;
import dev.kage.protocol.GuildSeniorityEntry;
import dev.kage.protocol.LifetimeRank;
import dev.kage.protocol.LifetimeRankingKind;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ba lệnh hỏi server lô 58: xếp hạng sát thủ PvP (ADR-391, {@code /topsatthu} hoặc {@code /topkillers}), thời gian tu
 * luyện (ADR-392, {@code /toptuluyen} hoặc {@code /topplaytime}), thâm niên trong bang (ADR-393, {@code /thamnien}
 * hoặc {@code /seniority}). In vào chat.
 */
final class LifetimeRankingCoordinator implements WindowFlow {

    private static final long SECONDS_PER_HOUR = 3_600L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final ZoneId zone;

    LifetimeRankingCoordinator(WorldCommandSender sender, ChatCoordinator chat, ZoneId zone) {
        this.sender = sender;
        this.chat = chat;
        this.zone = zone;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        switch (command) {
            case "/topsatthu", "/topkillers" -> askRanking(LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYER_KILLS);
            case "/toptuluyen", "/topplaytime" -> askRanking(LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYTIME);
            case "/thamnien", "/seniority" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_GUILD_SENIORITY_REQUEST_VALUE, GetGuildSeniorityRequest.getDefaultInstance());
            default -> {
                return false;
            }
        }
        return true;
    }

    private void askRanking(LifetimeRankingKind kind) {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_LIFETIME_RANKING_REQUEST_VALUE,
                GetLifetimeRankingRequest.newBuilder().setKind(kind).build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_LIFETIME_RANKING_RESPONSE_VALUE) {
            showRanking(GetLifetimeRankingResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_GET_GUILD_SENIORITY_RESPONSE_VALUE) {
            showSeniority(GetGuildSeniorityResponse.parseFrom(frame.payload()));
            return true;
        }
        return false;
    }

    private void showRanking(GetLifetimeRankingResponse response) {
        boolean playtime = response.getKind() == LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYTIME;
        String base = playtime ? "topplaytime" : "topkillers";
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, base + ".empty"));
            return;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, base + ".header"));
        int rank = 1;
        for (LifetimeRank entry : response.getEntriesList()) {
            long value = playtime ? entry.getValue() / SECONDS_PER_HOUR : entry.getValue();
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, base + ".line", rank++, entry.getName(), value));
        }
    }

    private void showSeniority(GetGuildSeniorityResponse response) {
        if (!response.getInGuild()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "seniority.no_guild"));
            return;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "seniority.header"));
        int rank = 1;
        for (GuildSeniorityEntry entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "seniority.line", rank++, entry.getName(), dateOf(entry.getJoinedEpochSeconds(), zone)));
        }
    }

    /** Ngày vào bang {@code dd/MM/yyyy} theo múi giờ máy. Tĩnh để test. */
    static String dateOf(long epochSeconds, ZoneId zone) {
        return DATE.format(Instant.ofEpochSecond(epochSeconds).atZone(zone));
    }
}
