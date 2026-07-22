package dev.kage.inventory.domain;

import java.util.Optional;

/** Cổng đọc dữ liệu tĩnh {@code item_template} — PHÁT ĐỌC THUẦN, không ghi (item_template là config). */
public interface ItemTemplatePort {

    /** {@code Optional.empty()} nếu {@code itemTemplateId} không tồn tại (dữ liệu hỏng/xoá nhầm). */
    Optional<ItemTemplate> findById(long itemTemplateId);
}
