package dev.kage.client.core.ui.theme;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

import dev.kage.client.core.render.FontFactory;

/**
 * Bộ giao diện tối thiểu cho HUD (UI.md §3 {@code ui/theme/}) — KHÔNG dùng art atlas (chưa có),
 * mọi drawable là mảng màu phẳng 1x1 sinh runtime. Đủ cho HUD combat đầu tiên; khi có atlas UI thật
 * thì thay nội dung lớp này, không phải chỗ gọi nó (widget/HUD chỉ biết {@link #labelStyle}/
 * {@link #buttonStyle}/{@link #textFieldStyle}/{@link #healthFill}/{@link #healthEmpty}, không biết
 * chúng sinh ra thế nào).
 *
 * <p>⚠️ Font nạp qua đường dẫn tương đối {@code ../raw/fonts/} thoát khỏi {@code workingDir}
 * ({@code assets/exported/}, xem {@code client-desktop/build.gradle.kts}) — {@code assets/raw/}
 * chưa được đưa vào asset-pipeline (RESOURCE.md §7: "chưa có UI nào tiêu thụ"). Đây là lối tắt có
 * chủ đích cho bản demo đầu tiên, không phải tích hợp pipeline thật — theo dõi ở RESOURCE.md.
 * {@code LoginScreen}/{@code CharacterSelectScreen} (client-core/screen/) dùng chung skin này.
 */
public final class KageSkin implements Disposable {

    private static final String FONT_PATH = "../raw/fonts/BeVietnamPro-Regular.ttf";
    private static final int FONT_SIZE_PX = 20;

    private final BitmapFont font;
    private final List<Texture> ownedTextures = new ArrayList<>();

    /**
     * Bảng màu HUD kiểu Ninja School Online (ảnh tham chiếu chủ dự án gửi 2026-08-09): nền nâu-đen
     * rất tối, viền vàng đồng, thanh Máu đỏ, thanh Khí xanh dương.
     *
     * <p>⚠️ Bản gốc dùng khung PIXEL ART chạm trổ; ở đây mọi drawable vẫn sinh runtime bằng
     * {@link Pixmap} (chưa có atlas UI — xem javadoc lớp), nên tái hiện được BỐ CỤC + TÔNG MÀU +
     * KHUNG VIỀN, KHÔNG tái hiện được hoa văn. Khi có art UI thật thì thay ruột lớp này, chỗ gọi
     * không phải sửa.
     */
    private static final Color PANEL_BACKGROUND = Color.valueOf("1A1008F0");

    private static final Color PANEL_BORDER = Color.valueOf("C8922Eff");

    private static final Color HEALTH_RED = Color.valueOf("D32F2F");

    private static final Color CHI_BLUE = Color.valueOf("2979FF");

    /** Vàng cam — vạch kinh nghiệm (2026-08-09). Khác hẳn đỏ Máu/xanh Khí để liếc là phân biệt. */
    private static final Color EXP_AMBER = Color.valueOf("FFB300");

    /** Độ dày viền khung/nút, pixel — cùng một giá trị cho mọi khung để HUD nhất quán. */
    private static final int BORDER_PX = 2;

    public final Drawable healthFill;
    public final Drawable healthEmpty;
    public final Drawable chiFill;
    public final Drawable expFill;
    /** Nền panel HUD — nâu đen, viền vàng đồng. Dùng cho cụm chỉ số/thanh skill. */
    public final Drawable panel;
    /** Ô skill vuông (viền vàng, nền tối) — kiểu thanh skill giữa dưới của Ninja School. */
    public final Drawable slot;

    public final Drawable slotPressed;
    /** Nút TRÒN viền vàng — nút đánh/di chuyển kiểu Ninja School. */
    public final Drawable roundButton;

    public final Drawable roundButtonPressed;
    public final Drawable roundButtonDisabled;
    public final Label.LabelStyle labelStyle;
    /** Nhãn nhỏ hơn cho số liệu trong thanh Máu/Khí (đè lên thanh, không chiếm dòng riêng). */
    public final Label.LabelStyle statLabelStyle;

    public final TextButton.TextButtonStyle buttonStyle;
    /** Nút tròn lớn (đánh thường/skill) — nền tròn thay cho nền chữ nhật của {@link #buttonStyle}. */
    public final TextButton.TextButtonStyle roundButtonStyle;

    public final TextField.TextFieldStyle textFieldStyle;

    public KageSkin() {
        FileHandle fontFile = Gdx.files.internal(FONT_PATH);
        this.font = FontFactory.generate(fontFile, FONT_SIZE_PX);

        this.healthFill = solid(HEALTH_RED); // đỏ — thanh Máu (đổi từ xanh lá cho khớp Ninja School)
        this.healthEmpty = solid(Color.valueOf("2A1410")); // nâu rất tối — phần đã mất
        this.chiFill = solid(CHI_BLUE);
        this.expFill = solid(EXP_AMBER);

        this.panel = framed(64, 64, PANEL_BACKGROUND, PANEL_BORDER);
        this.slot = framed(48, 48, Color.valueOf("20160BF0"), PANEL_BORDER);
        this.slotPressed = framed(48, 48, Color.valueOf("4A3418F0"), Color.valueOf("F0C060"));
        this.roundButton = disc(96, 4, Color.valueOf("241708E0"), PANEL_BORDER);
        this.roundButtonPressed = disc(96, 4, Color.valueOf("4A3418E0"), Color.valueOf("F0C060"));
        this.roundButtonDisabled = disc(96, 4, Color.valueOf("1A1A1AC0"), Color.valueOf("6B5A3A"));

        this.labelStyle = new Label.LabelStyle(font, Color.WHITE);
        this.statLabelStyle = new Label.LabelStyle(font, Color.valueOf("FFE9B0"));

        this.buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = slot;
        buttonStyle.down = slotPressed;
        buttonStyle.disabled = framed(48, 48, Color.valueOf("181818F0"), Color.valueOf("6B5A3A"));
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.valueOf("FFE9B0");
        buttonStyle.disabledFontColor = Color.GRAY;

        this.roundButtonStyle = new TextButton.TextButtonStyle();
        roundButtonStyle.up = roundButton;
        roundButtonStyle.down = roundButtonPressed;
        roundButtonStyle.disabled = roundButtonDisabled;
        roundButtonStyle.font = font;
        roundButtonStyle.fontColor = Color.valueOf("FFE9B0");
        roundButtonStyle.disabledFontColor = Color.GRAY;

        this.textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = font;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = solid(Color.valueOf("1B2430"));
        textFieldStyle.cursor = solid(Color.WHITE);
        textFieldStyle.selection = solid(Color.valueOf("2A4D69"));
        textFieldStyle.messageFont = font;
        textFieldStyle.messageFontColor = Color.LIGHT_GRAY;
    }

    /**
     * Ô chữ nhật nền {@code background} viền {@code border} dày {@link #BORDER_PX}, bo 4 góc bằng
     * cách bỏ đúng 1 pixel mỗi góc — đủ để mắt đọc ra "khung có bo" ở kích thước HUD, không cần vẽ
     * cung tròn thật.
     *
     * <p>Trả {@link TextureRegionDrawable} chứ KHÔNG phải {@code NinePatchDrawable}: widget HUD kéo
     * giãn khung này ra kích thước bất kỳ, nên viền sẽ dày mỏng không đều theo tỉ lệ. Chấp nhận được
     * cho bản dựng tạm bằng Pixmap; có atlas UI thật thì chuyển sang nine-patch để viền giữ nguyên độ
     * dày.
     */
    private Drawable framed(int width, int height, Color background, Color border) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(border);
        pixmap.fill();
        pixmap.setColor(background);
        pixmap.fillRectangle(BORDER_PX, BORDER_PX, width - (BORDER_PX * 2), height - (BORDER_PX * 2));
        pixmap.setColor(Color.CLEAR);
        pixmap.setBlending(Pixmap.Blending.None); // ghi ĐÈ alpha=0, không trộn — để góc trong suốt thật
        pixmap.drawPixel(0, 0);
        pixmap.drawPixel(width - 1, 0);
        pixmap.drawPixel(0, height - 1);
        pixmap.drawPixel(width - 1, height - 1);
        return toDrawable(pixmap);
    }

    /** Đĩa tròn nền {@code background} viền {@code border} — nút tròn kiểu Ninja School. */
    private Drawable disc(int diameter, int borderPx, Color background, Color border) {
        Pixmap pixmap = new Pixmap(diameter, diameter, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None); // nền phải trong suốt THẬT ngoài đường tròn
        pixmap.setColor(Color.CLEAR);
        pixmap.fill();
        pixmap.setBlending(Pixmap.Blending.SourceOver);
        int radius = (diameter / 2) - 1;
        pixmap.setColor(border);
        pixmap.fillCircle(diameter / 2, diameter / 2, radius);
        pixmap.setColor(background);
        pixmap.fillCircle(diameter / 2, diameter / 2, radius - borderPx);
        return toDrawable(pixmap);
    }

    private Drawable toDrawable(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        ownedTextures.add(texture);
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    private Drawable solid(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose(); // Pixmap chỉ cần lúc tạo Texture, không giữ lại (khác Texture bên dưới).
        ownedTextures.add(texture);
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    public BitmapFont font() {
        return font;
    }

    @Override
    public void dispose() {
        font.dispose();
        for (Texture texture : ownedTextures) {
            texture.dispose();
        }
    }
}
