package dev.kage.friend.domain;

import java.util.List;
import java.util.Optional;

/**
 * Sư đồ (ADR-093) — hiện thực ở server-database. Một đồ đệ có tối đa một sư phụ ({@code mentorship.disciple_id} là
 * khoá chính); mỗi mốc cấp của một đồ đệ chỉ được thưởng MỘT lần trong đời nhân vật ({@code mentorship_milestone}),
 * kể cả khi đổi sư phụ hay chuyển thế — chống nuôi đồ đệ để cày thưởng.
 */
public interface MentorshipPort {

    /** Sư phụ hiện tại của {@code discipleId}. */
    Optional<Long> mentorOf(long discipleId);

    /** Đồ đệ hiện tại của {@code mentorId}, cũ trước. */
    List<Long> disciplesOf(long mentorId);

    /**
     * Ghi quan hệ trong MỘT transaction (khoá theo sư phụ để hai lời nhận song song không vượt trần):
     * {@link MentorshipOutcome#ALREADY_HAS_MENTOR} / {@link MentorshipOutcome#MENTOR_FULL} /
     * {@link MentorshipOutcome#OK}.
     */
    MentorshipOutcome bind(long mentorId, long discipleId, int maxDisciples);

    /** Cắt quan hệ nếu {@code firstId}/{@code secondId} là sư đồ (chiều nào cũng được). */
    boolean unbind(long firstId, long secondId);

    /**
     * Ghi mốc {@code milestone} của {@code discipleId} cho sư phụ {@code mentorId} nếu CHƯA từng ghi.
     *
     * @return {@code true} nếu lần này mới ghi (được thưởng); {@code false} nếu mốc đã thưởng trước đó
     */
    boolean claimMilestone(long discipleId, long mentorId, int milestone);

    /** Ghi các mốc ĐÃ qua lúc nhận đồ đệ là đã dùng, KHÔNG thưởng (bỏ qua mốc đã ghi). */
    void markMilestonesPassed(long discipleId, long mentorId, List<Integer> milestones);

    /** Đồ đệ xuất sư — xoá quan hệ của {@code discipleId} (không có thì thôi). */
    void graduate(long discipleId);
}
