package dev.kage.server.gateway;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * Ba cách báo một người chơi mà các tính năng lô 21 cần — thư hệ thống (thưởng), dòng chat hệ thống (báo nhanh nếu
 * đang online) và gói sự kiện — gom một chỗ để mỗi lớp {@code *Commands} chỉ cầm một tham số thay vì thư + registry.
 * Gửi thư chạm DB: chỉ gọi trên Virtual Thread.
 */
final class PlayerNotices {

    private static final Logger LOG = LoggerFactory.getLogger(PlayerNotices.class);

    private final MailFacade mail;
    private final ConnectedCharacterRegistry registry;

    PlayerNotices(MailFacade mail, ConnectedCharacterRegistry registry) {
        this.mail = mail;
        this.registry = registry;
    }

    /** Một lá thư hệ thống: tiêu đề + thân (song ngữ "vi / en"), linh thạch, vật phẩm (có thể rỗng). */
    record SystemMail(String subject, String body, long gold, List<MailFacade.AttachmentRequest> items) {

        /** Thư chỉ có linh thạch. */
        SystemMail(String subject, String body, long gold) {
            this(subject, body, gold, List.of());
        }

        /** Danh sách bất biến — record không tự copy. */
        SystemMail {
            items = List.copyOf(items);
        }
    }

    /** Gửi thư hệ thống; hỏng thì log WARN — phần thưởng mất có dấu vết. */
    void mail(long characterId, SystemMail letter) {
        String subject = letter.subject();
        MailFacade.SendOutcome outcome = mail.sendMail(
                new MailFacade.SendMailRequest(
                        null, characterId, subject, letter.body(), letter.gold(), letter.items()),
                UUID.randomUUID().toString());
        if (outcome.result() != MailFacade.SendResult.OK) {
            LOG.warn(
                    "Thư hệ thống không gửi được: characterId={}, tiêu đề='{}' — {}",
                    characterId,
                    subject,
                    outcome.result());
        }
    }

    /** Một dòng chat hệ thống (kênh UNSPECIFIED, không người gửi) nếu đang online. */
    void systemLine(long characterId, String body) {
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build();
        registry.sendToCharacterIfOnline(characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event.toByteArray());
    }

    /** Một gói sự kiện nếu đang online. */
    void event(long characterId, int opcode, byte[] payload) {
        registry.sendToCharacterIfOnline(characterId, opcode, 0, payload);
    }

    boolean isOnline(long characterId) {
        return registry.isOnline(characterId);
    }
}
