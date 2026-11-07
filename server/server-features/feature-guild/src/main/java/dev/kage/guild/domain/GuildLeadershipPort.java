package dev.kage.guild.domain;

import java.time.Instant;

/** Nhường chức bang chủ (ADR-183). Chạm DB. */
public interface GuildLeadershipPort {

    /** Kết quả nhường chức. */
    enum Outcome {
        OK,
        NOT_IN_GUILD,
        NOT_LEADER,
        /** Người nhận không phải thành viên bang của mình. */
        TARGET_NOT_IN_GUILD,
        CANNOT_TRANSFER_TO_SELF
    }

    /**
     * Một transaction: khoá dòng bang, kiểm {@code leaderId} là bang chủ và {@code targetId} là thành viên cùng bang,
     * đổi vai hai người + {@code guild.leader_character_id}, ghi nhật ký bang {@code LEADER_CHANGED}.
     */
    Outcome transfer(long leaderId, long targetId, Instant at);
}
