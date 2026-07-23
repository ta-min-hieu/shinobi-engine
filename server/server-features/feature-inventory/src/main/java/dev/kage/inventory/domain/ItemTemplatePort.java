package dev.kage.inventory.domain;

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
}
