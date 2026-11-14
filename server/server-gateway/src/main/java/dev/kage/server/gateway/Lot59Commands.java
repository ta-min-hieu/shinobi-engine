package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.domain.Character;
import dev.kage.friend.api.WhisperPrivacyFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.domain.GuildDirectoryPort;
import dev.kage.mail.domain.MailReceiptPort;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.GetGuildChatHistoryResponse;
import dev.kage.protocol.GuildSearchEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SearchGuildsRequest;
import dev.kage.protocol.SearchGuildsResponse;
import dev.kage.protocol.SetWhisperFriendsOnlyCommand;
import dev.kage.protocol.SetWhisperFriendsOnlyResponse;

/** Lớp lệnh lô 59 (ADR-399…) — MỘT thành phần của {@link Lot58Commands}. Gọi trên Virtual Thread. */
record Lot59Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-401 — số tin bang phát lại tối đa. */
    static final int HISTORY_LINES = 30;

    private static final Logger LOG = LoggerFactory.getLogger(Lot59Commands.class);

    /** Lớp lệnh lô 60 (ADR-407…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot60Commands lot60() {
        return new Lot60Commands(gateway, registry);
    }

    /** ADR-399 — tìm bang theo tên. */
    SearchGuildsResponse searchGuilds(SearchGuildsRequest request) {
        SearchGuildsResponse.Builder response = SearchGuildsResponse.newBuilder();
        for (GuildDirectoryPort.Found found : lot58().guildDirectory().search(request.getQuery())) {
            response.addEntries(GuildSearchEntry.newBuilder()
                    .setName(found.name())
                    .setLevel(found.level())
                    .setMembers(found.members())
                    .setLeaderName(found.leaderName())
                    .setTag(found.tag()));
        }
        return response.build();
    }

    /**
     * ADR-400 — người nhận vừa mở thư lần đầu: người gửi (người chơi, đang online) nhận một dòng hệ thống. Việc phụ —
     * lỗi dừng ở đây, không làm hỏng lệnh đánh dấu đã đọc.
     */
    // PMD AvoidCatchingGenericException: việc phụ sau khi đã đánh dấu đã đọc.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void notifyRead(long readerId, long mailId) {
        try {
            Optional<MailReceiptPort.Receipt> receipt = facades().mailReceipt().receipt(mailId, readerId);
            if (receipt.isEmpty() || receipt.get().senderId() == readerId) {
                return;
            }
            String reader = gateway.characterFacade()
                    .findById(readerId)
                    .map(Character::name)
                    .orElse("?");
            registry.sendToCharacterIfOnline(
                    receipt.get().senderId(),
                    Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                    0,
                    receiptLine(reader, receipt.get().subject()));
        } catch (RuntimeException e) {
            LOG.warn("Không báo được đã đọc thư: mailId={}", mailId, e);
        }
    }

    /** Dòng báo đã đọc thư. Tĩnh để test. */
    static byte[] receiptLine(String reader, String subject) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(reader + " đã đọc thư «" + subject + "» / " + reader + " read your mail «" + subject + "»")
                .build()
                .toByteArray();
    }

    /** ADR-401 — phát lại tối đa {@value #HISTORY_LINES} tin bang gần nhất cho riêng người hỏi. */
    GetGuildChatHistoryResponse guildChatHistory(long characterId) {
        Optional<GuildFacade.MyGuildView> mine = guilds().findMyGuild(characterId);
        if (mine.isEmpty()) {
            return GetGuildChatHistoryResponse.newBuilder().setInGuild(false).build();
        }
        List<GuildChatBacklog.Line> lines = registry.guildChat.recent(mine.get().guildId(), HISTORY_LINES);
        for (GuildChatBacklog.Line line : lines) {
            registry.sendToCharacterIfOnline(
                    characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, Lot47Commands.replay(line));
        }
        return GetGuildChatHistoryResponse.newBuilder()
                .setInGuild(true)
                .setCount(lines.size())
                .build();
    }

    /** ADR-402 — bật/tắt (hoặc hỏi) "chỉ bạn bè được nhắn riêng". */
    SetWhisperFriendsOnlyResponse setWhisperFriendsOnly(long characterId, SetWhisperFriendsOnlyCommand command) {
        if (!command.getQueryOnly()) {
            facades().whisperPrivacy().setFriendsOnly(characterId, command.getEnabled());
        }
        return SetWhisperFriendsOnlyResponse.newBuilder()
                .setEnabled(facades().whisperPrivacy().friendsOnly(characterId))
                .build();
    }

    /** ADR-402 — {@code senderId} có được nhắn riêng cho {@code targetId} không. */
    boolean mayWhisperTo(long senderId, long targetId) {
        if (!facades().whisperPrivacy().friendsOnly(targetId)) {
            return true;
        }
        boolean friends = gateway.contentFacades()
                .socialFacades()
                .friendFacade()
                .listFriends(targetId)
                .contains(senderId);
        return WhisperPrivacyFacade.allows(true, friends, !friends && guildmates(senderId, targetId));
    }

    private boolean guildmates(long first, long second) {
        Optional<Long> mine = guilds().findMyGuild(first).map(GuildFacade.MyGuildView::guildId);
        return mine.isPresent() && mine.equals(guilds().findMyGuild(second).map(GuildFacade.MyGuildView::guildId));
    }

    private GuildFacade guilds() {
        return gateway.contentFacades().socialFacades().guildFacade();
    }

    private Lot59Facades facades() {
        return lot58().lot59();
    }

    private Lot58Facades lot58() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .lot44()
                .lot45()
                .lot46()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52()
                .lot53()
                .lot54()
                .lot56()
                .lot57()
                .lot58();
    }
}
