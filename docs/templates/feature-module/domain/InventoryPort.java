// TEMPLATE — Port: interface trong domain/, KHÔNG biết PostgreSQL/Redis.
// Adapter ở infrastructure/ sẽ implement (Dependency Inversion).
package dev.kage.inventory.domain;

import java.util.Optional;

/**
 * Cổng lưu trữ Inventory. Domain định nghĩa hợp đồng; infrastructure hiện thực.
 *
 * <p>KHÔNG có chi tiết công nghệ ở đây (không SQL, không tên bảng).
 * {@code Transaction} là abstraction của server-database, không phải java.sql.Connection.
 */
public interface InventoryPort {

    Optional<Inventory> findByCharacter(long characterId);

    /**
     * Lưu inventory trong transaction đang mở.
     * @param tx transaction bắt buộc — thao tác có giá trị phải nguyên tử ([DATABASE §5])
     */
    void save(Transaction tx, Inventory inventory);
}
