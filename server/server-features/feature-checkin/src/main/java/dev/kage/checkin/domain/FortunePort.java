package dev.kage.checkin.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Quẻ bói hằng ngày (ADR-149, V121) — hiện thực ở server-database. Gieo quẻ là MỘT giao dịch dưới khoá túi của nhân
 * vật: mỗi ngày (giờ Việt Nam) đúng một quẻ, quà kèm quẻ cấp trong cùng giao dịch.
 */
public interface FortunePort {

    /** Kết quả gieo quẻ. */
    enum Outcome {
        OK,
        ALREADY_DRAWN,
        BAG_FULL
    }

    /**
     * Một quẻ.
     *
     * @param expPercent phần trăm cộng (âm = trừ) vào EXP đánh quái tới hết ngày
     * @param dropBonusPercent % cơ hội tung bảng rơi thêm một lượt khi hạ quái (ADR-158)
     * @param itemTemplateId 0 nếu quẻ không kèm vật phẩm
     */
    record Fortune(
            String code,
            String nameKey,
            String textKey,
            int expPercent,
            long gold,
            long itemTemplateId,
            int quantity,
            int dropBonusPercent) {}

    /** Kết quả gieo: {@code fortune} là quẻ của ngày (vừa gieo, hoặc đã gieo từ trước). */
    record Draw(Outcome outcome, Optional<Fortune> fortune) {}

    /**
     * Gieo quẻ cho ngày {@code day}: đã gieo ⇒ {@code ALREADY_DRAWN} kèm quẻ cũ (không quà lần hai); chưa ⇒ chọn bằng
     * {@code pick} (mảng trọng số theo id), cấp quà, ghi lại. Túi đầy ⇒ {@code BAG_FULL}, chưa tính là đã gieo.
     */
    Draw draw(long characterId, LocalDate day, Instant now, ToIntFunction<int[]> pick);

    /** Quẻ đã gieo của ngày {@code day} (không khoá, chỉ đọc). */
    Optional<Fortune> today(long characterId, LocalDate day);
}
