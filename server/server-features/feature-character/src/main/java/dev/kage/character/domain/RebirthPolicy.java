package dev.kage.character.domain;

import java.util.Optional;

/**
 * Độ Kiếp Chuyển Thế (ADR-089, GDD §5) — hàm thuần. Ở cấp tối đa, nhân vật trùng tu từ cấp 1, giữ pháp khí/danh hiệu
 * và nhận Đạo Vận — chỉ số cộng phẳng VĨNH VIỄN cho mỗi lần chuyển thế. Mọi con số là PLACEHOLDER cân bằng.
 */
public final class RebirthPolicy {

    /** Tối đa số lần chuyển thế (GDD §5 "Tối đa 5 lần ở MVP"). */
    public static final int MAX_REBIRTHS = 5;

    /** Máu tối đa cộng thêm mỗi lần chuyển thế. */
    public static final int HP_PER_REBIRTH = 30;

    /** Công / thủ / kháng cộng thêm mỗi lần chuyển thế. */
    public static final int STAT_PER_REBIRTH = 4;

    private RebirthPolicy() {}

    /** Lý do không chuyển thế được, hoặc rỗng nếu được. */
    public static Optional<RebirthOutcome> refusal(int level, int rebirthCount) {
        if (rebirthCount >= MAX_REBIRTHS) {
            return Optional.of(RebirthOutcome.MAX_REBIRTHS);
        }
        if (level < ExperienceCurve.MAX_LEVEL) {
            return Optional.of(RebirthOutcome.LEVEL_TOO_LOW);
        }
        return Optional.empty();
    }
}
