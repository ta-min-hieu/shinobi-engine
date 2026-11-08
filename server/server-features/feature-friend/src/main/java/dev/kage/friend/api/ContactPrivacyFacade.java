package dev.kage.friend.api;

import dev.kage.friend.domain.ContactPrivacyPort;

/**
 * Riêng tư liên lạc (ADR-236/237). Luật thuần ở {@link #refusal}; ai là bạn / cùng bang do gateway tra rồi truyền vào
 * (gateway có {@link FriendFacade} và facade bang). Chạm DB — chỉ gọi trên Virtual Thread.
 */
public final class ContactPrivacyFacade {

    /** Không ai bật gì (composition test). */
    public static final ContactPrivacyFacade DISABLED = new ContactPrivacyFacade(new ContactPrivacyPort() {
        @Override
        public Settings settings(long characterId) {
            return Settings.NONE;
        }

        @Override
        public void setDoNotDisturb(long characterId, boolean enabled) {
            // không lưu gì
        }

        @Override
        public void setBlockStrangerInvites(long characterId, boolean enabled) {
            // không lưu gì
        }
    });

    /** Kiểu liên lạc người khác muốn gửi tới. */
    public enum Contact {
        /** Tin nhắn riêng. */
        WHISPER,
        /** Lời mời vào nhóm. */
        PARTY,
        /** Lời mời giao dịch. */
        TRADE,
        /** Lời mời kết bạn. */
        FRIEND
    }

    /** Lý do từ chối; {@link #NONE} = cho qua. */
    public enum Refusal {
        /** Cho qua. */
        NONE,
        /** Người nhận đang bật "không làm phiền" và người gửi không phải bạn (ADR-236). */
        DO_NOT_DISTURB,
        /** Người nhận chặn lời mời người lạ; người gửi không phải bạn, không cùng bang (ADR-237). */
        STRANGER_INVITES
    }

    /** Quan hệ của người gửi với người nhận, do gateway tra. */
    public record Relation(boolean friends, boolean guildmates) {}

    private final ContactPrivacyPort port;

    /** Dựng ở composition root. */
    public ContactPrivacyFacade(ContactPrivacyPort port) {
        this.port = port;
    }

    /**
     * Luật: bạn bè luôn qua. Không làm phiền chặn mọi kiểu liên lạc từ người không phải bạn (kể cả người cùng bang);
     * chặn người lạ chỉ chặn lời mời nhóm / giao dịch từ người không phải bạn, không cùng bang.
     */
    public static Refusal refusal(ContactPrivacyPort.Settings target, Contact contact, Relation relation) {
        if (relation.friends()) {
            return Refusal.NONE;
        }
        if (target.doNotDisturb()) {
            return Refusal.DO_NOT_DISTURB;
        }
        boolean invite = contact == Contact.PARTY || contact == Contact.TRADE;
        return invite && target.blockStrangerInvites() && !relation.guildmates()
                ? Refusal.STRANGER_INVITES
                : Refusal.NONE;
    }

    /** Cài đặt hiện tại của {@code characterId}. */
    public ContactPrivacyPort.Settings settings(long characterId) {
        return port.settings(characterId);
    }

    /** Bật / tắt "không làm phiền". */
    public void setDoNotDisturb(long characterId, boolean enabled) {
        port.setDoNotDisturb(characterId, enabled);
    }

    /** Bật / tắt "chặn lời mời từ người lạ". */
    public void setBlockStrangerInvites(long characterId, boolean enabled) {
        port.setBlockStrangerInvites(characterId, enabled);
    }
}
