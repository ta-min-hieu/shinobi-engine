package dev.kage.world.domain;

/**
 * Một bụi linh thảo trên world chính (ADR-098) — tham số đọc từ {@code herb_node}. Trạng thái sẵn/hồi KHÔNG ở đây
 * (RAM của gateway).
 *
 * @param itemCode mã vật phẩm hái được
 * @param kind loại điểm thu thập (ADR-107): {@code HERB} bụi linh thảo, {@code FISH} bờ câu, {@code ORE} mạch khoáng
 * @param gatherSeconds số giây phải đứng thu (ADR-107)
 */
public record HerbNode(
        long id,
        String code,
        float x,
        float y,
        long itemTemplateId,
        String itemCode,
        int quantity,
        int respawnSeconds,
        String kind,
        int gatherSeconds) {

    public HerbNode {
        if (quantity <= 0 || respawnSeconds <= 0 || gatherSeconds <= 0) {
            throw new IllegalArgumentException("herb_node " + code + ": quantity/respawn/gather phải dương");
        }
    }
}
