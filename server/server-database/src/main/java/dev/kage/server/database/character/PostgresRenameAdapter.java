package dev.kage.server.database.character;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.character.domain.RenamePort;
import dev.kage.character.domain.RenameRules;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.TemplateQuantityConsumer;

/**
 * Hiện thực {@link RenamePort} (ADR-148, V120). MỘT giao dịch: khoá túi → khoá dòng nhân vật → hồi chiêu → tiêu một
 * Dịch Danh Phù → đổi tên (UNIQUE {@code lower(name)} của V54 ⇒ {@code NAME_TAKEN}) → lịch sử tên → audit. Từ chối ở
 * bước nào cũng ném {@link Abort} để rollback — phù chỉ mất khi tên thật sự đổi.
 */
public final class PostgresRenameAdapter implements RenamePort {

    private static final String LOCK_NAME = "SELECT name FROM character WHERE id = ? FOR UPDATE";

    private static final String LAST_CHANGE =
            "SELECT MAX(changed_at) FROM character_name_history WHERE character_id = ?";

    private static final String TALISMAN_TEMPLATE = "SELECT id FROM item_template WHERE code = ?";

    private static final String RENAME = "UPDATE character SET name = ? WHERE id = ?";

    private static final String HISTORY = """
            INSERT INTO character_name_history (character_id, old_name, new_name, changed_at) VALUES (?, ?, ?, ?)
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một lệnh đổi tên. */
    private record Request(long characterId, String newName, Instant now) {}

    private final DataSource dataSource;

    public PostgresRenameAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Result rename(long characterId, String newName, Instant now) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> renameIn(connection, new Request(characterId, newName, now)));
        } catch (Abort abort) {
            return abort.result;
        }
    }

    private static Result renameIn(Connection connection, Request request) {
        long characterId = request.characterId();
        CharacterInventoryLock.acquire(connection, characterId);
        String oldName = JdbcOperations.queryOne(connection, LOCK_NAME, rs -> rs.getString(1), characterId)
                .orElseThrow(() -> new Abort(Outcome.NOT_FOUND, "", Optional.empty()));
        if (oldName.equals(request.newName())) {
            throw new Abort(Outcome.SAME_NAME, oldName, Optional.empty());
        }
        Optional<Instant> lastChange = JdbcOperations.queryOne(
                        connection, LAST_CHANGE, rs -> Optional.ofNullable(rs.getTimestamp(1)), characterId)
                .flatMap(stamp -> stamp.map(Timestamp::toInstant));
        if (lastChange.isPresent()) {
            Instant next = lastChange.get().plus(RenameRules.COOLDOWN);
            if (next.isAfter(request.now())) {
                throw new Abort(Outcome.ON_COOLDOWN, oldName, Optional.of(next));
            }
        }
        Optional<Long> talisman =
                JdbcOperations.queryOne(connection, TALISMAN_TEMPLATE, rs -> rs.getLong(1), RenameRules.TALISMAN_CODE);
        if (talisman.isEmpty() || !TemplateQuantityConsumer.consume(connection, characterId, talisman.get(), 1)) {
            throw new Abort(Outcome.NO_TALISMAN, oldName, Optional.empty());
        }
        JdbcOperations.updateOrThrow(
                connection,
                RENAME,
                () -> new Abort(Outcome.NAME_TAKEN, oldName, Optional.empty()),
                request.newName(),
                characterId);
        Timestamp at = Timestamp.from(request.now());
        JdbcOperations.update(connection, HISTORY, characterId, oldName, request.newName(), at);
        // Tên đi vào JSON — chỉ chữ/số/gạch dưới (luật tên), không có ký tự cần thoát.
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "CHARACTER_RENAME",
                "{\"from\":\"" + oldName + "\",\"to\":\"" + request.newName() + "\"}",
                at);
        return new Result(
                Outcome.OK, request.newName(), Optional.of(request.now().plus(RenameRules.COOLDOWN)));
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Result result;

        Abort(Outcome outcome, String name, Optional<Instant> nextAllowedAt) {
            super(outcome.name(), null, false, false);
            this.result = new Result(outcome, name, nextAllowedAt);
        }
    }
}
