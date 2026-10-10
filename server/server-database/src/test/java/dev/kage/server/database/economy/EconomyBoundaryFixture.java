package dev.kage.server.database.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.mail.api.MailFacade;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.trade.api.TradeFacade;

/**
 * Dựng và ĐỌC trạng thái cho {@link EconomyArithmeticBoundaryIntegrationTest}: nhân vật mới, vàng,
 * dòng túi đồ, giá cửa hàng — toàn SQL test-only đọc thẳng bảng, để bài kiểm đo GIÁ TRỊ CUỐI chứ
 * không tin lời use case.
 */
final class EconomyBoundaryFixture {

    private static final String INSERT_ROW = "INSERT INTO inventory_item"
            + " (character_id, item_template_id, quantity, enhance_level, slot_index)"
            + " VALUES (?, ?, ?, 0, (SELECT COUNT(*) FROM inventory_item WHERE character_id = ?)) RETURNING id";

    private final DataSource dataSource;
    private final long accountId;
    private final AtomicInteger characterCounter = new AtomicInteger();

    EconomyBoundaryFixture(DataSource dataSource) {
        this.dataSource = dataSource;
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        Account[] holder = new Account[1];
        new JdbcAccountTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, "econboundary"));
        this.accountId = holder[0].id();
    }

    /** Nhân vật mới, túi rỗng, {@code gold} vàng. */
    long newCharacter(long gold) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        NewCharacter newCharacter = new NewCharacter(
                accountId,
                "Bnd" + characterCounter.incrementAndGet(),
                Faction.HOA,
                UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        new JdbcCharacterTransactionRunner(dataSource)
                .inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        long characterId = holder[0].id();
        setGold(characterId, gold);
        return characterId;
    }

    void setGold(long characterId, long gold) {
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, characterId);
    }

    long gold(long characterId) {
        return single("SELECT gold FROM character_stat WHERE character_id = ?", characterId);
    }

    /** Chèn thẳng MỘT dòng túi (cấp cường hoá 0) — trả {@code inventory_item.id}. */
    long addRow(long characterId, long templateId, int quantity) {
        return single(INSERT_ROW, characterId, templateId, quantity, characterId);
    }

    /** {@code count} dòng, mỗi dòng {@code quantity} món. */
    void addRows(long characterId, long templateId, int quantity, int count) {
        for (int i = 0; i < count; i++) {
            addRow(characterId, templateId, quantity);
        }
    }

    /** Số lượng từng dòng của một loại, theo id tăng dần. */
    List<Integer> rowQuantities(long characterId, long templateId) {
        return JdbcOperations.query(
                dataSource,
                "SELECT quantity FROM inventory_item WHERE character_id = ? AND item_template_id = ? ORDER BY id",
                rs -> rs.getInt(1),
                characterId,
                templateId);
    }

    long totalQuantity(long characterId, long templateId) {
        return single(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item"
                        + " WHERE character_id = ? AND item_template_id = ?",
                characterId,
                templateId);
    }

    int rowCount(long characterId) {
        return (int) single("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?", characterId);
    }

    int enhanceLevel(long itemId) {
        return (int) single("SELECT enhance_level FROM inventory_item WHERE id = ?", itemId);
    }

    long templateId(String code) {
        return single("SELECT id FROM item_template WHERE code = ?", code);
    }

    int maxStack(long templateId) {
        return (int) single("SELECT max_stack FROM item_template WHERE id = ?", templateId);
    }

    long buyPrice(long templateId) {
        return single("SELECT buy_price FROM shop_item WHERE item_template_id = ?", templateId);
    }

    long sellPrice(long templateId) {
        return single("SELECT sell_price FROM shop_item WHERE item_template_id = ?", templateId);
    }

    long mailCount() {
        return single("SELECT COUNT(*) FROM mail");
    }

    /** Ảnh chụp mọi thứ một thao tác bị TỪ CHỐI không được đổi: vàng + từng dòng túi của nhân vật. */
    String snapshot(long characterId) {
        List<String> rows = JdbcOperations.query(
                dataSource,
                "SELECT id, item_template_id, quantity, enhance_level FROM inventory_item"
                        + " WHERE character_id = ? ORDER BY id",
                rs -> rs.getLong(1) + ":" + rs.getLong(2) + "x" + rs.getInt(3) + "+" + rs.getInt(4),
                characterId);
        return "gold=" + gold(characterId) + " rows=" + rows;
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }

    static List<MailFacade.AttachmentRequest> attachments(long templateId, int quantityEach, int count) {
        List<MailFacade.AttachmentRequest> attachments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            attachments.add(new MailFacade.AttachmentRequest(templateId, quantityEach));
        }
        return attachments;
    }

    /** Mở một phiên giao dịch đã được chấp nhận giữa {@code requester} và {@code target}. */
    static long openTrade(TradeFacade trade, long requester, long target) {
        TradeFacade.RequestResult requested = trade.requestTrade(requester, target);
        if (requested.outcome() != TradeFacade.RequestOutcome.OK) {
            throw new IllegalStateException("Không mở được phiên giao dịch: " + requested.outcome());
        }
        long sessionId = requested.sessionId();
        if (trade.acceptRequest(sessionId, target) != TradeFacade.AcceptResult.OK) {
            throw new IllegalStateException("Không chấp nhận được phiên giao dịch " + sessionId);
        }
        return sessionId;
    }

    private long single(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow(() -> new IllegalStateException("Không có dòng nào: " + sql));
    }
}
