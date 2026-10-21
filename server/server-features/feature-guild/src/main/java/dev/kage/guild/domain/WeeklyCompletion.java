package dev.kage.guild.domain;

import java.util.List;

/**
 * Nhiệm vụ tuần vừa xong — trả về ĐÚNG MỘT lần mỗi bang mỗi tuần (ADR-069 D2).
 *
 * @param memberIds thành viên lúc chốt — người nhận thư thưởng
 * @param leveledUp kinh nghiệm thưởng làm bang lên cấp
 */
public record WeeklyCompletion(long guildId, List<Long> memberIds, boolean leveledUp) {

    public WeeklyCompletion {
        memberIds = List.copyOf(memberIds);
    }
}
