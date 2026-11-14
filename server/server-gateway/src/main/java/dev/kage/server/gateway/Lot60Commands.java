package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.kage.character.domain.Character;
import dev.kage.protocol.GetCharacterAgeRequest;
import dev.kage.protocol.GetCharacterAgeResponse;
import dev.kage.protocol.GetMutualFriendsRequest;
import dev.kage.protocol.GetMutualFriendsResponse;
import dev.kage.protocol.GetWorldChatHistoryResponse;
import dev.kage.protocol.Opcode;

/** Lớp lệnh lô 60 (ADR-407…) — MỘT thành phần của {@link Lot59Commands}. Gọi trên Virtual Thread. */
record Lot60Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-407 — biến môi trường cấp tối thiểu gửi thư cho người không phải bạn ({@code 0} = tắt). */
    static final String MAIL_MIN_LEVEL_ENV = "KAGE_MAIL_MIN_LEVEL";

    /** ADR-407 — mặc định khi không cấu hình (bằng cấp tối thiểu chat thế giới, ADR-368). */
    static final int DEFAULT_MAIL_MIN_LEVEL = 5;

    /** ADR-407 — cấp tối thiểu đang áp dụng. */
    static final int MAIL_MIN_LEVEL =
            Lot55Commands.parseMinLevel(System.getenv(MAIL_MIN_LEVEL_ENV), DEFAULT_MAIL_MIN_LEVEL, MAIL_MIN_LEVEL_ENV);

    /** ADR-408 — số tin thế giới phát lại tối đa. */
    static final int WORLD_HISTORY_LINES = 20;

    /** ADR-408 — khoá duy nhất của bộ nhớ tin thế giới (dùng chung lớp bộ nhớ tin bang). */
    static final long WORLD_KEY = 0L;

    private static final java.time.format.DateTimeFormatter TIME =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneOffset.UTC);

    /** ADR-407 — {@code senderId} có được gửi thư cho {@code recipientId} không (bạn bè luôn được). */
    boolean mayMail(long senderId, long recipientId) {
        if (MAIL_MIN_LEVEL <= 0) {
            return true;
        }
        int level = gateway.characterFacade()
                .findById(senderId)
                .map(Character::level)
                .orElse(0);
        return level >= MAIL_MIN_LEVEL
                || gateway.contentFacades()
                        .socialFacades()
                        .friendFacade()
                        .listFriends(senderId)
                        .contains(recipientId);
    }

    /** ADR-408 — phát lại tối đa {@value #WORLD_HISTORY_LINES} tin thế giới gần nhất cho riêng người hỏi. */
    GetWorldChatHistoryResponse worldChatHistory(long characterId) {
        List<GuildChatBacklog.Line> lines = registry.worldChat.recent(WORLD_KEY, WORLD_HISTORY_LINES);
        for (GuildChatBacklog.Line line : lines) {
            registry.sendToCharacterIfOnline(characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, worldReplay(line));
        }
        return GetWorldChatHistoryResponse.newBuilder().setCount(lines.size()).build();
    }

    /** Tin thế giới phát lại, kèm giờ gửi. Tĩnh để test. */
    static byte[] worldReplay(GuildChatBacklog.Line line) {
        return dev.kage.protocol.ChatMessageEvent.newBuilder()
                .setChannel(dev.kage.protocol.ChatChannel.CHAT_CHANNEL_WORLD)
                .setSenderName(line.sender())
                .setBody("[" + TIME.format(line.at()) + " UTC] " + line.body())
                .build()
                .toByteArray();
    }

    /** ADR-409 — bạn chung của mình và người tên {@code target_name}. */
    GetMutualFriendsResponse mutualFriends(long characterId, GetMutualFriendsRequest request) {
        Optional<Character> target =
                gateway.characterFacade().findByName(request.getTargetName().strip());
        if (target.isEmpty()) {
            return GetMutualFriendsResponse.newBuilder().setFound(false).build();
        }
        var friends = gateway.contentFacades().socialFacades().friendFacade();
        Set<Long> mine = new HashSet<>(friends.listFriends(characterId));
        List<String> names = new ArrayList<>();
        for (long id : friends.listFriends(target.get().id())) {
            if (mine.contains(id)) {
                gateway.characterFacade().findById(id).map(Character::name).ifPresent(names::add);
            }
        }
        names.sort(String::compareTo);
        return GetMutualFriendsResponse.newBuilder()
                .setFound(true)
                .setTargetName(target.get().name())
                .addAllNames(names)
                .build();
    }

    /** ADR-410 — ngày tạo + số ngày tuổi của một nhân vật (tên rỗng = mình). */
    GetCharacterAgeResponse characterAge(long characterId, GetCharacterAgeRequest request) {
        String name = request.getTargetName().strip();
        Optional<Character> found = name.isEmpty()
                ? gateway.characterFacade().findById(characterId)
                : gateway.characterFacade().findByName(name);
        if (found.isEmpty()) {
            return GetCharacterAgeResponse.newBuilder().setFound(false).build();
        }
        Character character = found.get();
        return GetCharacterAgeResponse.newBuilder()
                .setFound(true)
                .setName(character.name())
                .setCreatedEpochSeconds(character.createdAt().getEpochSecond())
                .setDays(ageDays(character.createdAt(), Instant.now()))
                .build();
    }

    /** Số ngày trọn từ {@code created} tới {@code now} (âm ⇒ 0). Tĩnh để test. */
    static int ageDays(Instant created, Instant now) {
        return (int) Math.max(0L, Duration.between(created, now).toDays());
    }
}
