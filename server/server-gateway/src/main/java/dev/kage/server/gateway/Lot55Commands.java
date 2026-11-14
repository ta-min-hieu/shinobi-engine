package dev.kage.server.gateway;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.domain.Character;
import dev.kage.chat.domain.ChatMessage;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.guild.api.GuildFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.OfficerChatCommand;
import dev.kage.protocol.OfficerChatOutcome;
import dev.kage.protocol.OfficerChatResponse;
import dev.kage.protocol.Opcode;

/** Lớp lệnh lô 55 (ADR-367…) — MỘT thành phần của {@link Lot54Commands}. Gọi trên Virtual Thread. */
record Lot55Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-368 — biến môi trường cấp tối thiểu chat thế giới / giao dịch ({@code 0} = tắt). */
    static final String MIN_LEVEL_ENV = "KAGE_WORLD_CHAT_MIN_LEVEL";

    /** ADR-368 — mặc định khi không cấu hình. */
    static final int DEFAULT_WORLD_CHAT_MIN_LEVEL = 5;

    /** ADR-368 — cấp tối thiểu đang áp dụng. */
    static final int WORLD_CHAT_MIN_LEVEL = parseMinLevel(System.getenv(MIN_LEVEL_ENV));

    /** ADR-367 — độ dài tối đa của tin lãnh đạo (bằng tin chat thường). */
    static final int MAX_OFFICER_BODY = 200;

    private static final Logger LOG = LoggerFactory.getLogger(Lot55Commands.class);

    /** Lớp lệnh lô 56 (ADR-375…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot56Commands lot56() {
        return new Lot56Commands(gateway, registry);
    }

    /** ADR-367 — gửi tin tới bang chủ và phó bang chủ đang online (gồm cả chính người gửi, để thấy tin của mình). */
    OfficerChatResponse officerChat(long characterId, OfficerChatCommand command) {
        Optional<GuildFacade.MyGuildView> mine = guilds().findMyGuild(characterId);
        if (mine.isEmpty()) {
            return officer(OfficerChatOutcome.OFFICER_CHAT_OUTCOME_NOT_IN_GUILD);
        }
        if (!mine.get().isLeader() && !mine.get().isViceLeader()) {
            return officer(OfficerChatOutcome.OFFICER_CHAT_OUTCOME_NOT_OFFICER);
        }
        String body = ChatMessage.singleLine(command.getBody()).strip();
        if (!validOfficerBody(body)) {
            return officer(OfficerChatOutcome.OFFICER_CHAT_OUTCOME_INVALID_TEXT);
        }
        broadcastToOfficers(characterId, body);
        return officer(OfficerChatOutcome.OFFICER_CHAT_OUTCOME_OK);
    }

    /** Không rỗng, ≤ {@value #MAX_OFFICER_BODY} ký tự, không từ cấm. Tĩnh để test. */
    static boolean validOfficerBody(String body) {
        return !body.isEmpty() && body.length() <= MAX_OFFICER_BODY && !ProfanityFilter.containsBannedWord(body);
    }

    private void broadcastToOfficers(long characterId, String body) {
        String sender = gateway.characterFacade()
                .findById(characterId)
                .map(Character::name)
                .orElse("?");
        byte[] event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_GUILD)
                .setSenderId(characterId)
                .setSenderName(sender)
                .setBody(body)
                .setOfficerOnly(true)
                .build()
                .toByteArray();
        for (GuildFacade.MemberView member : guilds().listMembers(characterId)) {
            if (member.isLeader() || member.isViceLeader()) {
                registry.sendToCharacterIfOnline(
                        member.characterId(), Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event);
            }
        }
    }

    private static OfficerChatResponse officer(OfficerChatOutcome outcome) {
        return OfficerChatResponse.newBuilder().setOutcome(outcome).build();
    }

    /** Thiếu/rỗng ⇒ mặc định; số ≥ 0; giá trị lạ là lỗi cấu hình to tiếng (cùng khuôn {@code IdleKick}). */
    static int parseMinLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_WORLD_CHAT_MIN_LEVEL;
        }
        String trimmed = raw.strip();
        if (!trimmed.matches("[0-9]{1,3}")) {
            throw new IllegalStateException(MIN_LEVEL_ENV + " phải là số cấp (0 = tắt), nhận: " + raw);
        }
        return Integer.parseInt(trimmed);
    }

    /** ADR-368 — {@code true} nếu nhân vật đủ cấp chat kênh thế giới / giao dịch. */
    boolean mayChatPublicly(long characterId) {
        if (WORLD_CHAT_MIN_LEVEL <= 0) {
            return true;
        }
        return gateway.characterFacade()
                .findById(characterId)
                .map(character -> character.level() >= WORLD_CHAT_MIN_LEVEL)
                .orElse(false);
    }

    /** ADR-369 — {@code true} nếu được nhắn riêng {@code targetId} (bạn bè luôn được; người lạ thì đếm). */
    boolean mayWhisper(long senderId, long targetId) {
        boolean friends = gateway.contentFacades()
                .socialFacades()
                .friendFacade()
                .listFriends(senderId)
                .contains(targetId);
        return friends || registry.strangerWhispers.allow(senderId, targetId, System.currentTimeMillis());
    }

    /** ADR-370 — vừa đổi lời nhắn trạng thái (không rỗng) ⇒ bạn bè đang online thấy một dòng. Việc phụ. */
    // PMD AvoidCatchingGenericException: việc phụ sau khi đã lưu lời nhắn — lỗi không được làm hỏng lệnh.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void notifyStatusChanged(long characterId, String message) {
        if (message.isEmpty()) {
            return;
        }
        try {
            String name = gateway.characterFacade()
                    .findById(characterId)
                    .map(Character::name)
                    .orElse("?");
            byte[] line = statusLine(name, message);
            for (long friend :
                    gateway.contentFacades().socialFacades().friendFacade().listFriends(characterId)) {
                registry.sendToCharacterIfOnline(friend, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, line);
            }
        } catch (RuntimeException e) {
            LOG.warn("Không báo được bạn bè đổi lời nhắn: characterId={}", characterId, e);
        }
    }

    /** Dòng báo đổi lời nhắn. Tĩnh để test. */
    static byte[] statusLine(String name, String message) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(name + " đổi lời nhắn: \"" + message + "\" / " + name + " set status: \"" + message + "\"")
                .build()
                .toByteArray();
    }

    private GuildFacade guilds() {
        return gateway.contentFacades().socialFacades().guildFacade();
    }
}
