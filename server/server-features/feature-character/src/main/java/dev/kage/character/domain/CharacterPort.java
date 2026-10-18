package dev.kage.character.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port — DB-agnostic, không SQL/PostgreSQL ở đây (Điều 23). Implementation thật ({@code
 * PostgresCharacterAdapter}) nằm ở server-database.
 */
public interface CharacterPort {

    List<Character> listByAccount(long accountId);

    /** Đếm KHÔNG khoá — chỉ để hiển thị/kiểm sớm. Để THI HÀNH giới hạn, dùng {@link #lockAccountAndCountCharacters}. */
    int countByAccount(long accountId);

    /**
     * Khoá account trong transaction đang mở rồi mới đếm số nhân vật của nó — phép đếm dùng để thi
     * hành giới hạn nhân vật/account.
     *
     * <p>Khoá giữ đến hết transaction, nên mọi lượt tạo nhân vật khác trên CÙNG account phải chờ
     * lượt này commit/rollback rồi mới đếm được: "đếm rồi INSERT" trở thành nguyên tử theo account.
     * Không có khoá này, nhiều lượt tạo đồng thời cùng đọc "3 &lt; 4" và cùng INSERT (đo thật
     * 2026-09-25: 8 lượt song song trên account có 3 nhân vật ⇒ 11 nhân vật).
     *
     * <p>Phải là thao tác ĐẦU TIÊN có khoá trong transaction tạo nhân vật (thứ tự khoá: account
     * trước, rồi mới tới các dòng INSERT) — xem {@code PostgresCharacterAdapter}.
     *
     * @return số nhân vật hiện có của account, đọc SAU khi đã giữ khoá
     */
    int lockAccountAndCountCharacters(Transaction tx, long accountId);

    /** Tên đã trùng (bất kỳ account nào — UNIQUE toàn server, DATABASE.md §2.1) → Optional rỗng. */
    boolean existsByName(String name);

    /**
     * Tra nhân vật theo tên — dùng cho kết bạn/gửi thư theo tên (feature-friend/feature-mail
     * không được đọc bảng {@code character} trực tiếp, server-gateway gọi qua {@code
     * CharacterFacade} để đổi tên thành {@code characterId} trước khi gọi feature khác).
     */
    Optional<Character> findByName(String name);

    /**
     * Tra nhân vật theo ID — dùng để hiển thị TÊN khi gateway chỉ có {@code characterId} (danh
     * sách bạn bè, người gửi thư/chat riêng...). Chiều ngược lại {@link #findByName}.
     */
    Optional<Character> findById(long characterId);

    /**
     * MỌI nhân vật theo thứ hạng cảnh giới (ADR-057): cấp giảm dần, EXP giảm dần, id tăng dần (người
     * tới trước giữ hạng khi bằng nhau). Mặc định rỗng cho fake không cần bảng xếp hạng.
     */
    default List<Character> listAllByRealm() {
        return List.of();
    }

    /**
     * Tạo nhân vật mới + ghi {@code idempotencyKey} trong CÙNG transaction (ADR-025) — key đã dùng
     * trước đó phải khiến adapter ném lỗi (unique constraint), KHÔNG tạo nhân vật lần 2.
     */
    Character create(Transaction tx, NewCharacter newCharacter);

    /** Vị trí đã lưu lần cuối (SERVER.md §8) — điểm spawn khi chọn nhân vật vào world. */
    PositionSnapshot loadPosition(long characterId);

    /** Ghi đè vị trí — gọi ĐỊNH KỲ + lúc logout (SERVER.md §8), KHÔNG mỗi tick. */
    void savePosition(long characterId, float x, float y);

    /** Chỉ số nền — server-world derive thành ATK/DEF/... lúc attach vào world (GDD §5). */
    CharacterStats loadStats(long characterId);

    /**
     * Số dư vàng hiện tại ({@code character_stat.gold}, V7) — ĐỌC THUẦN, dùng để phát
     * {@code GoldChangedEvent} đầu tiên ngay lúc vào world (ADR-035 Decision-3).
     *
     * <p>KHÔNG phải đường ĐỔI vàng: mọi thay đổi đi qua {@code GoldPort}/{@code TradeGoldPort} của
     * feature tương ứng, và đi qua đúng MỘT chỗ ở hạ tầng ({@code GoldLedger} trong server-database)
     * để không đường nào phát thiếu sự kiện.
     */
    long loadGold(long characterId);

    /**
     * Cộng {@code amount} EXP và trả về trạng thái SAU khi cộng — một câu {@code UPDATE ...
     * RETURNING} duy nhất để không có cửa sổ đọc-rồi-ghi (hai nguồn EXP chạy song song trên Virtual
     * Thread khác nhau: giết quái và trao thưởng quest, xem {@code ExpPort}).
     *
     * <p>Trả về TỔNG EXP mới, không phải cấp: quy đổi ra cấp là việc của {@link ExperienceCurve}
     * (luật chơi, không phải hạ tầng) — adapter DB không được biết đường cong.
     */
    long addExperience(long characterId, long amount);

    /**
     * Ghi lại cấp đã tính từ tổng EXP — gọi CHỈ KHI cấp thật sự đổi (xem {@code GrantExperienceUseCase}).
     *
     * <p>Chỉ ghi nếu {@code exp} trong DB VẪN LÀ {@code derivedFromExp} (compare-and-set). Hai lần
     * cộng EXP chạy song song (giết 2 quái cùng một tick, mỗi lượt một Virtual Thread) có thể về đích
     * NGƯỢC thứ tự: lần cộng trước suy ra cấp thấp hơn nhưng ghi SAU, kéo cột {@code level} lùi lại.
     * Với điều kiện này, lần ghi dựa trên EXP đã cũ bị bỏ qua, còn lần cộng mới nhất luôn khớp và
     * luôn ghi được.
     *
     * <p>Và chỉ ghi nếu cột {@code level} đang KHÁC {@code level} (sửa 2026-10-10): giá trị trả về
     * là thứ {@code GrantExperienceUseCase} dùng để quyết định ai báo "lên cấp" cho client. Hai lượt
     * song song cùng suy ra một cấp thì đúng MỘT lượt đổi được cột, và chỉ lượt đó nhận {@code true}.
     *
     * @return {@code true} nếu cột {@code level} THẬT SỰ đổi bởi lần gọi này
     */
    boolean saveLevel(long characterId, int level, long derivedFromExp);

    /**
     * Ghi đè hệ phái (GDD §4 "chọn hệ ở Lv10", Phase 8) — 1 câu UPDATE đơn, không cần
     * {@link Transaction} (khác {@link #create}, không có ghi phụ nào trong cùng nguyên tử ở lát
     * cắt này — xem {@code SelectFactionUseCase} "Scope cut").
     */
    void updateFaction(long characterId, Faction faction);
}
