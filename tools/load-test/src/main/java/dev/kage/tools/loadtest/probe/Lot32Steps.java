package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.GetTradeHistoryRequest;
import dev.kage.protocol.GetTradeHistoryResponse;
import dev.kage.protocol.GuildCheckInCommand;
import dev.kage.protocol.GuildCheckInResponse;
import dev.kage.protocol.GuildRecruitmentView;
import dev.kage.protocol.ListGuildRecruitmentRequest;
import dev.kage.protocol.ListGuildRecruitmentResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PostGuildRecruitmentCommand;
import dev.kage.protocol.PostGuildRecruitmentResponse;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.SendGuildJoinRequestCommand;
import dev.kage.protocol.SendGuildJoinRequestResponse;
import dev.kage.protocol.SetGuildViceLeaderCommand;
import dev.kage.protocol.SetGuildViceLeaderResponse;
import dev.kage.protocol.TradeHistoryEntry;
import dev.kage.protocol.TransferGuildLeaderCommand;
import dev.kage.protocol.TransferGuildLeaderResponse;

/**
 * Bước lô 32: nhường chức bang chủ (ADR-183) {@code transferLeader:<tên>}; phó bang chủ (ADR-184) {@code vice:<tên>},
 * {@code unvice:<tên>}; chiêu mộ (ADR-185) {@code recruit:<cấp>=<lời>} ({@code recruit:off} gỡ), {@code recruitBoard},
 * {@code applyGuild:<tên bang>}; điểm danh bang (ADR-186) {@code guildCheckIn}; khoe vật phẩm (ADR-188) {@code
 * showItem:<id món>} (kênh Thế giới, chờ tin dội về), {@code showItemArea:<id>}. Nối sau {@link Lot31Steps}.
 */
final class Lot32Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot33Steps lot33;

    Lot32Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot33 = new Lot33Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "transferLeader" -> transferLeader(arg);
            case "vice" -> vice(arg, true);
            case "unvice" -> vice(arg, false);
            case "recruit" -> recruit(arg);
            case "recruitBoard" -> recruitBoard();
            case "applyGuild" -> applyGuild(arg);
            default -> moreStep(verb, arg);
        };
    }

    /** Nhánh thứ hai — tách khỏi {@link #step} vì trần CyclomaticComplexity. */
    private String moreStep(String verb, String arg) throws IOException {
        return switch (verb) {
            case "guildCheckIn" -> guildCheckIn();
            case "showItem" -> showItem(Long.parseLong(arg), ChatChannel.CHAT_CHANNEL_WORLD);
            case "showItemArea" -> showItem(Long.parseLong(arg), ChatChannel.CHAT_CHANNEL_AREA);
            case "showItemResult" -> showItemResult(Long.parseLong(arg));
            case "tradeHistory" -> tradeHistory();
            default -> lot33.step(verb, arg);
        };
    }

    private String transferLeader(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_TRANSFER_GUILD_LEADER_COMMAND_VALUE,
                TransferGuildLeaderCommand.newBuilder().setTargetName(name).build(),
                Opcode.OPCODE_TRANSFER_GUILD_LEADER_RESPONSE_VALUE,
                p -> {
                    TransferGuildLeaderResponse r = TransferGuildLeaderResponse.parseFrom(p);
                    return r.getOutcome().name() + " người nhận=" + r.getTargetName();
                });
    }

    private String vice(String name, boolean appoint) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_GUILD_VICE_LEADER_COMMAND_VALUE,
                SetGuildViceLeaderCommand.newBuilder()
                        .setTargetName(name)
                        .setAppoint(appoint)
                        .build(),
                Opcode.OPCODE_SET_GUILD_VICE_LEADER_RESPONSE_VALUE,
                p -> SetGuildViceLeaderResponse.parseFrom(p).getOutcome().name());
    }

    private String recruit(String arg) throws IOException {
        PostGuildRecruitmentCommand.Builder command = PostGuildRecruitmentCommand.newBuilder();
        if (!"off".equals(arg)) {
            String[] parts = arg.split("=", 2);
            command.setOpen(true).setMinLevel(Integer.parseInt(parts[0])).setMessage(parts[1]);
        }
        return asker.ask(
                Opcode.OPCODE_POST_GUILD_RECRUITMENT_COMMAND_VALUE,
                command.build(),
                Opcode.OPCODE_POST_GUILD_RECRUITMENT_RESPONSE_VALUE,
                p -> PostGuildRecruitmentResponse.parseFrom(p).getOutcome().name());
    }

    private String recruitBoard() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_GUILD_RECRUITMENT_REQUEST_VALUE,
                ListGuildRecruitmentRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_GUILD_RECRUITMENT_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (GuildRecruitmentView v :
                            ListGuildRecruitmentResponse.parseFrom(p).getEntriesList()) {
                        out.append(v.getGuildName())
                                .append(" Lv")
                                .append(v.getGuildLevel())
                                .append(' ')
                                .append(v.getMembers())
                                .append('/')
                                .append(v.getMaxMembers())
                                .append(" từCấp")
                                .append(v.getMinLevel())
                                .append(": ")
                                .append(v.getMessage())
                                .append(" | ");
                    }
                    return out.toString();
                });
    }

    private String applyGuild(String guildName) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_COMMAND_VALUE,
                SendGuildJoinRequestCommand.newBuilder().setGuildName(guildName).build(),
                Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_RESPONSE_VALUE,
                p -> SendGuildJoinRequestResponse.parseFrom(p).getOutcome().name());
    }

    private String guildCheckIn() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GUILD_CHECK_IN_COMMAND_VALUE,
                GuildCheckInCommand.getDefaultInstance(),
                Opcode.OPCODE_GUILD_CHECK_IN_RESPONSE_VALUE,
                p -> {
                    GuildCheckInResponse r = GuildCheckInResponse.parseFrom(p);
                    return r.getOutcome().name() + " cốngHiến+" + r.getContribution() + " expBang+" + r.getGuildExp()
                            + " hômNay=" + r.getTodayCount();
                });
    }

    /**
     * Khoe một món; kênh Thế giới thì chờ tin dội về (thấy LinkedItemView server gắn), kênh Khu vực thì chờ outcome.
     */
    private String showItem(long itemId, ChatChannel channel) throws IOException {
        SendChatMessageCommand command = SendChatMessageCommand.newBuilder()
                .setChannel(channel)
                .setBody("probe khoe")
                .setLinkedItemId(itemId)
                .build();
        if (channel == ChatChannel.CHAT_CHANNEL_AREA) {
            return asker.ask(
                    Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                    command,
                    Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE,
                    p -> SendChatMessageResponse.parseFrom(p).getOutcome().name());
        }
        return asker.ask(
                Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, command, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, p -> {
                    ChatMessageEvent e = ChatMessageEvent.parseFrom(p);
                    return "món=" + e.getLinkedItem().getItemTemplateId() + " +"
                            + e.getLinkedItem().getEnhanceLevel() + " công="
                            + e.getLinkedItem().getBonusAtk() + " body=" + e.getBody();
                });
    }

    /** Khoe ở kênh Thế giới nhưng đọc OUTCOME (vd món không phải của mình ⇒ LINKED_ITEM_NOT_FOUND). */
    private String showItemResult(long itemId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                SendChatMessageCommand.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                        .setBody("probe khoe")
                        .setLinkedItemId(itemId)
                        .build(),
                Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE,
                p -> SendChatMessageResponse.parseFrom(p).getOutcome().name());
    }

    private String tradeHistory() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_TRADE_HISTORY_REQUEST_VALUE,
                GetTradeHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_TRADE_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder();
                    for (TradeHistoryEntry e :
                            GetTradeHistoryResponse.parseFrom(p).getEntriesList()) {
                        out.append("với ")
                                .append(e.getCounterpartName())
                                .append(" đưa ")
                                .append(e.getGivenCount())
                                .append(" món+")
                                .append(e.getGoldGiven())
                                .append(" nhận ")
                                .append(e.getReceivedCount())
                                .append(" món+")
                                .append(e.getGoldReceived())
                                .append(" | ");
                    }
                    return out.toString();
                });
    }
}
