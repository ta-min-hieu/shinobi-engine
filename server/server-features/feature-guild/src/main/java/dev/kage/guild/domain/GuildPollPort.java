package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bình chọn trong bang (ADR-248): mỗi bang một cuộc gần nhất; tạo cuộc mới là đóng cuộc cũ. */
public interface GuildPollPort {

    /**
     * Cuộc bình chọn gần nhất của bang.
     *
     * @param votes số phiếu theo đúng thứ tự {@code options}
     * @param myVote lựa chọn của người hỏi (0-based); -1 nếu chưa bỏ phiếu
     */
    record Poll(long id, String question, List<String> options, List<Integer> votes, Instant closesAt, int myVote) {}

    /** Kết quả tạo. */
    enum CreateOutcome {
        OK,
        NOT_IN_GUILD,
        NOT_OFFICER,
        INVALID
    }

    /** Kết quả bỏ phiếu. */
    enum VoteOutcome {
        OK,
        NOT_IN_GUILD,
        NO_OPEN_POLL,
        BAD_OPTION
    }

    /** Một cuộc mới cần tạo (câu hỏi / lựa chọn đã kiểm và cắt khoảng trắng). */
    record Draft(long characterId, String question, List<String> options, Instant closesAt, Instant now) {}

    /** Bang chủ / phó tạo cuộc mới (đóng cuộc đang mở nếu có). */
    CreateOutcome create(Draft draft);

    /** Bỏ (hoặc đổi) phiếu cho lựa chọn {@code option} (0-based) của cuộc đang mở. */
    VoteOutcome vote(long characterId, int option, Instant now);

    /** Cuộc gần nhất của bang người này (mở hay đã đóng); rỗng nếu không ở bang nào hoặc bang chưa có cuộc nào. */
    Optional<Poll> latest(long characterId);
}
