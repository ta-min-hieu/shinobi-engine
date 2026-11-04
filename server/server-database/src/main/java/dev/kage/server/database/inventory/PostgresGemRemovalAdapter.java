package dev.kage.server.database.inventory;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.GemRemovalPort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link GemRemovalPort} (ADR-168). Mở đầu bằng {@link CharacterInventoryLock}; khoá dòng ô khảm; trừ phí;
 * xoá ô khảm; trả viên ngọc về túi (gộp chồng hoặc mở dòng — túi đầy thì rollback cả lần, ngọc vẫn nằm trên trang bị).
 */
public final class PostgresGemRemovalAdapter implements GemRemovalPort {

    private static final String GOLD_REASON = "gold.reason.unsocket_gem";

    private static final String OWNED = "SELECT COUNT(*) FROM inventory_item WHERE id = ? AND character_id = ?";

    private static final String LOCK_SOCKET = """
            SELECT s.gem_item_template_id
              FROM item_socket s JOIN inventory_item ii ON ii.id = s.inventory_item_id
             WHERE ii.id = ? AND ii.character_id = ?
               FOR UPDATE OF s
            """;

    private static final String REMOVE_SOCKET = "DELETE FROM item_socket WHERE inventory_item_id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một lệnh tháo. */
    private record Request(long characterId, long itemId, long fee, Instant at) {}

    private final DataSource dataSource;

    public PostgresGemRemovalAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Removed unsocket(long characterId, long itemId, long fee, Instant at) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> unsocketIn(connection, new Request(characterId, itemId, fee, at)));
        } catch (Abort abort) {
            return new Removed(abort.outcome, 0L);
        }
    }

    private static Removed unsocketIn(Connection connection, Request request) {
        long characterId = request.characterId();
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<Long> gem =
                JdbcOperations.queryOne(connection, LOCK_SOCKET, rs -> rs.getLong(1), request.itemId(), characterId);
        if (gem.isEmpty()) {
            boolean owned =
                    JdbcOperations.queryOne(connection, OWNED, rs -> rs.getInt(1), request.itemId(), characterId)
                                    .orElse(0)
                            > 0;
            throw new Abort(owned ? Outcome.NOT_SOCKETED : Outcome.NOT_FOUND);
        }
        if (!GoldLedger.deduct(connection, characterId, request.fee(), GOLD_REASON)) {
            throw new Abort(Outcome.NOT_ENOUGH_GOLD);
        }
        JdbcOperations.update(connection, REMOVE_SOCKET, request.itemId());
        long gemTemplateId = gem.get();
        if (!InventoryStackMerge.mergeIntoExistingStack(connection, characterId, gemTemplateId, 1)
                && !InventoryStackMerge.insertRespectingMaxStack(connection, characterId, gemTemplateId, 1)) {
            throw new Abort(Outcome.BAG_FULL);
        }
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "GEM_UNSOCKETED",
                "{\"itemId\":" + request.itemId() + ",\"gem\":" + gemTemplateId + ",\"gold\":" + request.fee() + "}",
                Timestamp.from(request.at()));
        return new Removed(Outcome.OK, gemTemplateId);
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;

        Abort(Outcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
