package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Tranh đoạt Linh Mạch (ADR-090) — hiện thực ở server-database (V84). */
public interface SpiritVeinPort {

    /** Điểm của một bang trong một khung; danh sách xếp theo điểm giảm dần, hoà thì ai đạt trước đứng trên. */
    record Score(long guildId, String guildName, int points) {}

    /** Bang đang giữ danh hiệu Chủ Linh Mạch tới {@code heldUntil}. */
    record Holder(long guildId, String guildName, Instant heldUntil) {}

    /** Một thư thưởng còn chờ gửi — ghi trong giao dịch chốt khung, xoá khi thư đã nằm trong hộp. */
    record Payout(String veinCode, Instant windowStart, long characterId, String guildName) {}

    /** Mọi Linh Mạch. */
    List<SpiritVein> veins();

    /** Bang đang giữ Linh Mạch tại {@code now}; rỗng nếu chưa ai giữ hoặc đã hết hạn. */
    Optional<Holder> holderOf(String veinCode, Instant now);

    /** Cộng 1 điểm cho bang trong khung bắt đầu ở {@code windowStart}. */
    void addPoint(String veinCode, Instant windowStart, long guildId, Instant at);

    /** Bảng điểm một khung. */
    List<Score> scores(String veinCode, Instant windowStart);

    /**
     * Chốt khung — nguyên tử và chỉ MỘT lần: bang đầu bảng (nếu có) giữ Linh Mạch tới {@code heldUntil}, thành viên
     * của bang được xếp vào hàng đợi thưởng.
     *
     * @return bang thắng nếu lần gọi NÀY là lần chốt và khung có người ghi điểm; rỗng nếu đã chốt rồi hoặc không ai
     *     ghi điểm
     */
    Optional<Score> settle(String veinCode, Instant windowStart, Instant heldUntil);

    /** Thư thưởng còn chờ gửi. */
    List<Payout> pendingPayouts();

    /** Xoá một mục khỏi hàng đợi thưởng. */
    void markPaid(Payout payout);
}
