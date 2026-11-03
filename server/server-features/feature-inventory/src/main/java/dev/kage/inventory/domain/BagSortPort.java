package dev.kage.inventory.domain;

/** Sắp xếp túi đồ (ADR-160) — đọc túi, áp {@link BagSortPolicy}, ghi lại trong MỘT transaction có khoá túi. */
public interface BagSortPort {

    /** Gom chồng + xếp lại túi; trả số dòng đã gom mất (0 nếu chỉ đổi thứ tự). */
    int sort(long characterId);
}
