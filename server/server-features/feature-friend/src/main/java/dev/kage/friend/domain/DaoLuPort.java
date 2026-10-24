package dev.kage.friend.domain;

import java.util.Optional;

/**
 * Đạo lữ (ADR-094) — hiện thực ở server-database. Mỗi cặp là HAI dòng {@code dao_lu} (mỗi chiều một dòng, khoá chính
 * là người) nên "mỗi người tối đa một đạo lữ" do chính khoá chính giữ.
 */
public interface DaoLuPort {

    /** Đạo lữ hiện tại. */
    Optional<Long> partnerOf(long characterId);

    /**
     * Kết đạo lữ trong MỘT transaction: khoá dòng {@code friendship} của cặp → kiểm thân mật ≥ {@code minIntimacy}
     * → trừ {@code cost} linh thạch của {@code proposerId} qua GoldLedger → ghi hai dòng → audit.
     */
    DaoLuOutcome bind(long proposerId, long accepterId, int minIntimacy, long cost);

    /** Giải đạo lữ của {@code characterId}; trả người kia nếu có. */
    Optional<Long> dissolve(long characterId);

    /** Thân mật của cặp bạn (0 nếu không phải bạn) — để báo lỗi sớm lúc cầu. */
    int intimacyBetween(long firstId, long secondId);
}
