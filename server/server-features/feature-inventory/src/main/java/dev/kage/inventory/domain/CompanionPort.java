package dev.kage.inventory.domain;

import java.util.Map;
import java.util.Optional;

/** Phi kiếm (ADR-070) + linh thú (ADR-072) — hiện thực ở server-database. */
public interface CompanionPort {

    /** Không có phi kiếm/linh thú nào (composition test). */
    CompanionPort NONE = new CompanionPort() {
        @Override
        public int moveSpeedPercentOf(long itemTemplateId) {
            return 0;
        }

        @Override
        public Map<Long, Integer> petLevels(long characterId) {
            return Map.of();
        }

        @Override
        public Optional<PetProgress> grantPetExp(long characterId, long amount) {
            return Optional.empty();
        }

        @Override
        public Optional<PetProgress> equippedPet(long characterId) {
            return Optional.empty();
        }

        @Override
        public Map<Long, Integer> petStages(long characterId) {
            return Map.of();
        }

        @Override
        public Optional<PetProgress> lockEquippedPet(Transaction tx, long characterId) {
            return Optional.empty();
        }

        @Override
        public void setPetStage(Transaction tx, long inventoryItemId, int stage) {
            throw new IllegalStateException("CompanionPort.NONE không có linh thú nào để tiến hoá");
        }

        @Override
        public Optional<PetProgress> grantPetExp(Transaction tx, long characterId, long amount) {
            return Optional.empty();
        }
    };

    /** {@code item_template.move_speed_percent} của một mẫu (0 nếu không phải phi kiếm). */
    int moveSpeedPercentOf(long itemTemplateId);

    /** Cấp các linh thú ĐÃ có dòng {@code pet_state} trong túi nhân vật, theo {@code inventory_item.id}. */
    Map<Long, Integer> petLevels(long characterId);

    /** Cộng EXP cho linh thú ĐANG MẶC (một transaction); rỗng nếu không mặc con nào. */
    Optional<PetProgress> grantPetExp(long characterId, long amount);

    /** Linh thú đang mặc và tiến độ của nó; rỗng nếu không mặc. */
    Optional<PetProgress> equippedPet(long characterId);

    /** Bậc tiến hoá các linh thú ĐÃ có dòng {@code pet_state}, theo {@code inventory_item.id} (ADR-078). */
    Map<Long, Integer> petStages(long characterId);

    /**
     * Linh thú đang mặc, khoá dòng {@code pet_state} tới hết transaction (tạo dòng cấp 1 nếu chưa có); rỗng nếu
     * không mặc. Gọi SAU khoá túi đồ — thứ tự khoá chung.
     */
    Optional<PetProgress> lockEquippedPet(Transaction tx, long characterId);

    /**
     * ADR-201 — cộng EXP cho linh thú đang mặc TRONG transaction của người gọi (sau khoá túi đồ); rỗng nếu không mặc.
     */
    Optional<PetProgress> grantPetExp(Transaction tx, long characterId, long amount);

    /** Ghi bậc tiến hoá — chỉ sau {@link #lockEquippedPet} trong cùng transaction. */
    void setPetStage(Transaction tx, long inventoryItemId, int stage);
}
