package dev.kage.client.core.ui.theme;

import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.I18NBundle;

/**
 * Nạp bundle {@code i18n/strings} — mỗi Stage riêng ({@code CombatHud}, {@code LoginScreen},
 * {@code CharacterSelectScreen}) tự gọi {@link #load()} để có bundle CỦA RIÊNG mình, không chia sẻ
 * 1 instance dùng chung (mỗi Screen/HUD dispose độc lập, đời sống khác nhau). Gom lại đúng 4 dòng
 * lặp lại y hệt ở 3 nơi — KHÔNG phải abstraction tổng quát hơn nhu cầu hiện tại.
 */
public final class KageI18n {

    private static final String BUNDLE_PATH = "i18n/strings";

    private KageI18n() {}

    public static I18NBundle load() {
        I18NBundle.setExceptionOnMissingKey(false); // key thiếu hiện "??key??" thay vì crash UI.
        return I18NBundle.createBundle(Gdx.files.internal(BUNDLE_PATH), Locale.of("vi"), "UTF-8");
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

    /** {@code I18NBundle} báo "không có khoá này" bằng cách trả về chính khoá kẹp giữa {@code ??}. */
    static boolean isMissingMarker(String text, String key) {
        return text == null || text.equals("??" + key + "??");
    }
}
