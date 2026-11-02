package dev.kage.character.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Đổi tên bằng Dịch Danh Phù (ADR-148, V120) — hiện thực ở server-database. MỘT giao dịch mở đầu bằng khoá túi của
 * nhân vật (thứ tự khoá chung): kiểm hồi chiêu, tiêu một phù, đổi tên (UNIQUE {@code lower(name)} của V54 chặn trùng),
 * ghi lịch sử tên + audit. Bất kỳ bước nào từ chối thì cả giao dịch rollback — không mất phù.
 */
public interface RenamePort {

    /** Kết quả đổi tên. */
    enum Outcome {
        OK,
        INVALID_NAME,
        SAME_NAME,
        NAME_TAKEN,
        NO_TALISMAN,
        ON_COOLDOWN,
        NOT_FOUND
    }

    /**
     * Kết quả một lệnh đổi tên.
     *
     * @param name tên SAU lệnh (tên mới nếu {@code OK}, tên cũ nếu bị từ chối; rỗng nếu {@code NOT_FOUND})
     * @param nextAllowedAt lúc được đổi lần sau — có khi {@code OK} hoặc {@code ON_COOLDOWN}
     */
    record Result(Outcome outcome, String name, Optional<Instant> nextAllowedAt) {}

    /** Đổi tên — {@code newName} đã chuẩn hoá NFC và qua {@link Character#validateNewName}. */
    Result rename(long characterId, String newName, Instant now);
}
