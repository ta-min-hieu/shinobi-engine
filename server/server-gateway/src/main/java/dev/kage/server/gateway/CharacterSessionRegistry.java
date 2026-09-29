package dev.kage.server.gateway;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ai đang GIỮ phiên của mỗi nhân vật — thi hành quyết định chủ dự án 2026-09-25: <b>một nhân vật
 * chỉ một phiên sống; chọn nhân vật đang online từ kết nối khác thì ĐÁ PHIÊN CŨ</b> (SERVER.md §8).
 *
 * <p><b>Vì sao cần một sổ riêng.</b> {@link ConnectedCharacterRegistry} biết nhân vật đang gắn với
 * kết nối nào để GỬI gói, nhưng không biết phiên nào có quyền DỌN nhân vật đó. Trước sổ này,
 * kết nối mới chỉ ghi đè kết nối cũ trong registry; kết nối nào đóng trước thì dọn luôn nhân vật
 * của kết nối kia, và khu vực đầu đầy thì cùng một nhân vật có hai thực thể ở hai khu vực.
 *
 * <p><b>Cách dùng</b> (xem {@code GatewayChannelHandler.handleSelectCharacterBlocking}): giữ
 * {@link #lockFor} suốt từ lúc {@link #claim} tới lúc nhân vật đã vào world, nên hai lượt chọn cùng
 * một nhân vật không bao giờ đan xen — lượt sau luôn thấy lượt trước ĐÃ vào world và đá nó ra đàng
 * hoàng. Khoá chỉ bị giữ trên Virtual Thread của {@code authExecutor}, KHÔNG BAO GIỜ trên EventLoop
 * (kết nối cũ tự đóng thì dọn trên EventLoop của nó mà không cần khoá — {@link #release} là lệnh
 * xoá có điều kiện nên không đè được chủ mới).
 *
 * <p>Khoá theo DẢI ({@value #LOCK_STRIPES} khoá cố định) thay vì một khoá mỗi nhân vật: không phải
 * dọn khoá khi nhân vật thoát, và hai nhân vật khác nhau chung dải chỉ chờ nhau vài mili giây trong
 * lúc đăng nhập — không có gì trên đường tick.
 */
final class CharacterSessionRegistry {

    /** Một phiên có thể bị đá — {@code GatewayChannelHandler} của một kết nối. */
    @FunctionalInterface
    interface Session {
        /**
         * Đá phiên này khỏi {@code characterId}: báo client lý do, dọn nhân vật y như ngắt kết nối
         * thường, rồi đóng kết nối.
         *
         * @return xong khi lần dọn đã chạy trọn — vị trí lúc thoát đã được GHI, world đã xử lý hết
         *     mọi lệnh cũ của phiên này. Phiên mới chỉ được đọc vị trí và vào world sau mốc đó.
         */
        CompletableFuture<Void> evict(long characterId);
    }

    private static final int LOCK_STRIPES = 64;

    private final Map<Long, Session> ownerByCharacterId = new ConcurrentHashMap<>();
    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    CharacterSessionRegistry() {
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    /** Khoá tuần tự hoá mọi lượt chọn {@code characterId} — chỉ giữ trên Virtual Thread. */
    ReentrantLock lockFor(long characterId) {
        return locks[Math.floorMod(Long.hashCode(characterId), LOCK_STRIPES)];
    }

    // Error Prone ReferenceEquality + PMD CompareObjectsWithEquals: đúng ý — "cùng một phiên" là
    // cùng một KẾT NỐI, tức cùng đối tượng; Session không có (và không được có) equals theo giá trị.
    /**
     * Ghi {@code session} làm chủ mới của {@code characterId}.
     *
     * @return chủ CŨ nếu là một phiên KHÁC — caller phải đá nó; rỗng nếu chưa ai giữ hoặc chính
     *     {@code session} đang giữ (bấm chọn hai lần trên cùng kết nối không tự đá mình)
     */
    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    Optional<Session> claim(long characterId, Session session) {
        Session previous = ownerByCharacterId.put(characterId, session);
        return previous == null || previous == session ? Optional.empty() : Optional.of(previous);
    }

    /** Thôi giữ — CHỈ khi {@code session} vẫn là chủ; chủ mới đã {@link #claim} thì không đụng tới. */
    void release(long characterId, Session session) {
        ownerByCharacterId.remove(characterId, session);
    }
}
