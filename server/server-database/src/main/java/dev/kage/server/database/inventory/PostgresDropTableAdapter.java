package dev.kage.server.database.inventory;

import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.DropTablePort;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Hiện thực {@link DropTablePort} trên PostgreSQL — đọc thuần, không random (roll ở
 * {@code LootRoller}, feature-inventory/domain — Điều 24: SQL chỉ ở đây).
 */
public final class PostgresDropTableAdapter implements DropTablePort {

    private static final String FIND_ENTRIES = """
            SELECT e.item_template_id, e.drop_chance_percent, e.min_quantity, e.max_quantity
              FROM drop_table_entry e
              JOIN drop_table dt ON dt.id = e.drop_table_id
             WHERE dt.code = ?
            """;

    private final DataSource dataSource;

    public PostgresDropTableAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<DropTableEntry> findEntries(String dropTableCode) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_ENTRIES)) {
            statement.setString(1, dropTableCode);
            try (ResultSet rs = statement.executeQuery()) {
                ArrayList<DropTableEntry> entries = new ArrayList<DropTableEntry>();
                while (rs.next()) {
                    entries.add(new DropTableEntry(
                            rs.getLong("item_template_id"), rs.getInt("drop_chance_percent"),
                            rs.getInt("min_quantity"), rs.getInt("max_quantity")));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc drop table: code=" + dropTableCode, e);
        }
    }
}
