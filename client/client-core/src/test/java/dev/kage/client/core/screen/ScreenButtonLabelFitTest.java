package dev.kage.client.core.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.ClientLanguage;
import dev.kage.client.core.ui.theme.TextWidthMeter;

/**
 * Nút rộng cố định của màn đăng nhập / chọn nhân vật phải chứa vừa nhãn ở CẢ HAI ngôn ngữ — cùng
 * luật với {@code HudButtonLabelFitTest} (xem javadoc ở đó về lý do và về thước đo).
 */
class ScreenButtonLabelFitTest {

    private static final float FULL_SCALE = 1f;

    /**
     * Tên nhân vật 16 ký tự (trần độ dài tên) bằng chữ thường có độ rộng TRUNG BÌNH — cùng giả định
     * "~9,7px/ký tự" mà javadoc {@code CharacterSelectScreen.CHARACTER_ROW_WIDTH_PX} dùng để chọn 440px.
     */
    private static final String LONGEST_NAME_SAMPLE = "NguyenThanhHuyen";

    /** Tầng có hai chữ số không tồn tại (1..9) — "9" là bản rộng nhất của {@code realm.tier_short}. */
    private static final String WIDEST_TIER = "9";

    private static final List<String> AWAKENED_FACTION_KEYS =
            List.of("faction.hoa", "faction.thuy", "faction.phong", "faction.loi", "faction.tho");

    private static final String UNAWAKENED_KEY = "faction.unspecified";

    private static final List<String> REALM_KEYS = List.of(
            "realm.luyen_khi", "realm.truc_co", "realm.tu_phu", "realm.kim_dan", "realm.nguyen_anh", "realm.hoa_than");

    private static TextWidthMeter meter;

    @BeforeAll
    static void openMeter() {
        meter = new TextWidthMeter();
    }

    @AfterAll
    static void closeMeter() {
        meter.close();
    }

    @Test
    void loginAndCharacterSelectButtonsFitTheirLabelsInEveryLanguage() {
        List<String> problems = new ArrayList<>();
        problems.addAll(meter.overflows(
                "LoginScreen kết nối",
                LoginScreen.CONNECT_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of("login.button.connect")));
        problems.addAll(meter.overflows(
                "LoginScreen ngôn ngữ",
                LoginScreen.LANGUAGE_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of(ClientLanguage.VI.nameKey(), ClientLanguage.EN.nameKey())));
        problems.addAll(meter.overflows(
                "CharacterSelectScreen tạo",
                CharacterSelectScreen.CREATE_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of("charselect.button.create")));
        assertThat(problems).as("Nhãn vẽ tràn ra ngoài nền nút rộng cố định").isEmpty();
    }

    /**
     * Dòng nhân vật ở trường hợp TỆ NHẤT CÓ THỂ XẢY RA của từng ngôn ngữ: tên dài nhất × mọi linh căn
     * đã thức tỉnh × mọi cảnh giới (bản ngắn tầng 9; Viên Mãn chỉ có ở Hoá Thần), cộng "chưa thức
     * tỉnh" — vốn chỉ có ở Luyện Khí (thức tỉnh ở tầng 5, ADR-038). Tiếng Anh là phía nguy hiểm:
     * "Foundation Establishment" dài gấp ba "Trúc Cơ".
     */
    @Test
    void characterRowFitsTheLongestSummaryInEveryLanguage() {
        float available = CharacterSelectScreen.CHARACTER_ROW_WIDTH_PX - (2f * TextWidthMeter.MIN_SIDE_MARGIN_PX);
        List<String> problems = new ArrayList<>();
        for (ClientLanguage language : ClientLanguage.values()) {
            Properties bundle = TextWidthMeter.bundle(language);
            List<String> rows = new ArrayList<>();
            rows.add(row(bundle, UNAWAKENED_KEY, tierShort(bundle, "realm.luyen_khi")));
            for (String factionKey : AWAKENED_FACTION_KEYS) {
                for (String realmKey : REALM_KEYS) {
                    rows.add(row(bundle, factionKey, tierShort(bundle, realmKey)));
                }
                rows.add(row(
                        bundle,
                        factionKey,
                        fill(bundle.getProperty("realm.perfect"), bundle.getProperty("realm.hoa_than"))));
            }
            String widest = "";
            float widestPx = 0f;
            for (String row : rows) {
                float width = meter.widestLine(row, FULL_SCALE);
                if (width > widestPx) {
                    widestPx = width;
                    widest = row;
                }
            }
            if (widestPx > available) {
                problems.add(String.format(
                        Locale.ROOT,
                        "[%s] \"%s\" cần %.0fpx chữ, nút chỉ chứa %.0fpx",
                        language.code(),
                        widest,
                        widestPx,
                        available));
            }
        }
        assertThat(problems)
                .as("Dòng nhân vật dài nhất tràn khỏi nút rộng cố định")
                .isEmpty();
    }

    private static String tierShort(Properties bundle, String realmKey) {
        return fill(bundle.getProperty("realm.tier_short"), bundle.getProperty(realmKey), WIDEST_TIER);
    }

    private static String row(Properties bundle, String factionKey, String realmLabel) {
        return fill(
                bundle.getProperty("charselect.character.summary"),
                LONGEST_NAME_SAMPLE,
                bundle.getProperty(factionKey),
                realmLabel);
    }

    /** Thay {@code {0}}, {@code {1}}... — đủ cho các mẫu ở đây (không có định dạng số). */
    private static String fill(String pattern, String... arguments) {
        String result = pattern;
        for (int index = 0; index < arguments.length; index++) {
            result = result.replace("{" + index + "}", arguments[index]);
        }
        return result;
    }
}
