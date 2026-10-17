package dev.kage.account.domain;

/**
 * Trạng thái tài khoản — cột {@code account.status} (V1, DATABASE.md §2.1). Có từ V1 vì đây là lý do
 * cốt lõi ADR-017 chọn session token (ban tức thì), nhưng tới 2026-10-17 đăng nhập chưa từng đọc nó
 * (README dòng 68(e)). Chưa có công cụ khoá tài khoản — đặt cột bằng SQL.
 */
public enum AccountStatus {
    ACTIVE,
    BANNED,
    SUSPENDED;

    /** Chỉ tài khoản đang hoạt động được đăng nhập. */
    public boolean allowsLogin() {
        return this == ACTIVE;
    }

    /**
     * Đọc giá trị cột. Giá trị lạ (gõ tay sai khi khoá bằng SQL) coi là {@link #SUSPENDED} — fail secure:
     * thà một tài khoản bị chặn nhầm còn hơn tài khoản bị khoá vẫn vào được vì sai chính tả.
     */
    public static AccountStatus fromColumn(String value) {
        for (AccountStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return SUSPENDED;
    }
}
