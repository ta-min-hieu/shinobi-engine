package dev.kage.quest.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Truy nã (ADR-101) — hiện thực ở server-database. Mỗi lệnh ghi là MỘT giao dịch khoá dòng {@code character} của người
 * chơi trước khi đếm (một truy nã đang làm, trần lần xong mỗi ngày).
 */
public interface BountyPort {

    /** Kết quả nhận truy nã. */
    enum AcceptOutcome {
        OK,
        NOT_ON_BOARD,
        LEVEL_TOO_LOW,
        ALREADY_ACTIVE,
        DAILY_LIMIT_REACHED
    }

    /**
     * Truy nã đang làm.
     *
     * @param progress số đã hạ
     */
    record Active(String code, String monsterCode, int progress, int target) {}

    /**
     * Một lần hạ vừa ghi.
     *
     * @param completed vừa đủ số — đã chuyển COMPLETED và đã cộng thưởng (vàng + EXP) trong cùng giao dịch
     */
    record Progress(String code, int progress, int target, boolean completed, long rewardGold, long rewardExp) {}

    List<BountyTemplate> templates();

    Optional<Active> active(long characterId);

    int completedOn(long characterId, LocalDate day);

    /** Nhận {@code template} trong ngày {@code day}. Cấp/bảng đã được use case kiểm. */
    AcceptOutcome accept(long characterId, LocalDate day, BountyTemplate template, int maxCompletionsPerDay);

    /** Bỏ truy nã đang làm (không mất gì, không tính lần xong). */
    boolean abandon(long characterId);

    /** Hạ một con {@code monsterCode}; rỗng nếu không có truy nã đang làm khớp loài. */
    Optional<Progress> onKill(long characterId, String monsterCode);
}
