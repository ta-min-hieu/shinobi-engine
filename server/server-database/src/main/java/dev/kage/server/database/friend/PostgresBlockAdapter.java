package dev.kage.server.database.friend;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.friend.domain.BlockOutcome;
import dev.kage.friend.domain.BlockPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link BlockPort} (ADR-091, V85). {@link #block} là một giao dịch: khoá dòng nhân vật chặn (hai lệnh chặn
 * song song không vượt trần) → đếm → ghi → xoá mọi quan hệ giữa hai người (tình bạn kể cả lời mời, đạo lữ, sư đồ).
 */
public final class PostgresBlockAdapter implements BlockPort {

    private static final String LOCK_BLOCKER = "SELECT id FROM character WHERE id = ? FOR UPDATE";

    private static final String COUNT_BLOCKED = "SELECT COUNT(*) FROM character_block WHERE blocker_id = ?";

    private static final String INSERT_BLOCK = """
            INSERT INTO character_block (blocker_id, blocked_id) VALUES (?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String DELETE_FRIENDSHIP = """
            DELETE FROM friendship
             WHERE (requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?)
            """;

    private static final String DELETE_DAO_LU = """
            DELETE FROM dao_lu WHERE (character_id = ? AND partner_id = ?) OR (character_id = ? AND partner_id = ?)
            """;

    private static final String DELETE_MENTORSHIP = """
            DELETE FROM mentorship WHERE (disciple_id = ? AND mentor_id = ?) OR (disciple_id = ? AND mentor_id = ?)
            """;

    private static final String DELETE_BLOCK = "DELETE FROM character_block WHERE blocker_id = ? AND blocked_id = ?";

    private static final String LIST_BLOCKED =
            "SELECT blocked_id FROM character_block WHERE blocker_id = ? ORDER BY created_at, blocked_id";

    private static final String EITHER_BLOCKS = """
            SELECT 1 FROM character_block
             WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?)
             LIMIT 1
            """;

    private final DataSource dataSource;

    public PostgresBlockAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public BlockOutcome block(long blockerId, long blockedId, int maxBlocked) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> blockIn(connection, blockerId, blockedId, maxBlocked));
    }

    private static BlockOutcome blockIn(Connection connection, long blockerId, long blockedId, int maxBlocked) {
        JdbcOperations.lock(connection, LOCK_BLOCKER, blockerId);
        long count = JdbcOperations.queryOne(connection, COUNT_BLOCKED, rs -> rs.getLong(1), blockerId)
                .orElse(0L);
        if (count >= maxBlocked) {
            return BlockOutcome.LIST_FULL;
        }
        if (JdbcOperations.update(connection, INSERT_BLOCK, blockerId, blockedId) == 0) {
            return BlockOutcome.ALREADY_BLOCKED;
        }
        JdbcOperations.update(connection, DELETE_FRIENDSHIP, blockerId, blockedId, blockedId, blockerId);
        JdbcOperations.update(connection, DELETE_DAO_LU, blockerId, blockedId, blockedId, blockerId);
        JdbcOperations.update(connection, DELETE_MENTORSHIP, blockerId, blockedId, blockedId, blockerId);
        return BlockOutcome.OK;
    }

    @Override
    public BlockOutcome unblock(long blockerId, long blockedId) {
        return JdbcOperations.update(dataSource, DELETE_BLOCK, blockerId, blockedId) == 0
                ? BlockOutcome.NOT_BLOCKED
                : BlockOutcome.OK;
    }

    @Override
    public List<Long> blockedBy(long blockerId) {
        return JdbcOperations.query(dataSource, LIST_BLOCKED, rs -> rs.getLong(1), blockerId);
    }

    @Override
    public boolean eitherBlocks(long firstId, long secondId) {
        return JdbcOperations.queryOne(
                        dataSource, EITHER_BLOCKS, rs -> Boolean.TRUE, firstId, secondId, secondId, firstId)
                .isPresent();
    }
}
