package dev.kage.friend.domain;

import java.util.List;

/**
 * Danh sách chặn (ADR-091) — hiện thực ở server-database. {@link #block} là MỘT transaction: đếm trần, ghi dòng chặn,
 * rồi xoá tình bạn / lời mời / đạo lữ / sư đồ giữa hai người (chặn ai thì không còn quan hệ gì với người đó).
 */
public interface BlockPort {

    /** Chặn {@code blockedId}; {@code maxBlocked} là trần số người chặn được. */
    BlockOutcome block(long blockerId, long blockedId, int maxBlocked);

    /** Bỏ chặn — {@link BlockOutcome#OK} hoặc {@link BlockOutcome#NOT_BLOCKED}. */
    BlockOutcome unblock(long blockerId, long blockedId);

    /** Id những người {@code blockerId} đang chặn, cũ trước. */
    List<Long> blockedBy(long blockerId);

    /** Một trong hai người đã chặn người kia. */
    boolean eitherBlocks(long firstId, long secondId);
}
