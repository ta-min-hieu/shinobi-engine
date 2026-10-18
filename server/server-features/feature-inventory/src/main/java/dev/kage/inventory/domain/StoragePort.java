package dev.kage.inventory.domain;

import java.util.List;

/** Lưu trữ kho đồ (ADR-061) — dùng chung {@link Transaction} với túi đồ. */
public interface StoragePort {

    /** Kho của nhân vật, theo thứ tự gửi vào. */
    List<StoredItem> list(long characterId);

    /** Như {@link #list} nhưng khoá mọi dòng — gọi SAU khi đã khoá túi đồ (D4). */
    List<StoredItem> listForUpdate(Transaction tx, long characterId);

    void insert(Transaction tx, long characterId, StoredItem item);

    void updateQuantity(Transaction tx, long storedItemId, int quantity);

    void delete(Transaction tx, long storedItemId);
}
