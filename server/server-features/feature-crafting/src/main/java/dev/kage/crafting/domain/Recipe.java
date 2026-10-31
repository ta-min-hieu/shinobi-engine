package dev.kage.crafting.domain;

import java.util.List;
import java.util.Objects;

/**
 * Một công thức luyện đan (ADR-079 D2) hoặc luyện khí (ADR-120) — dữ liệu bảng {@code crafting_recipe} + {@code
 * crafting_recipe_material}.
 *
 * @param code mã công thức — thứ client gửi lên
 * @param outputItemTemplateId món luyện ra
 * @param outputQuantity số lượng ra mỗi lần, {@code >= 1}
 * @param goldCost linh thạch mỗi lần, {@code >= 0}
 * @param requiredLevel cấp nhân vật tối thiểu
 * @param materials nguyên liệu, không rỗng
 * @param kind luyện đan hay luyện khí — luyện khí ra đúng MỘT món và được tung phẩm chất
 */
public record Recipe(
        String code,
        long outputItemTemplateId,
        int outputQuantity,
        long goldCost,
        int requiredLevel,
        List<RecipeMaterial> materials,
        RecipeKind kind) {

    public Recipe {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(kind, "kind");
        materials = List.copyOf(materials);
        if (outputQuantity <= 0 || goldCost < 0 || materials.isEmpty()) {
            throw new IllegalArgumentException("công thức hỏng: " + code);
        }
        if (kind == RecipeKind.FORGE && outputQuantity != 1) {
            throw new IllegalArgumentException("công thức luyện khí phải ra đúng 1 món: " + code);
        }
    }
}
