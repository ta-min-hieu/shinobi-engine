package dev.kage.client.core.ui.theme;

/**
 * Ánh xạ <b>cấp độ → cảnh giới tu luyện</b> ([ADR-038 D2](docs/01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md)).
 *
 * <p><b>Đây thuần tuý là CÁCH TRÌNH BÀY.</b> Cột {@code character.level} trong DB, trường
 * {@code level} trong packet, mọi công thức damage/điều kiện mặc đồ đều tiếp tục làm việc với con
 * số 1-100 y như trước — ADR-038 đổi bối cảnh chứ không đổi cơ chế. Lớp này chỉ trả lời một câu
 * hỏi duy nhất: con số đó hiện lên màn hình bằng chữ gì.
 *
 * <p><b>Không đụng libGDX.</b> Trả về <i>khoá</i> i18n chứ không phải chữ đã dịch, nên chạy được
 * trong test thường (không cần {@code Gdx.files} như {@link KageI18n#load()}) và không ràng lớp
 * này vào một ngôn ngữ nào. Bên gọi tra khoá rồi ghép bằng {@code realm.tier}/{@code
 * realm.tier_short}/{@code realm.perfect}.
 *
 * <p><b>Bảng ánh xạ</b> (ADR-038 D2 — 6 đại cảnh giới, mỗi cảnh 9 tầng):
 *
 * <pre>
 *   Cấp   1- 18  Luyện Khí     2 cấp/tầng
 *   Cấp  19- 36  Trúc Cơ       2 cấp/tầng
 *   Cấp  37- 54  Tử Phủ        2 cấp/tầng
 *   Cấp  55- 72  Kim Đan       2 cấp/tầng
 *   Cấp  73- 90  Nguyên Anh    2 cấp/tầng
 *   Cấp  91-100  Hoá Thần      1 cấp/tầng, riêng cấp 100 là Viên Mãn (mốc Độ Kiếp)
 * </pre>
 *
 * <p>Hoá Thần cố ý lên tầng nhanh gấp đôi năm cảnh giới trước: 10 cấp cuối trải 9 tầng, tạo cảm
 * giác dồn dập trước Độ Kiếp thay vì đều đều như đoạn giữa.
 *
 * @param nameKey khoá i18n của tên cảnh giới ({@code realm.luyen_khi}...)
 * @param tier tầng trong cảnh giới, luôn nằm trong 1..9
 * @param perfected đã tới đỉnh cảnh giới cuối (cấp 100) — bên gọi nên dùng {@code realm.perfect}
 *     thay cho {@code realm.tier}
 */
public record CultivationRealm(String nameKey, int tier, boolean perfected) {

    /** Cấp thấp nhất một nhân vật có thể có; số nhỏ hơn được kẹp về đây. */
    private static final int MIN_LEVEL = 1;

    /** Trần cấp của MVP (GDD §5) — cũng là mốc Độ Kiếp. */
    private static final int MAX_LEVEL = 100;

    /** Số tầng của mọi đại cảnh giới. */
    private static final int TIERS_PER_REALM = 9;

    /** Cấp cuối cùng còn thuộc năm cảnh giới "2 cấp một tầng". */
    private static final int LAST_TWO_LEVELS_PER_TIER = 90;

    /** Bề rộng của một đại cảnh giới ở đoạn 2 cấp/tầng. */
    private static final int LEVELS_PER_EARLY_REALM = 18;

    /**
     * Năm cảnh giới đầu, theo đúng thứ tự cấp — chỉ số trong mảng chính là số cảnh giới đã đi qua.
     * Cảnh giới thứ sáu (Hoá Thần) tính riêng vì nhịp tầng của nó khác.
     */
    private static final String[] EARLY_REALM_KEYS = {
        "realm.luyen_khi", "realm.truc_co", "realm.tu_phu", "realm.kim_dan", "realm.nguyen_anh"
    };

    /** Cảnh giới thứ sáu — 1 cấp một tầng. */
    private static final String FINAL_REALM_KEY = "realm.hoa_than";

    /**
     * Cảnh giới ứng với một cấp độ.
     *
     * <p>Kẹp giá trị ngoài khoảng thay vì ném ngoại lệ: {@code level} tới từ packet
     * ({@code CharacterSummary.getLevel()}), và một trường proto chưa được server đặt sẽ về đây
     * dưới dạng {@code 0}. Màn hình chọn nhân vật hiện "Luyện Khí 1" cho một gói tin thiếu trường
     * vẫn tốt hơn là làm sập cả màn hình vì một con số.
     *
     * @param level cấp độ thật của nhân vật
     * @return cảnh giới + tầng tương ứng
     */
    public static CultivationRealm of(int level) {
        int clamped = Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
        if (clamped > LAST_TWO_LEVELS_PER_TIER) {
            int tier = Math.min(clamped - LAST_TWO_LEVELS_PER_TIER, TIERS_PER_REALM);
            return new CultivationRealm(FINAL_REALM_KEY, tier, clamped == MAX_LEVEL);
        }
        int realmIndex = (clamped - 1) / LEVELS_PER_EARLY_REALM;
        int levelInRealm = clamped - (realmIndex * LEVELS_PER_EARLY_REALM);
        // Hai cấp một tầng, làm tròn LÊN: cấp 1 và 2 đều là tầng 1, cấp 3 và 4 là tầng 2...
        int tier = (levelInRealm + 1) / 2;
        return new CultivationRealm(EARLY_REALM_KEYS[realmIndex], tier, false);
    }
}
