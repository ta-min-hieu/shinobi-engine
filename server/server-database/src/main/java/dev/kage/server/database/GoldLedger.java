package dev.kage.server.database;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ĐIỂM DUY NHẤT trong toàn server mà cột {@code character_stat.gold} bị đổi giá trị (ADR-035
 * Decision-3, "Việc cần làm tiếp theo" #4).
 *
 * <p><b>Vì sao phải là một điểm duy nhất.</b> Hôm nay có 4 đường đổi vàng —
 * {@code PostgresGoldAdapter} (cường hoá), {@code PostgresShopGoldAdapter} (mua/bán),
 * {@code PostgresMailGoldAdapter} (nhận thư), {@code PostgresTradeGoldAdapter} (giao dịch) — mỗi
 * cái từng tự viết lại cùng một câu UPDATE. Rắc lời gọi "nhớ bắn GoldChangedEvent" vào từng chỗ là
 * cách chắc chắn sẽ sót một đường, và sót thì client hiện SỐ CŨ mà không có lỗi nào để lần ra. Bắt
 * cả 4 đi qua đây thì đường thứ 5 (nếu ai thêm) chỉ có hai lựa chọn: gọi vào đây, hoặc viết SQL
 * mới — mà viết SQL mới đụng {@code character_stat.gold} sẽ bị soi ra ngay khi review vì file này
 * là chỗ DUY NHẤT được phép chứa câu lệnh đó.
 *
 * <p><b>Vì sao chờ commit mới báo.</b> Vàng gần như luôn bị trừ trong một transaction còn có thể
 * abort SAU đó ({@code BuyItemUseCase} ném {@code ShopTransactionAbortedException} khi túi đầy →
 * rollback cả phần gold đã trừ). Báo ngay lúc UPDATE sẽ gửi cho client một số dư KHÔNG bao giờ tồn
 * tại. Nên thay đổi chỉ được "ghi tạm" theo {@link Connection} đang mở, rồi
 * {@link JdbcTransactionRunner} gọi {@link #publishCommitted}/{@link #discardPending} đúng một lần
 * ở ranh giới commit/rollback — chỗ duy nhất trong dự án biết transaction thật sự kết thúc thế nào.
 *
 * <p><b>{@code reasonKey} lấy ở đâu.</b> Mỗi adapter gọi vào đây phục vụ ĐÚNG một use-case và một
 * chiều tiền, nên khoá i18n là hằng số tại call site — không phải luồn thêm tham số qua 4 domain
 * port (và 4 bộ test của chúng) chỉ để chở một chuỗi cố định. ⚠️ Nếu một ngày một adapter phục vụ
 * NHIỀU hơn một use-case, lúc đó mới đưa {@code reasonKey} vào chữ ký port — đừng để nó báo sai lý do.
 *
 * <p>Field tĩnh + {@link #configureListener} gọi một lần lúc khởi động: cùng khuôn
 * {@link JdbcOperations#configureMetrics} (dự án không dùng DI container — ADR-009), tránh phải
 * luồn listener qua constructor của 4 adapter và mọi chỗ dựng chúng.
 */
public final class GoldLedger {

    private static final Logger LOG = LoggerFactory.getLogger(GoldLedger.class);

    // RETURNING gold: lấy số dư mới trong CHÍNH câu UPDATE. Một câu SELECT thứ hai sẽ mở cửa sổ
    // race với giao dịch song song và có thể báo về số dư của người khác vừa ghi đè.
    private static final String RECORD_ENTRY =
            "INSERT INTO gold_ledger_entry (character_id, delta, balance, reason_key) VALUES (?, ?, ?, ?)";

    private static final String DEDUCT_IF_ENOUGH = """
            UPDATE character_stat SET gold = gold - ? WHERE character_id = ? AND gold >= ? RETURNING gold
            """;

    // AND gold <= ?: tham số là MAX_GOLD - amount, tính ở Java — "gold + amount <= MAX_GOLD" viết thẳng
    // trong SQL sẽ tràn BIGINT ngay ở phép cộng với số dư sát mép.
    private static final String CREDIT_WITHIN_CAP = """
            UPDATE character_stat SET gold = gold + ? WHERE character_id = ? AND gold <= ? RETURNING gold
            """;

    private static final String HAS_GOLD_ROW = """
            SELECT 1 FROM character_stat WHERE character_id = ?
            """;

    /**
     * Trần số dư vàng của một nhân vật (quyết định chủ dự án 2026-10-11). Trước đó trần duy nhất là
     * {@code Long.MAX_VALUE} của cột, và chạm nó là lỗi 500. Chỉ {@link #credit} thi hành trần; số dư
     * đã vượt trần từ trước (không có trên DB nào hiện nay) vẫn TIÊU được bình thường.
     */
    public static final long MAX_GOLD = 9_999_999_999L;

    private static volatile GoldChangeListener listener = GoldChangeListener.NO_OP;

    // Khoá theo Connection chứ không phải ThreadLocal: JdbcTransactionRunner đã cầm sẵn đúng
    // Connection ở ranh giới commit/rollback, nên không cần giả định "một transaction = một thread"
    // (đúng hôm nay, nhưng là giả định vô hình dễ vỡ). ConcurrentHashMap vì nhiều Virtual Thread
    // chạy transaction song song, mỗi cái một Connection riêng từ pool.
    private static final Map<Connection, List<GoldChange>> PENDING =
            new ConcurrentHashMap<Connection, List<GoldChange>>();

    private GoldLedger() {}

    /**
     * Gọi 1 LẦN lúc khởi động ({@code ServerDatabaseBootstrap#configureGoldChangeListener}) — trước
     * đó mọi thay đổi vẫn ghi DB đúng, chỉ là không ai được báo.
     */
    public static void configureListener(GoldChangeListener goldChangeListener) {
        listener = goldChangeListener;
    }

    /**
     * Trừ {@code amount} gold NẾU đủ số dư — một câu UPDATE có điều kiện
     * ({@code WHERE gold >= amount}), tránh race đọc-rồi-ghi giữa hai request đồng thời.
     *
     * @return {@code true} nếu trừ được (đủ gold); {@code false} thì KHÔNG đổi gì và KHÔNG ghi tạm
     *     thay đổi nào — "không đủ tiền" không phải một lần vàng đổi.
     * @throws IllegalArgumentException nếu {@code amount} âm — xem {@link #requireNonNegative}
     */
    public static boolean deduct(Connection connection, long characterId, long amount, String reasonKey) {
        requireNonNegative(amount);
        return JdbcOperations.queryOne(
                        connection, DEDUCT_IF_ENOUGH, rs -> rs.getLong("gold"), amount, characterId, amount)
                .map(newBalance -> {
                    stage(connection, new GoldChange(characterId, newBalance, -amount, reasonKey));
                    return Boolean.TRUE;
                })
                .orElse(Boolean.FALSE);
    }

    /**
     * Cộng {@code amount} gold, không vượt {@link #MAX_GOLD}.
     *
     * @throws GoldCapExceededException nếu số dư sau khi cộng sẽ vượt trần — KHÔNG đổi gì; người gọi
     *     đang ở giữa một transaction và exception này làm nó rollback trọn
     * @throws IllegalArgumentException nếu {@code amount} âm — xem {@link #requireNonNegative}
     */
    public static void credit(Connection connection, long characterId, long amount, String reasonKey) {
        requireNonNegative(amount);
        // amount > MAX_GOLD ⇒ ngưỡng âm ⇒ không dòng nào khớp ⇒ đi xuống nhánh vượt trần, đúng ý.
        long highestBalanceThatStillFits = MAX_GOLD - amount;
        Optional<Long> newBalance = JdbcOperations.queryOne(
                connection,
                CREDIT_WITHIN_CAP,
                rs -> rs.getLong("gold"),
                amount,
                characterId,
                highestBalanceThatStillFits);
        if (newBalance.isPresent()) {
            stage(connection, new GoldChange(characterId, newBalance.get(), amount, reasonKey));
            return;
        }
        // Không dòng nào đổi: hoặc vượt trần, hoặc nhân vật không có dòng character_stat (giữ hành vi cũ
        // của trường hợp sau: không làm gì).
        if (JdbcOperations.queryOne(connection, HAS_GOLD_ROW, rs -> rs.getInt(1), characterId)
                .isPresent()) {
            throw new GoldCapExceededException(characterId);
        }
    }

    /**
     * Transaction đã commit — giao thay đổi cho listener. Gọi từ {@link JdbcTransactionRunner} cho
     * MỌI transaction (kể cả transaction không đụng vàng: lúc đó chỉ là một lần tra map trả về rỗng).
     */
    static void publishCommitted(Connection connection) {
        List<GoldChange> changes = PENDING.remove(connection);
        if (changes == null) {
            return;
        }
        for (GoldChange change : changes) {
            notifyQuietly(change);
        }
    }

    /** Transaction đã rollback — vứt thay đổi đi, KHÔNG báo cho ai (số dư đó chưa từng tồn tại). */
    static void discardPending(Connection connection) {
        PENDING.remove(connection);
    }

    /**
     * Số âm đảo chiều tiền: {@code deduct(-100)} là CỘNG 100 vàng mà bỏ qua trần, {@code credit(-100)}
     * là trừ mà bỏ qua điều kiện đủ tiền. Không đường hợp lệ nào gửi số âm (chủ dự án chặn 2026-10-14,
     * README dòng 25(iv)); ném để transaction đang mở rollback trọn.
     */
    private static void requireNonNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Số vàng âm: " + amount);
        }
    }

    private static void stage(Connection connection, GoldChange change) {
        // ADR-169 — sổ vàng: ghi cùng transaction với lần đổi (rollback thì dòng sổ cũng mất).
        JdbcOperations.update(
                connection, RECORD_ENTRY, change.characterId(), change.delta(), change.gold(), change.reasonKey());
        PENDING.computeIfAbsent(connection, key -> new ArrayList<GoldChange>()).add(change);
    }

    // PMD AvoidCatchingGenericException: hiệu ứng thật ĐÃ commit rồi. Listener hỏng (client vừa
    // ngắt kết nối, encode lỗi...) không được phép làm caller tưởng giao dịch thất bại và retry —
    // hậu quả sẽ là trừ tiền hai lần. Ghi log để lần ra, rồi đi tiếp.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void notifyQuietly(GoldChange change) {
        try {
            listener.onGoldChanged(change);
        } catch (RuntimeException e) {
            LOG.error(
                    "Báo GoldChangedEvent thất bại (vàng ĐÃ đổi thật trong DB): characterId={}",
                    change.characterId(),
                    e);
        }
    }
}
