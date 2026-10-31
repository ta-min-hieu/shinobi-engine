package dev.kage.world.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Kỳ ngộ (ADR-121, V101) — hiện thực ở server-database. Mở và chọn đều là MỘT giao dịch dưới khoá túi của nhân vật
 * (cùng thứ tự khoá chung), nên hai lượt hạ quái gần nhau không thể cùng mở hai kỳ ngộ hay vượt trần ngày.
 */
public interface EncounterPort {

    /** Kết quả chọn. */
    enum Outcome {
        OK,
        NOT_FOUND,
        INVALID_OPTION,
        BAG_FULL
    }

    /** Một lựa chọn hiển thị. */
    record Option(int index, String labelKey) {}

    /** Một kỳ ngộ vừa mở cho nhân vật. */
    record Offer(long instanceId, String promptKey, List<Option> options, Instant expiresAt) {

        public Offer {
            options = List.copyOf(options);
        }
    }

    /**
     * Kết quả một lựa chọn; {@code spawnMonsterCode} có thì phía gọi sinh quái cạnh người chơi.
     *
     * @param itemTemplateId 0 nếu không thưởng vật phẩm
     */
    record Resolution(
            Outcome outcome,
            String resultKey,
            long gold,
            long itemTemplateId,
            int quantity,
            Optional<String> spawnMonsterCode) {

        /** Lựa chọn bị từ chối — không thưởng gì. */
        public static Resolution refused(Outcome outcome) {
            return new Resolution(outcome, "", 0L, 0L, 0, Optional.empty());
        }
    }

    /**
     * Mở một kỳ ngộ nếu nhân vật chưa có kỳ ngộ đang chờ và số kỳ ngộ từ {@code dayStart} còn dưới
     * {@link EncounterRules#DAILY_LIMIT}; {@code pick} chọn chỉ số kỳ ngộ từ mảng trọng số (thứ tự theo id). Rỗng =
     * không mở.
     */
    Optional<Offer> tryStart(long characterId, Instant now, Instant dayStart, ToIntFunction<int[]> pick);

    /** Chọn một lựa chọn cho kỳ ngộ đang chờ (chưa hết hạn) của chính nhân vật; thưởng cấp trong cùng giao dịch. */
    Resolution choose(long characterId, long instanceId, int optionIndex, Instant now);
}
