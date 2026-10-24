package dev.kage.friend.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.kage.friend.domain.MentorshipOutcome;
import dev.kage.friend.domain.MentorshipPolicy;
import dev.kage.friend.domain.MentorshipPort;

/**
 * Sư đồ (ADR-093). Cấp của hai người do gateway đọc và đưa vào — feature-friend không biết feature-character.
 *
 * <p>Lúc nhận đồ đệ, mọi mốc ĐÃ qua của đồ đệ được ghi là đã dùng mà không thưởng: một nhân vật Lv29 bái sư rồi lên
 * Lv30 chỉ được thưởng mốc 30, không nhặt lại mốc 10/20 đã qua trước khi có sư phụ.
 */
public final class MentorshipUseCase {

    /**
     * Thưởng một mốc vừa chạm — gateway gửi thư cho cả hai.
     *
     * @param graduated mốc này là xuất sư, quan hệ đã khép lại
     */
    public record MilestoneReward(
            long mentorId, int milestone, long discipleGold, long mentorGold, boolean graduated) {}

    private final MentorshipPort port;
    private final PendingOffers requests;

    public MentorshipUseCase(MentorshipPort port, PendingOffers requests) {
        this.port = port;
        this.requests = requests;
    }

    /** Đồ đệ xin bái sư — kiểm trước để báo lỗi ngay, lời chờ sư phụ nhận. */
    public MentorshipOutcome request(long discipleId, int discipleLevel, long mentorId, int mentorLevel) {
        if (discipleId == mentorId) {
            return MentorshipOutcome.CANNOT_TARGET_SELF;
        }
        MentorshipOutcome levels = MentorshipPolicy.checkLevels(discipleLevel, mentorLevel);
        if (levels != MentorshipOutcome.OK) {
            return levels;
        }
        if (port.mentorOf(discipleId).isPresent()) {
            return MentorshipOutcome.ALREADY_HAS_MENTOR;
        }
        if (port.disciplesOf(mentorId).size() >= MentorshipPolicy.MAX_DISCIPLES) {
            return MentorshipOutcome.MENTOR_FULL;
        }
        requests.put(mentorId, discipleId);
        return MentorshipOutcome.OK;
    }

    /** Sư phụ nhận lời — kiểm lại cấp (có thể đã đổi trong 5 phút chờ) rồi ghi quan hệ. */
    public MentorshipOutcome accept(long mentorId, int mentorLevel, long discipleId, int discipleLevel) {
        if (!requests.take(mentorId, discipleId)) {
            return MentorshipOutcome.NO_PENDING_REQUEST;
        }
        MentorshipOutcome levels = MentorshipPolicy.checkLevels(discipleLevel, mentorLevel);
        if (levels != MentorshipOutcome.OK) {
            return levels;
        }
        MentorshipOutcome bound = port.bind(mentorId, discipleId, MentorshipPolicy.MAX_DISCIPLES);
        if (bound == MentorshipOutcome.OK) {
            port.markMilestonesPassed(discipleId, mentorId, MentorshipPolicy.milestonesReachedAt(discipleLevel));
        }
        return bound;
    }

    /** Một trong hai cắt quan hệ. */
    public MentorshipOutcome leave(long characterId, long otherId) {
        return port.unbind(characterId, otherId) ? MentorshipOutcome.OK : MentorshipOutcome.NOT_RELATED;
    }

    public Optional<Long> mentorOf(long discipleId) {
        return port.mentorOf(discipleId);
    }

    public List<Long> disciplesOf(long mentorId) {
        return port.disciplesOf(mentorId);
    }

    /**
     * {@code characterId} vừa lên {@code level} — thưởng mọi mốc chưa dùng mà cấp này đã chạm; chạm cấp xuất sư thì
     * khép quan hệ. Không có sư phụ thì rỗng (đa số lần lên cấp — một câu đọc).
     */
    public List<MilestoneReward> onLevelReached(long characterId, int level) {
        Optional<Long> mentor = port.mentorOf(characterId);
        if (mentor.isEmpty()) {
            return List.of();
        }
        boolean graduated = level >= MentorshipPolicy.GRADUATION_LEVEL;
        List<MilestoneReward> rewards = new ArrayList<>();
        for (int milestone : MentorshipPolicy.milestonesReachedAt(level)) {
            if (port.claimMilestone(characterId, mentor.get(), milestone)) {
                rewards.add(new MilestoneReward(
                        mentor.get(),
                        milestone,
                        milestone * MentorshipPolicy.DISCIPLE_GOLD_PER_LEVEL,
                        milestone * MentorshipPolicy.MENTOR_GOLD_PER_LEVEL,
                        graduated && milestone == MentorshipPolicy.GRADUATION_LEVEL));
            }
        }
        if (graduated) {
            port.graduate(characterId);
        }
        return rewards;
    }
}
