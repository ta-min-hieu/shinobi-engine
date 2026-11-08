package dev.kage.server.database.character;

import javax.sql.DataSource;

import dev.kage.character.domain.StarterGiftPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link StarterGiftPort} (ADR-227, V166) — một câu UPDATE có điều kiện: chỉ một lần thắng. */
public final class PostgresStarterGiftAdapter implements StarterGiftPort {

    private static final String MARK =
            "UPDATE character SET starter_gift_sent = TRUE WHERE id = ? AND NOT starter_gift_sent";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresStarterGiftAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean markSent(long characterId) {
        return JdbcOperations.update(dataSource, MARK, characterId) == 1;
    }
}
