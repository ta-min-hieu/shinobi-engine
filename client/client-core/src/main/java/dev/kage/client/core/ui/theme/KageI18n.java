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
}
