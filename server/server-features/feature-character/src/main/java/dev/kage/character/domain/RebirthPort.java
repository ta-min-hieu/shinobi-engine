package dev.kage.character.domain;

import java.util.Optional;

/** Độ Kiếp Chuyển Thế (ADR-089) — hiện thực ở server-database (cột {@code character.rebirth_count}, có từ V2). */
public interface RebirthPort {

    /**
     * Cấp + số lần chuyển thế của một nhân vật.
     *
     * @param level cấp hiện tại
     * @param rebirthCount số lần đã chuyển thế
     */
    record Progress(int level, int rebirthCount) {}

    /** Khoá dòng nhân vật tới hết giao dịch rồi đọc; rỗng nếu không có nhân vật. */
    Optional<Progress> lock(Transaction tx, long characterId);

    /** Đưa về cấp 1, EXP 0, tăng số lần chuyển thế — chỉ sau {@link #lock} trong cùng giao dịch. */
    void rebirth(Transaction tx, long characterId);

    /** Số lần đã chuyển thế (0 nếu không có nhân vật). Đọc không khoá. */
    int rebirthCountOf(long characterId);
}
