package dev.kage.crafting.domain;

import java.util.List;
import java.util.Objects;

/**
 * Một công thức luyện đan (ADR-079 D2) — dữ liệu bảng {@code crafting_recipe} + {@code crafting_recipe_material}.
 *
 * @param code mã công thức — thứ client gửi lên
 * @param outputItemTemplateId món luyện ra
 * @param outputQuantity số lượng ra mỗi lần, {@code >= 1}
 * @param goldCost linh thạch mỗi lần, {@code >= 0}
 * @param requiredLevel cấp nhân vật tối thiểu
 * @param materials nguyên liệu, không rỗng
 */
public record Recipe(
        String code,
        long outputItemTemplateId,
        int outputQuantity,
        long goldCost,
        int requiredLevel,
        List<RecipeMaterial> materials) {

    public Recipe {
        Objects.requireNonNull(code, "code");
        materials = List.copyOf(materials);
        if (outputQuantity <= 0 || goldCost < 0 || materials.isEmpty()) {
            throw new IllegalArgumentException("công thức hỏng: " + code);
        }
    }
}
