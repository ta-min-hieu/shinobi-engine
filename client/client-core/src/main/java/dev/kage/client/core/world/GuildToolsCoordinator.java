package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetGuildChatHistoryRequest;
import dev.kage.protocol.GetGuildChatHistoryResponse;
import dev.kage.protocol.GuildSearchEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SearchGuildsRequest;
import dev.kage.protocol.SearchGuildsResponse;
import dev.kage.protocol.SetWhisperFriendsOnlyCommand;
import dev.kage.protocol.SetWhisperFriendsOnlyResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ba lệnh hỏi server lô 59: tìm bang (ADR-399, {@code /timbang <chữ>} hoặc {@code /findguild}), xem lại chat bang
 * (ADR-401, {@code /lichsubang} hoặc {@code /guildhistory} — các tin tự vào khung chat), chỉ bạn bè nhắn riêng
 * (ADR-402, {@code /chibanbe [bat|tat]} hoặc {@code /whisperfriends}; bỏ trống ⇒ hỏi).
 */
final class GuildToolsCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildToolsCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        String arg = words.length > 1 ? words[1].strip() : "";
        switch (command) {
            case "/timbang", "/findguild" -> search(arg);
            case "/lichsubang", "/guildhistory" ->
                sender.sendReadRequest(
                        Opcode.OPCODE_GET_GUILD_CHAT_HISTORY_REQUEST_VALUE,
                        GetGuildChatHistoryRequest.getDefaultInstance());
            case "/chibanbe", "/whisperfriends" -> {
                String mode = arg.toLowerCase(Locale.ROOT);
                sender.sendReadRequest(
                        Opcode.OPCODE_SET_WHISPER_FRIENDS_ONLY_COMMAND_VALUE,
                        SetWhisperFriendsOnlyCommand.newBuilder()
                                .setEnabled("bat".equals(mode) || "on".equals(mode))
                                .setQueryOnly(mode.isEmpty())
                                .build());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void search(String query) {
        if (query.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "findguild.usage"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SEARCH_GUILDS_REQUEST_VALUE,
                SearchGuildsRequest.newBuilder().setQuery(query).build());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_SEARCH_GUILDS_RESPONSE_VALUE) {
            showSearch(SearchGuildsResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_GET_GUILD_CHAT_HISTORY_RESPONSE_VALUE) {
            GetGuildChatHistoryResponse response = GetGuildChatHistoryResponse.parseFrom(frame.payload());
            chat.showSystemNotice(
                    response.getInGuild()
                            ? KageI18n.formatOrKey(i18n, "guildhistory.done", response.getCount())
                            : KageI18n.textOrKey(i18n, "guildhistory.no_guild"));
            return true;
        }
        if (opcode == Opcode.OPCODE_SET_WHISPER_FRIENDS_ONLY_RESPONSE_VALUE) {
            boolean on =
                    SetWhisperFriendsOnlyResponse.parseFrom(frame.payload()).getEnabled();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, on ? "whisperfriends.on" : "whisperfriends.off"));
            return true;
        }
        return false;
    }

    private void showSearch(SearchGuildsResponse response) {
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "findguild.none"));
            return;
        }
        for (GuildSearchEntry entry : response.getEntriesList()) {
            String name = entry.getTag().isEmpty() ? entry.getName() : "[" + entry.getTag() + "] " + entry.getName();
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "findguild.line", name, entry.getLevel(), entry.getMembers(), entry.getLeaderName()));
        }
    }
}
