package dev.kage.friend.domain;

/** Hai công tắc riêng tư liên lạc của một nhân vật (ADR-236 không làm phiền, ADR-237 chặn lời mời người lạ). */
public interface ContactPrivacyPort {

    /** Cài đặt hiện tại. */
    record Settings(boolean doNotDisturb, boolean blockStrangerInvites) {

        /** Mặc định: tắt cả hai. */
        public static final Settings NONE = new Settings(false, false);
    }

    /** Cài đặt của {@code characterId}; nhân vật không tồn tại ⇒ {@link Settings#NONE}. */
    Settings settings(long characterId);

    /** Bật / tắt "không làm phiền". */
    void setDoNotDisturb(long characterId, boolean enabled);

    /** Bật / tắt "chặn lời mời từ người lạ". */
    void setBlockStrangerInvites(long characterId, boolean enabled);
}
