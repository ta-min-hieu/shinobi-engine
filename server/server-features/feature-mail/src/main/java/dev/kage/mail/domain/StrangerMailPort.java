package dev.kage.mail.domain;

/** Công tắc chặn thư người lạ của một nhân vật (ADR-212). */
public interface StrangerMailPort {

    /** {@code characterId} có đang chặn thư từ người không phải bạn bè không. */
    boolean blocksStrangers(long characterId);

    /** Bật / tắt. */
    void setBlocksStrangers(long characterId, boolean enabled);
}
