package dev.kage.crafting.domain;

/**
 * Một nguyên liệu của công thức.
 *
 * @param itemTemplateId mẫu vật phẩm bị tiêu
 * @param quantity số lượng mỗi lần luyện, {@code >= 1}
 */
public record RecipeMaterial(long itemTemplateId, int quantity) {

    public RecipeMaterial {
        if (itemTemplateId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("nguyên liệu hỏng: " + itemTemplateId + " x" + quantity);
        }
    }
}
