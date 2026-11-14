package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 59: tìm bang (ADR-399) {@code searchGuild:<chữ>}, xem lại chat bang (ADR-401) {@code guildHistory:}, chỉ bạn
 * bè nhắn riêng (ADR-402) {@code whisperFriends:on|off|query} (thử chặn bằng bước {@code whisper:<tên>} có sẵn), đánh
 * dấu đã đọc thư (ADR-400) {@code readMail:<id>}.
 */
final class Lot59Steps {

    private final Lot23Scenario.Asker asker;

    Lot59Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "searchGuild" -> searchGuild(arg);
            case "guildHistory" -> guildHistory();
            case "whisperFriends" -> whisperFriends(arg);
            case "readMail" -> readMail(arg);
            default -> "bước không hiểu";
        };
    }

    private String searchGuild(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SEARCH_GUILDS_REQUEST_VALUE,
                dev.kage.protocol.SearchGuildsRequest.newBuilder().setQuery(arg).build(),
                Opcode.OPCODE_SEARCH_GUILDS_RESPONSE_VALUE,
                p -> {
                    StringBuilder text = new StringBuilder();
                    for (dev.kage.protocol.GuildSearchEntry entry :
                            dev.kage.protocol.SearchGuildsResponse.parseFrom(p).getEntriesList()) {
                        text.append(entry.getName())
                                .append(" cấp=")
                                .append(entry.getLevel())
                                .append(" tv=")
                                .append(entry.getMembers())
                                .append(" chủ=")
                                .append(entry.getLeaderName())
                                .append("; ");
                    }
                    return text.toString().strip();
                });
    }

    private String guildHistory() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_GUILD_CHAT_HISTORY_REQUEST_VALUE,
                dev.kage.protocol.GetGuildChatHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_GUILD_CHAT_HISTORY_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetGuildChatHistoryResponse r =
                            dev.kage.protocol.GetGuildChatHistoryResponse.parseFrom(p);
                    return "bang=" + r.getInGuild() + " tin=" + r.getCount();
                });
    }

    private String whisperFriends(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_WHISPER_FRIENDS_ONLY_COMMAND_VALUE,
                dev.kage.protocol.SetWhisperFriendsOnlyCommand.newBuilder()
                        .setEnabled("on".equals(arg))
                        .setQueryOnly("query".equals(arg))
                        .build(),
                Opcode.OPCODE_SET_WHISPER_FRIENDS_ONLY_RESPONSE_VALUE,
                p -> "chỉBạn="
                        + dev.kage.protocol.SetWhisperFriendsOnlyResponse.parseFrom(p)
                                .getEnabled());
    }

    private String readMail(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_MARK_MAIL_READ_COMMAND_VALUE,
                dev.kage.protocol.MarkMailReadCommand.newBuilder()
                        .setMailId(Long.parseLong(arg))
                        .build(),
                Opcode.OPCODE_MARK_MAIL_READ_RESPONSE_VALUE,
                p -> "chưaĐọc="
                        + dev.kage.protocol.MarkMailReadResponse.parseFrom(p).getUnread());
    }
}
