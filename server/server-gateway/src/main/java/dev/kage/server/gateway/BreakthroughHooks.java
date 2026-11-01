package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * ADR-136 — nghi lễ đột phá cảnh giới: lên cấp MỞ ĐẦU một cảnh giới (19 Trúc Cơ, 37 Tử Phủ, 55 Kim Đan, 73 Nguyên Anh,
 * 91 Hoá Thần) thì báo cả server một dòng hệ thống song ngữ (cùng khuôn thông báo bang chiến). VFX đột phá do chính
 * client phát khi gói EXP báo cảnh giới đổi — không có gói mới. Chạy trên luồng cộng EXP (Virtual Thread).
 */
final class BreakthroughHooks {

    /** Số cấp mỗi cảnh giới ở năm cảnh giới đầu (ADR-038 D2, cùng số với {@code CultivationRealm} phía client). */
    static final int LEVELS_PER_REALM = 18;

    /** Cấp mở đầu cảnh giới cuối (Hoá Thần). */
    static final int LAST_REALM_START = 91;

    /** Tên cảnh giới theo chỉ số (0 = Luyện Khí) — "vi / en", khớp chuỗi {@code realm.*} của client. */
    private static final List<String> REALM_NAMES = List.of(
            "Luyện Khí / Qi Condensation",
            "Trúc Cơ / Foundation Establishment",
            "Tử Phủ / Purple Mansion",
            "Kim Đan / Golden Core",
            "Nguyên Anh / Nascent Soul",
            "Hoá Thần / Spirit Transformation");

    private BreakthroughHooks() {}

    /** Chỉ số cảnh giới mà {@code level} MỞ ĐẦU; rỗng nếu cấp này không phải cấp đầu của một cảnh giới mới. */
    static Optional<Integer> realmOpenedAt(int level) {
        if (level <= 1 || level > LAST_REALM_START || (level - 1) % LEVELS_PER_REALM != 0) {
            return Optional.empty();
        }
        return Optional.of((level - 1) / LEVELS_PER_REALM);
    }

    static CharacterLevelSync.LevelListener listener(CharacterFacade characters, ConnectedCharacterRegistry registry) {
        return (characterId, level) -> realmOpenedAt(level).ifPresent(realm -> {
            Optional<Character> character = characters.findById(characterId);
            if (character.isEmpty()) {
                return;
            }
            String name = character.get().name();
            String[] realmName = REALM_NAMES.get(realm).split(" / ", 2);
            ChatMessageEvent event = ChatMessageEvent.newBuilder()
                    .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                    .setSenderName("")
                    .setBody(name + " đột phá " + realmName[0] + "! / " + name + " broke through to " + realmName[1]
                            + "!")
                    .build();
            registry.broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, event.toByteArray());
        });
    }
}
