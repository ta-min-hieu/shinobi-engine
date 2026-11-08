package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;

/** Lịch hoạt động bang (ADR-256): bang chủ / phó hẹn giờ, cả bang được nhắc trước. */
public interface GuildEventPort {

    /** Một buổi hẹn sắp tới. */
    record Event(long id, String title, Instant startsAt) {}

    /** Buổi đến giờ nhắc: kèm bang chủ (để tra danh sách thành viên). */
    record Due(long id, long guildId, long leaderId, String title, Instant startsAt) {}

    /** Một buổi cần tạo (tiêu đề đã kiểm). */
    record Draft(long characterId, String title, Instant startsAt, Instant now) {}

    /** Kết quả tạo. */
    enum CreateOutcome {
        OK,
        NOT_IN_GUILD,
        NOT_OFFICER,
        INVALID,
        FULL
    }

    /** Kết quả huỷ. */
    enum CancelOutcome {
        OK,
        NOT_FOUND,
        NOT_OFFICER
    }

    /** Tạo nếu là sĩ quan và bang chưa đủ {@code maxUpcoming} buổi sắp tới. */
    CreateOutcome create(Draft draft, int maxUpcoming);

    /** Sĩ quan huỷ một buổi của bang mình. */
    CancelOutcome cancel(long characterId, long eventId);

    /** Các buổi chưa tới giờ của bang người này, sớm nhất trước. */
    List<Event> upcoming(long characterId, Instant now);

    /** Buổi bắt đầu trong khoảng ({@code now}, {@code until}] chưa nhắc. */
    List<Due> due(Instant now, Instant until);

    /** Đánh dấu đã nhắc. */
    void markReminded(long eventId);
}
