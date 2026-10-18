package dev.kage.checkin.domain;

import java.util.UUID;

/**
 * Trao quà một ngày điểm danh — gateway hiện thực bằng THƯ HỆ THỐNG (ADR-060 D3). Feature này không
 * biết feature thư tồn tại (Điều 23).
 */
@FunctionalInterface
public interface CheckInRewardSender {

    /**
     * Gửi quà. Gọi lại với CÙNG {@code idempotencyKey} không bao giờ gửi lần hai.
     *
     * @return kết quả; {@link Delivery#delivered()} {@code true} cả khi khoá đã dùng (quà đã gửi ở lượt trước)
     */
    Delivery send(long characterId, CheckInReward reward, UUID idempotencyKey);

    /**
     * Kết quả gửi quà.
     *
     * @param delivered quà đã nằm trong hộp thư (lượt này hoặc lượt trước)
     * @param mailId id thư vừa tạo ở LƯỢT NÀY; 0 nếu thư do lượt trước tạo hoặc không gửi được
     */
    record Delivery(boolean delivered, long mailId) {
        public static final Delivery FAILED = new Delivery(false, 0L);
    }
}
