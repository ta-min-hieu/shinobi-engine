package dev.kage.guild.domain;

import java.time.Instant;
import java.util.Optional;

/** Buff kinh nghiệm toàn bang (ADR-239): bang chủ tiêu kinh nghiệm bang để bật +EXP cho mọi thành viên. */
public interface GuildBuffPort {

    /** Kết quả bật buff. */
    enum Outcome {
        OK,
        NOT_IN_GUILD,
        NOT_LEADER,
        ALREADY_ACTIVE,
        /** Trừ xong sẽ tụt dưới mốc cấp hiện tại — không cho tụt cấp vì buff. */
        NOT_ENOUGH_EXP
    }

    /** Kết quả + hạn buff ({@link Instant#EPOCH} nếu chưa có) + kinh nghiệm bang còn lại + id bang (0 nếu không có). */
    record Activation(Outcome outcome, Instant until, long guildExpLeft, long guildId) {}

    /** Trong MỘT giao dịch: khoá bang, kiểm, trừ {@code cost} kinh nghiệm bang, đặt hạn {@code until}. */
    Activation activate(long leaderId, long cost, Instant until, Instant now);

    /** Hạn buff của bang người này; rỗng nếu không ở bang nào hoặc bang chưa từng bật. */
    Optional<Instant> buffUntilOf(long characterId);
}
