package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.friend.api.ContactPrivacyFacade;
import dev.kage.friend.domain.ContactPrivacyPort;
import dev.kage.protocol.ContactPrivacySetting;
import dev.kage.protocol.SetContactPrivacyCommand;
import dev.kage.protocol.SetContactPrivacyResponse;

/**
 * Không làm phiền (ADR-236) + chặn lời mời người lạ (ADR-237): công tắc và cổng kiểm cho tin riêng / lời mời nhóm /
 * giao dịch / kết bạn. Gọi trên Virtual Thread.
 */
final class ContactPrivacyCommands {

    /** Khoá lỗi khi người nhận bật "không làm phiền". */
    static final String DND_KEY = "error.target_do_not_disturb";

    /** Khoá lỗi khi người nhận chặn lời mời người lạ. */
    static final String STRANGERS_KEY = "error.target_blocks_strangers";

    /** Mã lỗi chung cho hai lần từ chối riêng tư. */
    static final int REFUSED_CODE = 403;

    /** Một lần {@code senderId} muốn liên lạc {@code targetId}. */
    record Attempt(long senderId, long targetId, ContactPrivacyFacade.Contact contact) {}

    private final GatewayFacades gateway;

    ContactPrivacyCommands(GatewayFacades gateway) {
        this.gateway = gateway;
    }

    /** Khoá lỗi nếu bị từ chối vì riêng tư; rỗng = cho qua. Không bật gì ⇒ không tra bạn bè / bang. */
    Optional<String> refusal(Attempt attempt) {
        ContactPrivacyPort.Settings target = facade().settings(attempt.targetId());
        if (!target.doNotDisturb() && !target.blockStrangerInvites()) {
            return Optional.empty();
        }
        boolean friends = gateway.contentFacades()
                .socialFacades()
                .friendFacade()
                .listFriends(attempt.targetId())
                .contains(attempt.senderId());
        ContactPrivacyFacade.Relation relation = new ContactPrivacyFacade.Relation(friends, guildmates(attempt));
        return switch (ContactPrivacyFacade.refusal(target, attempt.contact(), relation)) {
            case NONE -> Optional.empty();
            case DO_NOT_DISTURB -> Optional.of(DND_KEY);
            case STRANGER_INVITES -> Optional.of(STRANGERS_KEY);
        };
    }

    private boolean guildmates(Attempt attempt) {
        dev.kage.guild.api.GuildFacade guilds =
                gateway.contentFacades().socialFacades().guildFacade();
        Optional<Long> mine = guilds.findMyGuild(attempt.senderId()).map(g -> g.guildId());
        return mine.isPresent()
                && mine.equals(guilds.findMyGuild(attempt.targetId()).map(g -> g.guildId()));
    }

    /** Đổi một công tắc ({@code UNSPECIFIED} = chỉ hỏi) rồi trả cả hai. */
    SetContactPrivacyResponse set(long characterId, SetContactPrivacyCommand command) {
        if (command.getSetting() == ContactPrivacySetting.CONTACT_PRIVACY_SETTING_DO_NOT_DISTURB) {
            facade().setDoNotDisturb(characterId, command.getEnabled());
        } else if (command.getSetting() == ContactPrivacySetting.CONTACT_PRIVACY_SETTING_BLOCK_STRANGER_INVITES) {
            facade().setBlockStrangerInvites(characterId, command.getEnabled());
        }
        ContactPrivacyPort.Settings now = facade().settings(characterId);
        return SetContactPrivacyResponse.newBuilder()
                .setDoNotDisturb(now.doNotDisturb())
                .setBlockStrangerInvites(now.blockStrangerInvites())
                .build();
    }

    private ContactPrivacyFacade facade() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .contactPrivacy();
    }
}
