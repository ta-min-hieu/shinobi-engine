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

        /**
         * Như {@link #evict(long)} nhưng nói cho client đúng LÝ DO (ADR-052: {@code error.kicked_by_gm}).
         * Mặc định = lý do "đăng nhập nơi khác".
         */
        default CompletableFuture<Void> evict(long characterId, String errorKey) {
            return evict(characterId);
        }
    }

    private static final int LOCK_STRIPES = 64;

    private final Map<Long, Session> ownerByCharacterId = new ConcurrentHashMap<>();
    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    /**
     * Mỗi tài khoản đang chơi nhân vật nào (quyết định chủ dự án 2026-10-11: một tài khoản chỉ MỘT nhân
     * vật online). Trước đó sổ chỉ biết theo nhân vật, nên một tài khoản mở hai kết nối và chọn hai nhân
     * vật khác nhau là chơi song song được cả hai — tự giao dịch, tự kéo nhau vào hầm ngục.
     */
    private final Map<Long, Long> playingCharacterByAccountId = new ConcurrentHashMap<>();

    /** Chiều ngược của {@link #playingCharacterByAccountId} — để {@link #release} tự dọn. */
    private final Map<Long, Long> accountByCharacterId = new ConcurrentHashMap<>();

    private final ReentrantLock[] accountLocks = new ReentrantLock[LOCK_STRIPES];

    CharacterSessionRegistry() {
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
            accountLocks[i] = new ReentrantLock();
        }
    }

    /** Phiên đang giữ {@code characterId}, nếu có — cho lệnh GM kick (ADR-052). */
    Optional<Session> ownerOf(long characterId) {
        return Optional.ofNullable(ownerByCharacterId.get(characterId));
    }

    /**
     * Khoá tuần tự hoá mọi lượt vào world của MỘT tài khoản. Lấy TRƯỚC {@link #lockFor} của nhân vật
     * (thứ tự cố định: tài khoản → nhân vật), chỉ giữ trên Virtual Thread. Thiếu nó thì hai kết nối
     * của cùng tài khoản chọn hai nhân vật khác nhau đúng cùng lúc đều thấy "chưa ai chơi" và cùng vào.
     */
    ReentrantLock lockForAccount(long accountId) {
        return accountLocks[Math.floorMod(Long.hashCode(accountId), LOCK_STRIPES)];
    }

    /** Nhân vật KHÁC của cùng tài khoản đang online, và phiên đang giữ nó. */
    record OtherCharacterOnline(long characterId, Session session) {}

    /**
     * Ghi {@code characterId} là nhân vật tài khoản {@code accountId} đang chơi. Gọi SAU {@link #claim}
     * của chính nhân vật đó, khi đang giữ {@link #lockForAccount}.
     *
     * @return nhân vật KHÁC của tài khoản đang được một phiên KHÁC giữ — caller phải đá nó; rỗng nếu
     *     tài khoản chưa chơi ai, đang chơi chính nhân vật này, hoặc nhân vật kia do chính
     *     {@code session} giữ (đổi nhân vật trên cùng kết nối tự dọn lấy)
     */
    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    Optional<OtherCharacterOnline> claimAccount(long accountId, long characterId, Session session) {
        accountByCharacterId.put(characterId, accountId);
        Long previous = playingCharacterByAccountId.put(accountId, characterId);
        if (previous == null || previous == characterId) {
            return Optional.empty();
        }
        Session owner = ownerByCharacterId.get(previous);
        return owner == null || owner == session
                ? Optional.empty()
                : Optional.of(new OtherCharacterOnline(previous, owner));
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
        if (ownerByCharacterId.remove(characterId, session)) {
            Long accountId = accountByCharacterId.remove(characterId);
            if (accountId != null) {
                // Có điều kiện: tài khoản đã chuyển sang nhân vật khác thì dòng đó không phải của ta.
                playingCharacterByAccountId.remove(accountId, characterId);
            }
        }
    }

    /** Có phiên nào đang giữ {@code characterId} không — chỉ để quan sát (test, chẩn đoán). */
    boolean isClaimed(long characterId) {
        return ownerByCharacterId.containsKey(characterId);
    }
}
