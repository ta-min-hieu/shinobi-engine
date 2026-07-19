package dev.kage.account.domain;

import java.util.Optional;

/**
 * Port cho session token (ADR-017: opaque random token + Redis, sliding TTL 24h) — DB-agnostic,
 * không biết Redis ở đây (Điều 23). Implementation thật ({@code RedisSessionAdapter}) nằm ở
 * server-database.
 */
public interface SessionPort {

    /** Phát hành token mới cho account, TTL khởi điểm. */
    String issue(long accountId);

    /**
     * Xác thực token còn hiệu lực không, đồng thời gia hạn TTL (sliding — ADR-017: mỗi request
     * xác thực thành công tự gia hạn, không cần refresh token riêng).
     */
    Optional<Long> validateAndRenew(String token);

    /** Thu hồi token ngay lập tức — ban/đăng xuất (ADR-017: lý do chính chọn session token thay JWT). */
    void revoke(String token);
}
