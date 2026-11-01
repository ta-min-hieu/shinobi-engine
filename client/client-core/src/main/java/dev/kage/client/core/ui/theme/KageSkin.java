package dev.kage.client.core.ui.theme;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
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
 * <p>Font nạp từ CLASSPATH ({@code fonts/…}), không phải từ thư mục làm việc. Bản trước dùng
 * {@code ../raw/fonts/…} — một đường dẫn THOÁT RA NGOÀI {@code workingDir} ({@code assets/exported/}),
 * chạy được ở dev chỉ vì mọi task Gradle đều tự đặt {@code workingDir} về gốc asset. Bản CÀI thì
 * không: nó khởi động từ chỗ người dùng đứng, nên đường đó trỏ vào hư không và client **không hiện
 * được một chữ nào**. Đo thật 2026-09-16: {@code installDist} ra {@code bin/} + {@code lib/} với 69
 * jar và KHÔNG một file {@code .ttf} nào.
 *
 * <p>Nay {@code client-core/build.gradle.kts} copy đúng file đó từ {@code assets/raw/fonts/} vào
 * classpath lúc {@code processResources} — một nguồn sự thật, không nhân bản file trong repo. Cùng
 * cơ chế {@code i18n/strings} và {@code tls/dev-cert.pem} vẫn chạy, nên đường này đã được chứng
 * minh chứ không phải mới.
 * {@code LoginScreen}/{@code CharacterSelectScreen} (client-core/screen/) dùng chung skin này.
 */
public final class KageSkin implements Disposable {

    private static final String FONT_PATH = "fonts/BeVietnamPro-Regular.ttf";
    /** Cỡ chữ UI duy nhất — package-private để {@code TextWidthMeter} (test) đo đúng cỡ đang vẽ. */
    static final int FONT_SIZE_PX = 20;

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
     *
     * <p><b>Lô 25 (2026-11-01) — khung tu tiên "ngọc và lụa"</b> thay nâu-đen/vàng đồng: nền lụa mực xanh, viền ngọc,
     * chỉ vàng bên trong, ngọc thoi ở góc cửa sổ ({@link JadeSilkFrames}, nine-patch). Thanh Máu/Khí/EXP giữ màu cũ.
     */
    private static final Color PANEL_BACKGROUND = Color.valueOf("0F1C1BF0");

    private static final Color PANEL_BORDER = Color.valueOf("4FA58CFF");

    /** Sợi chỉ vàng trong viền và vòng trong của nút tròn. */
    private static final Color GOLD_THREAD = Color.valueOf("D4AF5AFF");

    /** Viền khi nhấn: ngọc sáng hẳn lên. */
    private static final Color JADE_BRIGHT = Color.valueOf("8FD9C0FF");

    private static final JadeSilkFrames.Palette DISABLED =
            new JadeSilkFrames.Palette(Color.valueOf("181C1BF0"), Color.valueOf("4A5A55FF"), Color.valueOf("6B5A3AFF"));

    private static final Color HEALTH_RED = Color.valueOf("D32F2F");

    private static final Color CHI_BLUE = Color.valueOf("2979FF");

    /**
     * Vàng cam ĐẬM — vạch kinh nghiệm (2026-08-09). Khác hẳn đỏ Máu/xanh Khí để liếc là phân biệt.
     * Tông đậm chứ không phải vàng sáng: số EXP màu trắng đè lên trên, thử FFB300 trước thì chữ
     * chìm hẳn vào nền ở phần đã đầy (thấy thật trên ảnh chụp preview).
     */
    private static final Color EXP_AMBER = Color.valueOf("B26A00");

    /** Độ dày viền khung/nút, pixel — cùng một giá trị cho mọi khung để HUD nhất quán. */
    private static final int BORDER_PX = 2;

    /** Bề rộng thanh cuộn dọc — xem {@link #scrollPaneStyle}. */
    private static final float SCROLLBAR_WIDTH_PX = 10f;

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

    /**
     * Vùng cuộn cho danh sách dài (túi đồ, trang bị). Chỉ có thanh cuộn DỌC: mọi danh sách trong
     * HUD đều rộng cố định theo khung chứa, không có gì để cuộn ngang.
     *
     * <p>Không có {@code background}: vùng cuộn luôn nằm TRONG một khung đã có nền
     * ({@link #panel}), thêm một lớp nền nữa chỉ làm tối chồng lên tối.
     */
    public final ScrollPane.ScrollPaneStyle scrollPaneStyle;

    public KageSkin() {
        FileHandle fontFile = Gdx.files.internal(FONT_PATH);
        this.font = FontFactory.generate(fontFile, FONT_SIZE_PX);

        this.healthFill = solid(HEALTH_RED); // đỏ — thanh Máu (đổi từ xanh lá cho khớp Ninja School)
        this.healthEmpty = solid(Color.valueOf("2A1410")); // nâu rất tối — phần đã mất
        this.chiFill = solid(CHI_BLUE);
        this.expFill = solid(EXP_AMBER);

        this.panel = framed(64, new JadeSilkFrames.Palette(PANEL_BACKGROUND, PANEL_BORDER, GOLD_THREAD), true);
        this.slot = framed(48, new JadeSilkFrames.Palette(Color.valueOf("142625F0"), PANEL_BORDER, GOLD_THREAD), false);
        this.slotPressed =
                framed(48, new JadeSilkFrames.Palette(Color.valueOf("24463FF0"), JADE_BRIGHT, GOLD_THREAD), false);
        this.roundButton = disc(96, new JadeSilkFrames.Palette(Color.valueOf("142625E0"), PANEL_BORDER, GOLD_THREAD));
        this.roundButtonPressed =
                disc(96, new JadeSilkFrames.Palette(Color.valueOf("24463FE0"), JADE_BRIGHT, GOLD_THREAD));
        this.roundButtonDisabled = disc(96, DISABLED);

        this.labelStyle = new Label.LabelStyle(font, Color.WHITE);
        this.statLabelStyle = new Label.LabelStyle(font, Color.valueOf("FFE9B0"));

        this.buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = slot;
        buttonStyle.down = slotPressed;
        buttonStyle.disabled = framed(48, DISABLED, false);
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

        this.scrollPaneStyle = buildScrollPaneStyle();

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
     * Tách khỏi constructor để nó không vượt ngưỡng {@code MethodLength} của Checkstyle (max=50) —
     * cùng lý do {@code CombatHud} tách từng cụm ra hàm riêng.
     *
     * <p>BẮT BUỘC đặt {@code minWidth}: {@link #solid} sinh drawable từ ảnh 1x1, mà {@link ScrollPane}
     * lấy ĐÚNG {@code minWidth} của drawable làm bề rộng thanh cuộn — để nguyên thì thanh cuộn rộng
     * 1px, nhìn như không có (thấy bằng ảnh chụp {@code HudPreviewTool}, build xanh không nói gì).
     */
    private ScrollPane.ScrollPaneStyle buildScrollPaneStyle() {
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        style.vScroll = sized(solid(Color.valueOf("2A1C0C80")), SCROLLBAR_WIDTH_PX); // rãnh: nâu tối, mờ
        style.vScrollKnob = sized(solid(PANEL_BORDER), SCROLLBAR_WIDTH_PX); // tay kéo: vàng đồng của viền
        return style;
    }

    /**
     * Ép bề rộng/chiều cao tối thiểu cho một drawable sinh từ ảnh 1x1 — xem {@link #scrollPaneStyle}
     * về việc vì sao thiếu bước này thì thanh cuộn mỏng 1px.
     */
    private static Drawable sized(Drawable drawable, float minWidth) {
        drawable.setMinWidth(minWidth);
        drawable.setMinHeight(minWidth);
        return drawable;
    }

    /**
     * Khung ngọc-lụa vuông {@code size} ({@link JadeSilkFrames#frame}) dưới dạng NINE-PATCH: kéo giãn bao nhiêu thì
     * viền ngọc, chỉ vàng và ngọc góc vẫn giữ nguyên độ dày — bản {@code TextureRegionDrawable} cũ thì viền phình
     * theo tỉ lệ.
     *
     * <p>⚠️ Giữ NGUYÊN hình học bố cục của bản cũ: padding (left/right/top/bottom width) về 0 và kích thước tối
     * thiểu bằng cỡ ảnh. Để mặc định, {@link NinePatchDrawable} lấy {@link JadeSilkFrames#CORNER_PX} làm padding
     * cho mọi Table/nút dùng nó làm nền — mọi panel sẽ co nội dung lại 10px mỗi bên và nhãn tiếng Việt trên nút bề
     * rộng cố định tràn ra ngoài (đúng bẫy "nút bề rộng cố định" đã gặp).
     */
    private Drawable framed(int size, JadeSilkFrames.Palette palette, boolean gems) {
        Texture texture = own(JadeSilkFrames.frame(size, palette, gems));
        int corner = JadeSilkFrames.CORNER_PX;
        NinePatchDrawable drawable =
                new NinePatchDrawable(new NinePatch(new TextureRegion(texture), corner, corner, corner, corner));
        drawable.setLeftWidth(0f);
        drawable.setRightWidth(0f);
        drawable.setTopHeight(0f);
        drawable.setBottomHeight(0f);
        drawable.setMinWidth(size);
        drawable.setMinHeight(size);
        return drawable;
    }

    /** Nút tròn ngọc-lụa ({@link JadeSilkFrames#ring}), viền ngọc dày {@link #BORDER_PX} × 2. */
    private Drawable disc(int diameter, JadeSilkFrames.Palette palette) {
        return toDrawable(JadeSilkFrames.ring(diameter, BORDER_PX * 2, palette));
    }

    /** Texture từ Pixmap (lọc NEAREST — viền 1px của nine-patch không được nhoè sang ô bên cạnh), giải phóng Pixmap. */
    private Texture own(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        ownedTextures.add(texture);
        return texture;
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
