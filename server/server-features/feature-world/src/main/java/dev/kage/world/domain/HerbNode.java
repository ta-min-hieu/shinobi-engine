package dev.kage.world.domain;

/**
 * Một bụi linh thảo trên world chính (ADR-098) — tham số đọc từ {@code herb_node}. Trạng thái sẵn/hồi KHÔNG ở đây
 * (RAM của gateway).
 *
 * @param itemCode mã vật phẩm hái được
 */
public record HerbNode(
        long id,
        String code,
        float x,
        float y,
        long itemTemplateId,
        String itemCode,
        int quantity,
        int respawnSeconds) {

    public HerbNode {
        if (quantity <= 0 || respawnSeconds <= 0) {
            throw new IllegalArgumentException("herb_node " + code + ": quantity/respawn phải dương");
        }
    }
}
