package dev.kage.character.domain;

/**
 * Luật đổi linh căn (ADR-066, GDD §4 "đổi 1 lần miễn phí trước Lv30, sau đó tốn Tẩy Căn Đan").
 *
 * <p>Lần chọn ĐẦU TIÊN (thức tỉnh ở Lv10, rời hệ khởi đầu Hỏa) luôn miễn phí — đó là thức tỉnh, không
 * phải "đổi". Lần thứ hai miễn phí nếu còn dưới {@link #FREE_RECHANGE_BELOW_LEVEL}. Mọi lần sau tốn đan.
 */
public final class FactionChangePolicy {

    /** Cấp tối thiểu để chọn linh căn (GDD §4, Luyện Khí tầng 5). */
    public static final int MIN_LEVEL = 10;

    /** Lần đổi lại miễn phí chỉ có khi cấp còn DƯỚI mốc này. */
    public static final int FREE_RECHANGE_BELOW_LEVEL = 30;

    private FactionChangePolicy() {}

    /**
     * Lần đổi này có miễn phí không.
     *
     * @param changesSoFar số lần đã chọn/đổi trước lần này
     * @param level cấp hiện tại
     */
    public static boolean isFree(int changesSoFar, int level) {
        return changesSoFar == 0 || (changesSoFar == 1 && level < FREE_RECHANGE_BELOW_LEVEL);
    }
}
