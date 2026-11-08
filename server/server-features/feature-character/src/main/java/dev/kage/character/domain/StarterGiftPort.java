package dev.kage.character.domain;

/** Cờ "đã gửi gói quà tân thủ" (ADR-227) — mỗi nhân vật nhận đúng một lần. */
public interface StarterGiftPort {

    /**
     * Đánh dấu đã gửi cho {@code characterId}.
     *
     * @return {@code true} nếu lần này là lần đánh dấu ĐẦU TIÊN (người gọi gửi quà); {@code false} nếu đã gửi trước đó
     */
    boolean markSent(long characterId);
}
