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
    };

    /** {@code item_template.move_speed_percent} của một mẫu (0 nếu không phải phi kiếm). */
    int moveSpeedPercentOf(long itemTemplateId);

    /** Cấp các linh thú ĐÃ có dòng {@code pet_state} trong túi nhân vật, theo {@code inventory_item.id}. */
    Map<Long, Integer> petLevels(long characterId);

    /** Cộng EXP cho linh thú ĐANG MẶC (một transaction); rỗng nếu không mặc con nào. */
    Optional<PetProgress> grantPetExp(long characterId, long amount);

    /** Linh thú đang mặc và tiến độ của nó; rỗng nếu không mặc. */
    Optional<PetProgress> equippedPet(long characterId);
}
