package dev.kage.inventory.domain;

import java.util.Optional;

/**
 * Kỹ năng thụ động theo LOÀI linh thú (ADR-086), chỉ trên đòn đánh thường. Bậc tiến hoá 2 (ADR-078) gấp đôi xác suất.
 * Mọi con số là PLACEHOLDER cân bằng.
 */
public enum PetSkill {
    /** Linh Hồ — xác suất đòn đánh thường gây thêm 50%. */
    STRIKE("spirit_fox"),
    /** Huyền Quy — xác suất chắn nửa sát thương đòn đánh thường nhận vào. */
    GUARD("spirit_turtle");

    /** Xác suất ở bậc 1, phần nghìn. */
    public static final int BASE_CHANCE_PERMILLE = 100;

    private final String petCode;

    PetSkill(String petCode) {
        this.petCode = petCode;
    }

    /** Kỹ năng của loài mang {@code item_template.code}; rỗng nếu loài đó không có kỹ năng. */
    public static Optional<PetSkill> ofPet(String code) {
        for (PetSkill skill : values()) {
            if (skill.petCode.equals(code)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    /** Xác suất (phần nghìn) ở bậc tiến hoá {@code stage}. */
    public static int chancePermille(int stage) {
        return stage >= PetPolicy.MAX_STAGE ? BASE_CHANCE_PERMILLE * 2 : BASE_CHANCE_PERMILLE;
    }
}
