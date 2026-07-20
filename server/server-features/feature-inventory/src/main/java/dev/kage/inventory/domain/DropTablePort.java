package dev.kage.inventory.domain;

import java.util.List;

/**
 * Cổng đọc bảng rơi đồ — PHÁT ĐỌC THUẦN (không random ở đây, xem {@code GrantLootUseCase} cho
 * việc roll). Tách roll ra khỏi Port để domain roll được test mà không cần Fake random.
 */
public interface DropTablePort {

    /** {@code dropTableCode} không tồn tại → danh sách rỗng (không ném lỗi — an toàn hơn cho lát cắt). */
    List<DropTableEntry> findEntries(String dropTableCode);
}
