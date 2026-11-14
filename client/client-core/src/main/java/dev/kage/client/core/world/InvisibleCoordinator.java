package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetGuildLevelRankingRequest;
import dev.kage.protocol.GetGuildLevelRankingResponse;
import dev.kage.protocol.GetLikeRankingRequest;
import dev.kage.protocol.GuildLevelRank;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetInvisibleCommand;
import dev.kage.protocol.SetInvisibleResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ba lệnh hỏi server lô 57: chế độ ẩn (ADR-383, {@code /an [bat|tat]} hoặc {@code /invisible}; bỏ trống ⇒ hỏi), xếp
 * hạng thích tuần (ADR-384, {@code /thichtuan} hoặc {@code /weeklylikes} — kết quả in qua luồng {@code /topthich}),
 * xếp hạng cấp trong bang (ADR-385, {@code /capbang} hoặc {@code /guildlevels}).
 */
final class InvisibleCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    InvisibleCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        String arg = words.length > 1 ? words[1].strip().toLowerCase(Locale.ROOT) : "";
        switch (command) {
            case "/an", "/invisible" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_SET_INVISIBLE_COMMAND_VALUE,
                        SetInvisibleCommand.newBuilder()
                                .setEnabled("bat".equals(arg) || "on".equals(arg))
                                .setQueryOnly(arg.isEmpty())
                                .build());
            case "/thichtuan", "/weeklylikes" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_LIKE_RANKING_REQUEST_VALUE,
                        GetLikeRankingRequest.newBuilder().setWeekly(true).build());
            case "/capbang", "/guildlevels" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_GUILD_LEVEL_RANKING_REQUEST_VALUE,
                        GetGuildLevelRankingRequest.getDefaultInstance());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_SET_INVISIBLE_RESPONSE_VALUE) {
            boolean on = SetInvisibleResponse.parseFrom(frame.payload()).getEnabled();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, on ? "invisible.on" : "invisible.off"));
            return true;
        }
        if (opcode != Opcode.OPCODE_GET_GUILD_LEVEL_RANKING_RESPONSE_VALUE) {
            return false;
        }
        GetGuildLevelRankingResponse response = GetGuildLevelRankingResponse.parseFrom(frame.payload());
        if (!response.getInGuild()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildlevels.no_guild"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "guildlevels.header"));
        int rank = 1;
        for (GuildLevelRank entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    entry.getOnline() ? "guildlevels.line_online" : "guildlevels.line",
                    rank++,
                    entry.getName(),
                    realmLabel(entry.getLevel())));
        }
        return true;
    }

    private String realmLabel(int level) {
        CultivationRealm realm = CultivationRealm.of(level);
        String realmName = KageI18n.textOrKey(i18n, realm.nameKey());
        return realm.perfected()
                ? KageI18n.formatOrKey(i18n, "realm.perfect", realmName)
                : KageI18n.formatOrKey(i18n, "realm.tier_short", realmName, realm.tier());
    }
}
