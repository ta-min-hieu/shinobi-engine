package dev.kage.server.database.quest.fuzz;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * SQL test-only của bộ fuzz quest: tạo nhân vật, dựng sẵn trạng thái (chuỗi đã lĩnh, đồ trong túi,
 * túi đầy) và đọc thẳng các bảng để so với mô hình. Mọi câu ĐỌC ở đây cố ý đi vòng qua adapter của
 * production — so adapter với chính nó thì không bắt được gì.
 */
final class QuestFuzzDb {

    /** Một dòng đọc ra: {@code (cột 1, cột 2, cột 3)} dạng chuỗi — đủ cho mọi câu hỏi của bộ fuzz. */
    record Row(String first, String second, String third) {}

    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            SELECT ?, id, ?, 0, 0 FROM item_template WHERE code = ?
            """;

    private final DataSource dataSource;

    QuestFuzzDb(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    DataSource dataSource() {
        return dataSource;
    }

    long createAccount(String username) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        Account[] holder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, username));
        return holder[0].id();
    }

    long createCharacter(long accountId, String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    /** Đặt nhân vật ở giữa chuỗi: quest này coi như đã lĩnh từ trước (không audit, không thưởng). */
    void seedClaimed(long characterId, String questCode) {
        execute("""
                INSERT INTO character_quest_progress
                       (character_id, quest_template_id, status, progress_json, updated_at)
                SELECT ?, id, 'CLAIMED', '{}', now() FROM quest_template WHERE code = ?
                """, characterId, questCode);
    }

    void insertItem(long characterId, String itemCode, int quantity) {
        execute(INSERT_ITEM, characterId, quantity, itemCode);
    }

    void deleteItemsOf(long characterId, String itemCode) {
        execute("""
                DELETE FROM inventory_item
                 WHERE character_id = ?
                   AND item_template_id = (SELECT id FROM item_template WHERE code = ?)
                """, characterId, itemCode);
    }

    void execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chạy SQL: " + sql, e);
        }
    }

    /** Đọc tối đa ba cột đầu của mọi dòng, dạng chuỗi ({@code ""} cho cột thiếu hoặc NULL). */
    List<Row> rows(String sql, Object... params) {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                int columns = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    rows.add(new Row(column(rs, 1, columns), column(rs, 2, columns), column(rs, 3, columns)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only chạy SQL: " + sql, e);
        }
        return rows;
    }

    long single(String sql, Object... params) {
        List<Row> rows = rows(sql, params);
        if (rows.size() != 1) {
            throw new IllegalStateException("Mong đúng 1 dòng, có " + rows.size() + ": " + sql);
        }
        return Long.parseLong(rows.get(0).first());
    }

    private static String column(ResultSet rs, int index, int columns) throws SQLException {
        if (index > columns) {
            return "";
        }
        String value = rs.getString(index);
        return value == null ? "" : value;
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }
}
