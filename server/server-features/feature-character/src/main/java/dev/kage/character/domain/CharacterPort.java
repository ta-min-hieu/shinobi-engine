package dev.kage.character.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port — DB-agnostic, không SQL/PostgreSQL ở đây (Điều 23). Implementation thật ({@code
 * PostgresCharacterAdapter}) nằm ở server-database.
 */
public interface CharacterPort {

    List<Character> listByAccount(long accountId);

    int countByAccount(long accountId);

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
     * Ghi đè hệ phái (GDD §4 "chọn hệ ở Lv10", Phase 8) — 1 câu UPDATE đơn, không cần
     * {@link Transaction} (khác {@link #create}, không có ghi phụ nào trong cùng nguyên tử ở lát
     * cắt này — xem {@code SelectFactionUseCase} "Scope cut").
     */
    void updateFaction(long characterId, Faction faction);
}
