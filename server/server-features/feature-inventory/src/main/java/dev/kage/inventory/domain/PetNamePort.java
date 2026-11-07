package dev.kage.inventory.domain;

import java.util.Optional;

/** Tên riêng của linh thú (ADR-193) — cột {@code pet_state.custom_name}, theo dòng túi đồ của linh thú đang mặc. */
public interface PetNamePort {

    /**
     * Đặt tên cho linh thú ĐANG MẶC; {@code name} rỗng = xoá tên riêng.
     *
     * @return {@code false} và không ghi gì nếu không mặc linh thú nào
     */
    boolean renameEquipped(long characterId, String name);

    /** Tên riêng của linh thú đang mặc; rỗng nếu không mặc hoặc chưa đặt tên. */
    Optional<String> equippedName(long characterId);
}
