package dev.kage.inventory.domain;

/** Huỷ vật phẩm (ADR-231) — xoá hẳn một chồng khỏi túi, một transaction dưới khoá túi. */
public interface ItemDestroyPort {

    /** Kết quả. */
    enum Outcome {
        OK,
        NOT_FOUND,
        LOCKED,
        EQUIPPED,
        IN_TRADE
    }

    Outcome destroy(long characterId, long itemId);
}
