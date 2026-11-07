package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildItemDonationPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.TradeOfferedQuantity;

/**
 * Hiện thực {@link GuildItemDonationPort} (ADR-207, V161). Cùng điều kiện "rời túi được" với bán ở Tàng Bảo Các
 * (ADR-170): chưa cường hoá, không khoá, không khảm / tẩy luyện / linh thú, không đang mặc, không đang chào giao dịch —
 * và phải có giá thu ({@code shop_item.sell_price > 0}), vì giá thu là giá trị quy đổi ra cống hiến. Thứ tự khoá: túi
 * đồ → chồng vật phẩm → dòng bang ({@link PostgresGuildProgressionAdapter#addGuildExp}).
 */
public final class PostgresGuildItemDonationAdapter implements GuildItemDonationPort {

    private static final String GUILD_OF = "SELECT guild_id FROM guild_member WHERE character_id = ?";
    private static final String LEVEL_OF = "SELECT level FROM guild WHERE id = ? FOR UPDATE";

    private static final String DONATABLE_ROW = "SELECT ii.quantity, s.sell_price, it.code"
            + " FROM inventory_item ii"
            + " JOIN item_template it ON it.id = ii.item_template_id"
            + " JOIN shop_item s ON s.item_template_id = it.id"
            + " WHERE ii.id = ? AND ii.character_id = ? AND s.sell_price > 0 AND ii.enhance_level = 0"
            + " AND NOT ii.locked"
            + " AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM item_socket x WHERE x.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM item_refinement r WHERE r.inventory_item_id = ii.id)"
            + " AND NOT EXISTS (SELECT 1 FROM pet_state p WHERE p.inventory_item_id = ii.id)"
            + " AND " + TradeOfferedQuantity.OF_II + " = 0"
            + " FOR UPDATE OF ii";

    private static final String DELETE = "DELETE FROM inventory_item WHERE id = ?";
    private static final String DECREMENT = "UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?";

    private static final String ADD_CONTRIBUTION =
            "UPDATE guild_member SET contribution = contribution + ? WHERE character_id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Chồng được chọn: số đang có, giá thu mỗi cái, mã mẫu. */
    private record Row(int quantity, long priceEach, String code) {}

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildItemDonationAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Donation donate(long characterId, long inventoryItemId, int quantity) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> donateIn(connection, characterId, inventoryItemId, quantity));
    }

    private static Donation donateIn(Connection connection, long characterId, long inventoryItemId, int quantity) {
        Optional<Long> guild = JdbcOperations.queryOne(connection, GUILD_OF, rs -> rs.getLong(1), characterId);
        if (guild.isEmpty()) {
            return Donation.refused(Outcome.NOT_IN_GUILD);
        }
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<Row> row = JdbcOperations.queryOne(
                connection,
                DONATABLE_ROW,
                rs -> new Row(rs.getInt("quantity"), rs.getLong("sell_price"), rs.getString("code")),
                inventoryItemId,
                characterId);
        if (row.isEmpty()) {
            return Donation.refused(Outcome.NOT_DONATABLE);
        }
        if (row.get().quantity() < quantity) {
            return Donation.refused(Outcome.INSUFFICIENT_QUANTITY);
        }
        if (row.get().quantity() == quantity) {
            JdbcOperations.update(connection, DELETE, inventoryItemId);
        } else {
            JdbcOperations.update(connection, DECREMENT, quantity, inventoryItemId);
        }
        long guildId = guild.get();
        long value = row.get().priceEach() * quantity;
        int levelBefore = JdbcOperations.queryOne(connection, LEVEL_OF, rs -> rs.getInt(1), guildId)
                .orElse(1);
        int levelAfter = PostgresGuildProgressionAdapter.addGuildExp(connection, guildId, value);
        JdbcOperations.update(connection, ADD_CONTRIBUTION, value, characterId);
        record(
                connection,
                new GuildLog.Entry(
                        guildId,
                        GuildLog.Kind.ITEM_DONATED,
                        characterId,
                        0L,
                        quantity,
                        row.get().code()),
                value,
                levelAfter > levelBefore ? levelAfter : 0);
        return new Donation(Outcome.OK, value, levelAfter > levelBefore, guildId);
    }

    /** Audit + nhật ký bang của một lần góp ({@code donated}), và dòng "bang lên cấp" nếu {@code newLevel} > 0. */
    private static void record(Connection connection, GuildLog.Entry donated, long value, int newLevel) {
        Instant now = Instant.now();
        JdbcOperations.update(
                connection,
                AUDIT,
                donated.actorId(),
                "GUILD_ITEM_DONATION",
                "{\"guildId\":" + donated.guildId() + ",\"item\":\"" + donated.detail() + "\",\"quantity\":"
                        + donated.amount() + ",\"value\":" + value + "}",
                Timestamp.from(now));
        GuildLog.append(connection, donated, now);
        if (newLevel > 0) {
            GuildLog.append(
                    connection,
                    new GuildLog.Entry(donated.guildId(), GuildLog.Kind.LEVEL_UP, donated.actorId(), 0L, newLevel, ""),
                    now);
        }
    }
}
