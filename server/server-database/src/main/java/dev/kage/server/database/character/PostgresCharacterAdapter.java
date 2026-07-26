package dev.kage.server.database.character;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.IdempotencyKeyReusedException;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.character.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Hiện thực {@link CharacterPort} trên PostgreSQL. SQL chỉ tồn tại ở đây (Điều 24 + DATABASE.md
 * §11). Luôn {@link PreparedStatement} qua {@link JdbcOperations}, không nối chuỗi (SECURITY.md
 * §11).
 *
 * <p>3 method đọc ({@link #listByAccount}, {@link #countByAccount}, {@link #existsByName}) không
 * nhận {@code Transaction} (Port không yêu cầu — đọc thuần, không cần transaction bao quanh) nên
 * tự mở connection ngắn hạn từ {@link DataSource} riêng, khác với {@link #create} vốn dùng
 * connection của transaction đang mở (bắt buộc, để cùng transaction với ghi character/character_stat).
 */
public final class PostgresCharacterAdapter implements CharacterPort {

    // Số liệu khởi điểm — PLACEHOLDER, GDD chưa chốt (feature-character/CLAUDE.md).
    private static final int STARTING_STAT = 10;
    private static final int STARTING_HP = 100;
    private static final int STARTING_CHI = 50;

    private final DataSource dataSource;

    public PostgresCharacterAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static final String INSERT_IDEMPOTENCY_KEY = """
            INSERT INTO idempotency_key (key, created_at) VALUES (?, ?)
            """;

    private static final String INSERT_CHARACTER = """
            INSERT INTO character (account_id, realm_id, name, faction, level, exp, rebirth_count, created_at)
            VALUES (?, 1, ?, ?, 1, 0, 0, ?)
            RETURNING id
            """;

    private static final String INSERT_CHARACTER_STAT = """
            INSERT INTO character_stat (character_id, str, agi, int_, vit, current_hp, current_chi)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String LIST_BY_ACCOUNT = """
            SELECT id, account_id, realm_id, name, faction, level, exp, rebirth_count, created_at
              FROM character WHERE account_id = ? ORDER BY id
            """;

    private static final String COUNT_BY_ACCOUNT = """
            SELECT COUNT(*) FROM character WHERE account_id = ?
            """;

    private static final String EXISTS_BY_NAME = """
            SELECT 1 FROM character WHERE name = ?
            """;

    private static final String FIND_BY_NAME = """
            SELECT id, account_id, realm_id, name, faction, level, exp, rebirth_count, created_at
              FROM character WHERE name = ?
            """;

    private static final String FIND_BY_ID = """
            SELECT id, account_id, realm_id, name, faction, level, exp, rebirth_count, created_at
              FROM character WHERE id = ?
            """;

    private static final String LOAD_POSITION = """
            SELECT pos_x, pos_y FROM character WHERE id = ?
            """;

    private static final String SAVE_POSITION = """
            UPDATE character SET pos_x = ?, pos_y = ? WHERE id = ?
            """;

    private static final String LOAD_STATS = """
            SELECT str, agi, int_, vit FROM character_stat WHERE character_id = ?
            """;

    private static final String UPDATE_FACTION = """
            UPDATE character SET faction = ? WHERE id = ?
            """;

    @Override
    public List<Character> listByAccount(long accountId) {
        return JdbcOperations.query(dataSource, LIST_BY_ACCOUNT, PostgresCharacterAdapter::mapRow, accountId);
    }

    @Override
    public int countByAccount(long accountId) {
        return JdbcOperations.queryOne(dataSource, COUNT_BY_ACCOUNT, rs -> rs.getInt(1), accountId)
                .orElse(0);
    }

    @Override
    public boolean existsByName(String name) {
        return !JdbcOperations.query(dataSource, EXISTS_BY_NAME, rs -> Boolean.TRUE, name).isEmpty();
    }

    @Override
    public Optional<Character> findByName(String name) {
        return JdbcOperations.queryOne(dataSource, FIND_BY_NAME, PostgresCharacterAdapter::mapRow, name);
    }

    @Override
    public Optional<Character> findById(long characterId) {
        return JdbcOperations.queryOne(dataSource, FIND_BY_ID, PostgresCharacterAdapter::mapRow, characterId);
    }

    @Override
    public Character create(Transaction tx, NewCharacter newCharacter) {
        Connection connection = ((JdbcTransaction) tx).connection();
        insertIdempotencyKey(connection, newCharacter.idempotencyKey());
        long characterId = JdbcOperations.insertReturningId(
                connection,
                INSERT_CHARACTER,
                newCharacter.accountId(),
                newCharacter.name(),
                newCharacter.faction().name(),
                Timestamp.from(Instant.now()));
        JdbcOperations.update(
                connection,
                INSERT_CHARACTER_STAT,
                characterId,
                STARTING_STAT,
                STARTING_STAT,
                STARTING_STAT,
                STARTING_STAT,
                STARTING_HP,
                STARTING_CHI);
        return new Character(
                characterId,
                newCharacter.accountId(),
                1,
                newCharacter.name(),
                newCharacter.faction(),
                1,
                0,
                0,
                Instant.now());
    }

    private static void insertIdempotencyKey(Connection connection, String idempotencyKey) {
        JdbcOperations.updateOrThrow(
                connection,
                INSERT_IDEMPOTENCY_KEY,
                () -> new IdempotencyKeyReusedException(idempotencyKey),
                UUID.fromString(idempotencyKey),
                Timestamp.from(Instant.now()));
    }

    @Override
    public PositionSnapshot loadPosition(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        LOAD_POSITION,
                        rs -> new PositionSnapshot(rs.getFloat("pos_x"), rs.getFloat("pos_y")),
                        characterId)
                .orElseThrow(() ->
                        new DatabaseAccessException("Nhân vật không tồn tại: characterId=" + characterId, null));
    }

    @Override
    public void savePosition(long characterId, float x, float y) {
        JdbcOperations.update(dataSource, SAVE_POSITION, x, y, characterId);
    }

    @Override
    public void updateFaction(long characterId, Faction faction) {
        JdbcOperations.update(dataSource, UPDATE_FACTION, faction.name(), characterId);
    }

    @Override
    public CharacterStats loadStats(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        LOAD_STATS,
                        rs -> new CharacterStats(
                                rs.getInt("str"), rs.getInt("agi"), rs.getInt("int_"), rs.getInt("vit")),
                        characterId)
                .orElseThrow(() ->
                        new DatabaseAccessException("Nhân vật không có chỉ số: characterId=" + characterId, null));
    }

    private static Character mapRow(ResultSet rs) throws SQLException {
        return new Character(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getInt("realm_id"),
                rs.getString("name"),
                Faction.valueOf(rs.getString("faction")),
                rs.getInt("level"),
                rs.getLong("exp"),
                rs.getInt("rebirth_count"),
                rs.getTimestamp("created_at").toInstant());
    }
}
