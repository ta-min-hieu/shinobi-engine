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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Hiện thực {@link CharacterPort} trên PostgreSQL. SQL chỉ tồn tại ở đây (Điều 24 + DATABASE.md
 * §11). Luôn {@link PreparedStatement}, không nối chuỗi (SECURITY.md §11).
 *
 * <p>3 method đọc ({@link #listByAccount}, {@link #countByAccount}, {@link #existsByName}) không
 * nhận {@code Transaction} (Port không yêu cầu — đọc thuần, không cần transaction bao quanh) nên
 * tự mở connection ngắn hạn từ {@link DataSource} riêng, khác với {@link #create} vốn dùng
 * connection của transaction đang mở (bắt buộc, để cùng transaction với ghi character/character_stat).
 */
public final class PostgresCharacterAdapter implements CharacterPort {

    // Mã lỗi Postgres cho unique_violation (SQLState) — dùng để phát hiện idempotencyKey trùng.
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

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

    private static final String LOAD_POSITION = """
            SELECT pos_x, pos_y FROM character WHERE id = ?
            """;

    private static final String SAVE_POSITION = """
            UPDATE character SET pos_x = ?, pos_y = ? WHERE id = ?
            """;

    private static final String LOAD_STATS = """
            SELECT str, agi, int_, vit FROM character_stat WHERE character_id = ?
            """;

    @Override
    public List<Character> listByAccount(long accountId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LIST_BY_ACCOUNT)) {
            statement.setLong(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                var result = new ArrayList<Character>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê nhân vật: accountId=" + accountId, e);
        }
    }

    @Override
    public int countByAccount(long accountId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(COUNT_BY_ACCOUNT)) {
            statement.setLong(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đếm nhân vật: accountId=" + accountId, e);
        }
    }

    @Override
    public boolean existsByName(String name) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(EXISTS_BY_NAME)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi kiểm tra tên nhân vật: " + name, e);
        }
    }

    @Override
    public Character create(Transaction tx, NewCharacter newCharacter) {
        var connection = ((JdbcTransaction) tx).connection();
        try {
            insertIdempotencyKey(connection, newCharacter.idempotencyKey());
            long characterId = insertCharacter(connection, newCharacter);
            insertCharacterStat(connection, characterId);
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
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tạo nhân vật: " + newCharacter.name(), e);
        }
    }

    private static void insertIdempotencyKey(Connection connection, String idempotencyKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_IDEMPOTENCY_KEY)) {
            statement.setObject(1, UUID.fromString(idempotencyKey), Types.OTHER);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            throw e;
        }
    }

    private static long insertCharacter(Connection connection, NewCharacter newCharacter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CHARACTER)) {
            statement.setLong(1, newCharacter.accountId());
            statement.setString(2, newCharacter.name());
            statement.setString(3, newCharacter.faction().name());
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void insertCharacterStat(Connection connection, long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CHARACTER_STAT)) {
            statement.setLong(1, characterId);
            statement.setInt(2, STARTING_STAT);
            statement.setInt(3, STARTING_STAT);
            statement.setInt(4, STARTING_STAT);
            statement.setInt(5, STARTING_STAT);
            statement.setInt(6, STARTING_HP);
            statement.setInt(7, STARTING_CHI);
            statement.executeUpdate();
        }
    }

    @Override
    public PositionSnapshot loadPosition(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LOAD_POSITION)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new DatabaseAccessException("Nhân vật không tồn tại: characterId=" + characterId, null);
                }
                return new PositionSnapshot(rs.getFloat("pos_x"), rs.getFloat("pos_y"));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc vị trí nhân vật: characterId=" + characterId, e);
        }
    }

    @Override
    public void savePosition(long characterId, float x, float y) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(SAVE_POSITION)) {
            statement.setFloat(1, x);
            statement.setFloat(2, y);
            statement.setLong(3, characterId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi lưu vị trí nhân vật: characterId=" + characterId, e);
        }
    }

    @Override
    public CharacterStats loadStats(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LOAD_STATS)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new DatabaseAccessException("Nhân vật không có chỉ số: characterId=" + characterId, null);
                }
                return new CharacterStats(rs.getInt("str"), rs.getInt("agi"), rs.getInt("int_"), rs.getInt("vit"));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc chỉ số nhân vật: characterId=" + characterId, e);
        }
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
