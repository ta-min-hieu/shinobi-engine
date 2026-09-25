package dev.kage.server.database.inventory;

import java.sql.Connection;

import dev.kage.server.database.JdbcOperations;

/**
 * ĐIỂM TUẦN TỰ HOÁ DUY NHẤT cho túi đồ của một nhân vật: mọi transaction ghi {@code inventory_item}
 * của nhân vật X (thêm, bớt, đổi chủ, cường hoá) phải gọi {@link #acquire} cho X <b>trước mọi khoá
 * khác</b> (vàng, thư, dòng vật phẩm...). Khoá tự nhả khi transaction commit/rollback.
 *
 * <p><b>Vì sao tồn tại (2026-09-25).</b> Có sáu đường ghi túi đồ — rơi đồ/cường hoá/dùng đồ (qua
 * {@code InventoryFacade}), cửa hàng, thư, quest, giao dịch — mỗi đường "đếm ô rồi INSERT" trong
 * transaction riêng. {@code ReentrantLock} của {@code InventoryFacade} chỉ phủ các lệnh đi qua facade,
 * và chỉ trong MỘT JVM. Ở READ COMMITTED, hai đường khác nhau cùng đếm thấy 19, cùng chèn, cùng commit
 * ⇒ túi 21 ô. Đường facade còn tệ hơn: đọc túi rồi {@code save()} xoá mọi dòng không có trong bản đã
 * đọc ⇒ XOÁ THẬT món cửa hàng vừa bán (đã trừ tiền). Tái hiện tất định ở
 * {@code InventoryGrantRaceIntegrationTest}.
 *
 * <p><b>Vì sao khoá tư vấn chứ không {@code SELECT ... FROM character FOR UPDATE}.</b> Dòng
 * {@code character} bị nhiều đường KHÔNG dính túi đồ ghi (vị trí lúc ngắt kết nối, cấp độ...) — khoá
 * nó là bắt những đường đó xếp hàng sau mọi lần mua bán, và kéo chúng vào đồ thị khoá của túi đồ.
 * Khoá tư vấn chỉ những ai gọi vào đây mới thấy. Không cần đổi schema.
 *
 * <p><b>Thứ tự khoá (chống deadlock) — xem thêm DATABASE.md §5.1.</b>
 *
 * <ol>
 *   <li>Khoá túi đồ ({@link #acquire}/{@link #acquireBoth}) — LUÔN là khoá đầu tiên. Ngoại lệ duy
 *       nhất: giao dịch phải khoá dòng {@code trade_session} trước, vì chỉ đọc dòng đó mới biết hai
 *       người tham gia là ai; không đường nào giữ khoá túi đồ rồi mới xin dòng {@code trade_session},
 *       nên không tạo vòng.
 *   <li>Sau đó mới tới {@code character_stat} (vàng/EXP qua {@code GoldLedger}), dòng thư, dòng
 *       {@code inventory_item}, audit... theo thứ tự sẵn có của từng use case — thứ tự giữa chúng
 *       không còn quan trọng với nhau vì hai transaction cùng đụng túi X đã xếp hàng ở bước 1.
 *   <li>Cần khoá HAI nhân vật (giao dịch): {@link #acquireBoth}, xếp theo KHOÁ (không theo vai
 *       người mời/người nhận), để hai giao dịch ngược chiều giữa cùng một cặp không khoá chéo nhau.
 * </ol>
 *
 * <p>Gọi lại cho cùng nhân vật trong cùng transaction là an toàn (khoá tư vấn cấp transaction là
 * khoá đếm, cùng phiên xin lại thì được ngay).
 */
public final class CharacterInventoryLock {

    private static final String LOCK = "SELECT pg_advisory_xact_lock(?, ?)";

    // "Không gian tên" riêng (dạng 2 tham số int của khoá tư vấn) để không đụng khoá tư vấn khác
    // trong DB — PostgresFriendAdapter dùng 0x46524E44. Hằng số CỐ ĐỊNH: đổi trong lúc bản cũ còn
    // chạy song song (rolling deploy) là hai bản khoá hai chìa khác nhau cho cùng một túi.
    private static final int INVENTORY_LOCK_NAMESPACE = 0x494E_5654;

    private CharacterInventoryLock() {}

    /** Khoá túi đồ của {@code characterId} tới hết transaction đang mở trên {@code connection}. */
    public static void acquire(Connection connection, long characterId) {
        JdbcOperations.lock(connection, LOCK, INVENTORY_LOCK_NAMESPACE, keyOf(characterId));
    }

    /**
     * Khoá túi đồ của HAI nhân vật theo thứ tự khoá tăng dần — thứ tự tất định, không phụ thuộc
     * ai là người gọi, nên hai transaction cùng khoá một cặp không bao giờ chờ chéo nhau.
     */
    public static void acquireBoth(Connection connection, long characterIdA, long characterIdB) {
        int keyA = keyOf(characterIdA);
        int keyB = keyOf(characterIdB);
        JdbcOperations.lock(connection, LOCK, INVENTORY_LOCK_NAMESPACE, Math.min(keyA, keyB));
        if (keyA != keyB) {
            JdbcOperations.lock(connection, LOCK, INVENTORY_LOCK_NAMESPACE, Math.max(keyA, keyB));
        }
    }

    // character.id là BIGINT, tham số khoá là int: gập 64 → 32 bit. Hai nhân vật trùng khoá chỉ làm
    // chúng xếp hàng chờ nhau một chút, không bao giờ sai kết quả — và acquireBoth sắp theo KHOÁ nên
    // trùng khoá cũng không đảo thứ tự.
    private static int keyOf(long characterId) {
        return Long.hashCode(characterId);
    }
}
