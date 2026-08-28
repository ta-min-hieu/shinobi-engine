package dev.kage.inventory.domain;

import java.util.List;
import java.util.Optional;

/** Cổng đọc dữ liệu tĩnh {@code item_template} — PHÁT ĐỌC THUẦN, không ghi (item_template là config). */
public interface ItemTemplatePort {

    /** {@code Optional.empty()} nếu {@code itemTemplateId} không tồn tại (dữ liệu hỏng/xoá nhầm). */
    Optional<ItemTemplate> findById(long itemTemplateId);

    /**
     * Tra {@code item_template.id} theo {@code code} (khoá nghiệp vụ ổn định, VD
     * {@code "enhance_protection_charm"}) — dùng khi UseCase cần định danh 1 item ĐẶC BIỆT mà
     * không nên hardcode ID số (ID phụ thuộc thứ tự seed). {@code Optional.empty()} nếu chưa seed.
     */
    Optional<Long> findIdByCode(String code);

    /**
     * TOÀN BỘ catalog vật phẩm — dùng cho {@code GetItemCatalogUseCase} (ADR-035 Decision-2), KHÔNG
     * dùng trong đường gameplay nóng nào. Trả về đủ 6 cột của {@code item_template} (khác
     * {@link #findById} vốn chỉ mang phần "mặc đồ"), xem {@link ItemCatalogEntry}.
     *
     * <p>{@code default} rỗng để Fake/anonymous class hiện có (chỉ implement 2 method trên) không
     * vỡ compile — cùng lý do đã ghi ở {@code QuestTemplatePort#listAllScriptRefs}. Chỉ adapter
     * Postgres thật cần ghi đè.
     */
    default List<ItemCatalogEntry> listCatalog() {
        return List.of();
    }
}
