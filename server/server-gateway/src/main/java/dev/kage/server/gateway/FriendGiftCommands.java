package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.FriendGiftFacade;
import dev.kage.friend.application.FriendGiftUseCase;
import dev.kage.friend.domain.FriendGiftOutcome;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendFriendGiftOutcome;
import dev.kage.protocol.SendFriendGiftResponse;

/**
 * Tặng quà bạn bè (ADR-084) — tách khỏi {@code GatewayChannelHandler}. Tra tên, gọi feature-friend, báo người nhận một
 * dòng hệ thống, và khi cặp bạn chạm mốc thân mật thì gửi THƯ HỆ THỐNG thưởng cho CẢ HAI (feature-friend không biết
 * thư).
 * Chạy trên Virtual Thread (chạm DB).
 */
final class FriendGiftCommands {

    private static final Logger LOG = LoggerFactory.getLogger(FriendGiftCommands.class);

    /** Linh thạch thưởng mỗi người khi chạm một mốc = mốc × hệ số này (100 ⇒ 500, 300 ⇒ 1.500, 600 ⇒ 3.000). */
    private static final long GOLD_PER_INTIMACY_AT_MILESTONE = 5L;

    private static final Map<FriendGiftOutcome, SendFriendGiftOutcome> OUTCOMES =
            new EnumMap<>(FriendGiftOutcome.class);

    static {
        OUTCOMES.put(FriendGiftOutcome.OK, SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_OK);
        OUTCOMES.put(FriendGiftOutcome.NOT_FRIENDS, SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_NOT_FRIENDS);
        OUTCOMES.put(
                FriendGiftOutcome.ALREADY_GIFTED_TODAY,
                SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_ALREADY_GIFTED_TODAY);
        OUTCOMES.put(
                FriendGiftOutcome.INSUFFICIENT_GOLD, SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_INSUFFICIENT_GOLD);
        OUTCOMES.put(
                FriendGiftOutcome.CANNOT_GIFT_SELF, SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_CANNOT_GIFT_SELF);
    }

    private final FriendGiftFacade gifts;
    private final CharacterFacade characters;
    private final MailFacade mail;
    private final ConnectedCharacterRegistry registry;

    FriendGiftCommands(
            FriendGiftFacade gifts, CharacterFacade characters, MailFacade mail, ConnectedCharacterRegistry registry) {
        this.gifts = gifts;
        this.characters = characters;
        this.mail = mail;
        this.registry = registry;
    }

    SendFriendGiftResponse gift(long senderId, String targetName) {
        Optional<Character> target = characters.findByName(targetName.strip());
        if (target.isEmpty()) {
            return SendFriendGiftResponse.newBuilder()
                    .setOutcome(SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_TARGET_NOT_FOUND)
                    .build();
        }
        long recipientId = target.get().id();
        FriendGiftUseCase.Result result = gifts.gift(senderId, recipientId);
        if (result.outcome() == FriendGiftOutcome.OK) {
            String senderName =
                    characters.findById(senderId).map(Character::name).orElse("#" + senderId);
            notify(
                    recipientId,
                    "%s tặng ngươi một món quà (thân mật %d). / %s sent you a gift (intimacy %d)."
                            .formatted(senderName, result.intimacy(), senderName, result.intimacy()));
            result.milestone().ifPresent(milestone -> rewardMilestone(senderId, recipientId, milestone));
        }
        return SendFriendGiftResponse.newBuilder()
                .setOutcome(OUTCOMES.get(result.outcome()))
                .setIntimacy(result.intimacy())
                .setMilestone(result.milestone().orElse(0))
                .build();
    }

    Map<Long, Integer> intimacyOf(long characterId) {
        return gifts.intimacyOf(characterId);
    }

    private void rewardMilestone(long senderId, long recipientId, int milestone) {
        String body = "Thân mật đạt %d. / Intimacy reached %d.".formatted(milestone, milestone);
        for (long characterId : List.of(senderId, recipientId)) {
            MailFacade.SendOutcome outcome = mail.sendMail(
                    new MailFacade.SendMailRequest(
                            null,
                            characterId,
                            "Thân mật / Intimacy",
                            body,
                            milestone * GOLD_PER_INTIMACY_AT_MILESTONE,
                            List.of()),
                    UUID.randomUUID().toString());
            if (outcome.result() != MailFacade.SendResult.OK) {
                LOG.warn("Thư mốc thân mật không gửi được: characterId={} — {}", characterId, outcome.result());
            }
        }
    }

    private void notify(long characterId, String body) {
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build();
        registry.sendToCharacterIfOnline(characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event.toByteArray());
    }
}
