package dev.kage.server.database;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final String DEDUCT_IF_ENOUGH = """
            UPDATE character_stat SET gold = gold - ? WHERE character_id = ? AND gold >= ? RETURNING gold
            """;

    private static final String CREDIT = """
            UPDATE character_stat SET gold = gold + ? WHERE character_id = ? RETURNING gold
            """;

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
     */
    public static boolean deduct(Connection connection, long characterId, long amount, String reasonKey) {
        return JdbcOperations.queryOne(
                        connection, DEDUCT_IF_ENOUGH, rs -> rs.getLong("gold"), amount, characterId, amount)
                .map(newBalance -> {
                    stage(connection, new GoldChange(characterId, newBalance, -amount, reasonKey));
                    return Boolean.TRUE;
                })
                .orElse(Boolean.FALSE);
    }

    /** Cộng {@code amount} gold — luôn thành công (không có trần số dư ở lát cắt này). */
    public static void credit(Connection connection, long characterId, long amount, String reasonKey) {
        JdbcOperations.queryOne(connection, CREDIT, rs -> rs.getLong("gold"), amount, characterId)
                .ifPresent(newBalance -> stage(connection, new GoldChange(characterId, newBalance, amount, reasonKey)));
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

    private static void stage(Connection connection, GoldChange change) {
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
