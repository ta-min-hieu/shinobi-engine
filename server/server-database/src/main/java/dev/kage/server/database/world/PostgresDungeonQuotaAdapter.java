package dev.kage.server.database.world;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.world.domain.DungeonQuotaPort;

/**
 * Hiện thực {@link DungeonQuotaPort} (ADR-099, V87). Trừ lượt cả nhóm trong MỘT giao dịch: đảm bảo có dòng, khoá các
 * dòng theo thứ tự id (hai nhóm chung người không deadlock), kiểm hết rồi mới cộng.
 */
public final class PostgresDungeonQuotaAdapter implements DungeonQuotaPort {

    private static final String ENSURE = """
            INSERT INTO dungeon_entry (character_id, dungeon_code, day) VALUES (?, ?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String LOCK = """
            SELECT entries FROM dungeon_entry WHERE character_id = ? AND dungeon_code = ? AND day = ? FOR UPDATE
            """;

    private static final String CONSUME = """
            UPDATE dungeon_entry SET entries = entries + 1 WHERE character_id = ? AND dungeon_code = ? AND day = ?
            """;

    private static final String USED =
            "SELECT entries FROM dungeon_entry WHERE character_id = ? AND dungeon_code = ? AND day = ?";

    private static final String FIRST_CLEAR = """
            INSERT INTO dungeon_first_clear (character_id, dungeon_code) VALUES (?, ?) ON CONFLICT DO NOTHING
            """;

    private final DataSource dataSource;

    public PostgresDungeonQuotaAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Long> tryConsume(List<Long> characterIds, String dungeonCode, LocalDate day, int maxEntries) {
        List<Long> ordered = new ArrayList<>(characterIds);
        ordered.sort(Long::compare);
        Date sqlDay = Date.valueOf(day);
        return JdbcTransactionRunner.call(dataSource, connection -> {
            for (long id : ordered) {
                JdbcOperations.update(connection, ENSURE, id, dungeonCode, sqlDay);
            }
            for (long id : ordered) {
                int used = JdbcOperations.queryOne(connection, LOCK, rs -> rs.getInt(1), id, dungeonCode, sqlDay)
                        .orElse(0);
                if (used >= maxEntries) {
                    return Optional.of(id);
                }
            }
            for (long id : ordered) {
                JdbcOperations.update(connection, CONSUME, id, dungeonCode, sqlDay);
            }
            return Optional.<Long>empty();
        });
    }

    @Override
    public int usedOn(long characterId, String dungeonCode, LocalDate day) {
        return JdbcOperations.queryOne(
                        dataSource, USED, rs -> rs.getInt(1), characterId, dungeonCode, Date.valueOf(day))
                .orElse(0);
    }

    @Override
    public boolean recordFirstClear(long characterId, String dungeonCode) {
        return JdbcOperations.update(dataSource, FIRST_CLEAR, characterId, dungeonCode) > 0;
    }
}
