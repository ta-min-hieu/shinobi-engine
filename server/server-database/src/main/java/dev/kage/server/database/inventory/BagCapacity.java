package dev.kage.server.database.inventory;

import java.sql.Connection;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;

/**
 * Sức chứa túi đồ của MỘT nhân vật (ADR-088, V83 {@code character_stat.bag_capacity}) — nguồn chung thay cho bốn hằng
 * số {@code 20} từng chép rời ở túi đồ, thư/cửa hàng ({@link InventoryStackMerge}), quest và giao dịch. Đồ đang mặc
 * không chiếm ô (không đổi so với trước).
 */
public final class BagCapacity {

    /** Sức chứa khởi điểm — bằng hằng số cũ. */
    public static final int DEFAULT = 20;

    /** Trần sau mọi lần mở rộng (CHECK của V83). */
    public static final int MAX = 40;

    private static final String SELECT = "SELECT bag_capacity FROM character_stat WHERE character_id = ?";

    private static final String EXPAND = """
            UPDATE character_stat SET bag_capacity = bag_capacity + ?
             WHERE character_id = ? AND bag_capacity + ? <= ?
            """;

    private BagCapacity() {}

    /** Sức chứa hiện tại; {@link #DEFAULT} nếu chưa có dòng chỉ số (không xảy ra với nhân vật thật). */
    public static int of(Connection connection, long characterId) {
        return JdbcOperations.queryOne(connection, SELECT, rs -> rs.getInt(1), characterId)
                .orElse(DEFAULT);
    }

    /** Như {@link #of(Connection, long)}, cho đường chỉ đọc ngoài giao dịch. */
    public static int of(DataSource dataSource, long characterId) {
        return JdbcOperations.queryOne(dataSource, SELECT, rs -> rs.getInt(1), characterId)
                .orElse(DEFAULT);
    }

    /**
     * Cộng {@code amount} ô nếu không vượt {@link #MAX} — một câu UPDATE có điều kiện.
     *
     * @return {@code false} nếu đã chạm trần (không đổi gì)
     */
    public static boolean expand(Connection connection, long characterId, int amount) {
        return JdbcOperations.update(connection, EXPAND, amount, characterId, amount, MAX) == 1;
    }
}
