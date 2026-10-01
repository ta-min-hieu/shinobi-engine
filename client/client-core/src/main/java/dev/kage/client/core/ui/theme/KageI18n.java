package dev.kage.client.core.ui.theme;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;

/**
 * Nạp bundle {@code i18n/strings} — mỗi Stage riêng ({@code CombatHud}, {@code LoginScreen},
 * {@code CharacterSelectScreen}) tự gọi {@link #load()} để có bundle CỦA RIÊNG mình, không chia sẻ
 * 1 instance dùng chung (mỗi Screen/HUD dispose độc lập, đời sống khác nhau). Gom lại đúng 4 dòng
 * lặp lại y hệt ở 3 nơi — KHÔNG phải abstraction tổng quát hơn nhu cầu hiện tại.
 *
 * <p><b>Ngôn ngữ (song ngữ Việt–Anh, quyết định chủ dự án 2026-10-01).</b> {@link #load()} nạp theo
 * {@link #currentLanguage()} — một giá trị CỦA TIẾN TRÌNH, chỉ đổi qua {@link #useLanguage} ở màn
 * đăng nhập. Mọi màn hình/HUD/coordinator nạp bundle MỘT lần lúc dựng và giữ nó suốt đời mình, nên
 * đổi ngôn ngữ chỉ có hiệu lực với những gì dựng SAU lúc đổi. Điều đó đúng vì chỉ có một chỗ đổi được
 * — {@code LoginScreen}, trước khi vào thế giới — và nó tự nạp lại bundle của chính nó; màn chọn
 * nhân vật, màn world và mọi HUD đều được dựng mới sau đó. Muốn mở nút đổi ngôn ngữ ở chỗ khác (VD
 * trong game) thì phải dựng lại cả cây UI đang sống, không chỉ gọi {@link #useLanguage}.
 */
public final class KageI18n {

    private static final String BUNDLE_PATH = "i18n/strings";
    private static final String BUNDLE_ENCODING = "UTF-8";

    /** Chỉ đọc/ghi trên luồng render; {@code volatile} để không ai phải chứng minh điều đó. */
    private static volatile ClientLanguage language = ClientLanguage.DEFAULT;

    private KageI18n() {}

    /** Bundle của ngôn ngữ đang dùng ({@link #currentLanguage()}). */
    public static I18NBundle load() {
        return load(Gdx.files.internal(BUNDLE_PATH), language);
    }

    /**
     * Nạp bundle {@code baseFile_<code>.properties} của {@code target}. Tách riêng để test dựng được
     * bundle thật bằng {@code new FileHandle(File)} mà không cần libGDX khởi tạo.
     */
    static I18NBundle load(FileHandle baseFile, ClientLanguage target) {
        I18NBundle.setExceptionOnMissingKey(
                false); // key thiếu hiện "???key???" thay vì crash UI (BA dấu hỏi — xem isMissingMarker).
        return I18NBundle.createBundle(baseFile, target.locale(), BUNDLE_ENCODING);
    }

    /** Ngôn ngữ mà lần {@link #load()} kế tiếp sẽ nạp. */
    public static ClientLanguage currentLanguage() {
        return language;
    }

    /**
     * Đổi ngôn ngữ cho các lần {@link #load()} SAU — bundle đã nạp từ trước KHÔNG tự đổi theo (xem
     * javadoc lớp). Không tự lưu xuống máy: việc đó của {@link LanguageSettings}.
     */
    public static void useLanguage(ClientLanguage target) {
        language = target;
    }

    /**
     * Dịch {@code error_key} server gửi, rơi về {@code fallbackKey} khi bundle không có nó.
     *
     * <p>Server gửi {@code error_key} để client DÙNG THẲNG làm khoá i18n. Rà bằng máy 2026-09-19:
     * server sinh 25 khoá {@code error.*} mà bundle chỉ có 10 — tức cái bất biến ghi trong javadoc
     * {@code FactionController.errorMessageKey} ("mọi khoá server sinh ra đều đã có mặt trong
     * bundle") **không đúng**. Thiếu khoá thì cần một câu chung, không phải một chuỗi khoá trần.
     *
     * <p>⚠️ Phải soi {@code ??key??} chứ KHÔNG bắt {@code MissingResourceException}:
     * {@link #load()} gọi {@code setExceptionOnMissingKey(false)}, nên {@code bundle.get} với khoá
     * thiếu **không ném gì cả** — nó trả về đúng chuỗi ấy. Một {@code try/catch} ở đây trông như
     * đang phòng thủ mà thật ra không bao giờ chạy, và {@code ??error.abc??} sẽ hiện thẳng lên màn
     * hình người chơi. (Bản đầu của chính lần sửa này viết sai đúng kiểu đó.)
     */
    public static String errorText(I18NBundle bundle, String errorKey, String fallbackKey) {
        if (errorKey == null || errorKey.isBlank() || isMissingMarker(bundle.get(errorKey), errorKey)) {
            return bundle.get(fallbackKey);
        }
        return bundle.get(errorKey);
    }

    /**
     * Tra bản dịch, thiếu thì trả về CHÍNH khoá đó — hợp đồng mà javadoc của 24 lớp HUD
     * và coordinator đã hứa từ lâu: nội dung nằm trong script/DB phía server còn bản dịch nằm ở
     * client, hai nhịp cập nhật khác nhau, nên "thêm nội dung mà quên dịch" là chuyện SẼ xảy ra.
     *
     * <p>⚠️ Mỗi lớp đó tự viết một {@code try/catch MissingResourceException} để giữ lời hứa ấy, và
     * <b>không cái nào từng chạy</b>: {@link #load()} gọi {@code setExceptionOnMissingKey(false)} — cờ
     * <b>static trên cả lớp</b> {@code I18NBundle}, nên một lần gọi là mọi bundle trong tiến trình đều
     * thôi ném. Cái người chơi thực sự đọc là {@code ??quest.name.foundation_breakthrough??}.
     * Dồn về đây để phép kiểm chỉ còn một chỗ, và là phép kiểm THẬT.
     */
    public static String textOrKey(I18NBundle bundle, String key) {
        String text = bundle.get(key);
        return isMissingMarker(text, key) ? key : text;
    }

    /**
     * Như {@link #textOrKey} nhưng có tham số để chèn.
     *
     * <p>Phải hỏi {@code get} TRƯỚC rồi mới {@code format}: với khoá thiếu, {@code format} đem chính chuỗi
     * {@code ??key??} đi qua {@code MessageFormat} — không có ô {@code {0}} nào để điền nên tham số bị nuốt
     * im lặng và kết quả vẫn là {@code ??key??}.
     */
    public static String formatOrKey(I18NBundle bundle, String key, Object... arguments) {
        return isMissingMarker(bundle.get(key), key) ? key : bundle.format(key, arguments);
    }

    /**
     * {@code I18NBundle} báo "không có khoá này" bằng cách trả về chính khoá kẹp giữa <b>BA</b> dấu
     * hỏi: {@code ???key???}, không phải hai.
     *
     * <p>⚠️ Bản đầu của hàm này (2026-09-19) đếm HAI, và test đi kèm cũng đếm hai — tức cả hai đều
     * chép lại cùng một phỏng đoán chứ không ai hỏi {@code I18NBundle} một câu. Hệ quả: bản vá
     * {@link #errorText} tưởng đã sửa xong hôm ấy thực ra <b>không nhận ra một khoá thiếu nào</b>. Con
     * số ba này nay được khoá bằng một bundle THẬT trong {@code KageI18nMissingKeyTest}, không bằng trí
     * nhớ — libGDX đổi thì ca đó đỏ trước.
     */
    static boolean isMissingMarker(String text, String key) {
        return text == null || text.equals("???" + key + "???");
    }
}
