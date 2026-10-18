package dev.kage.gm.domain;

import java.util.Optional;

/**
 * Đọc quyền GM của một tài khoản ({@code gm_role}, V61) — CHỈ ĐỌC (ADR-052 D8: không lệnh nào trong
 * game đổi được quyền; cấp/thu bằng SQL tay). Đọc lại MỖI lệnh, không cache (D2).
 */
public interface GmRolePort {

    /** Rỗng = người chơi thường. */
    Optional<GmRole> findRole(long accountId);
}
