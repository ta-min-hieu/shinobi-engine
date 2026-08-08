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

    public final Drawable healthFill;
    public final Drawable healthEmpty;
    public final Label.LabelStyle labelStyle;
    public final TextButton.TextButtonStyle buttonStyle;
    public final TextField.TextFieldStyle textFieldStyle;

    public KageSkin() {
        FileHandle fontFile = Gdx.files.internal(FONT_PATH);
        this.font = FontFactory.generate(fontFile, FONT_SIZE_PX);

        this.healthFill = solid(Color.valueOf("4CAF50")); // xanh lá — còn máu
        this.healthEmpty = solid(Color.valueOf("3A1414")); // nâu đỏ đậm — nền/đã mất

        this.labelStyle = new Label.LabelStyle(font, Color.WHITE);

        this.buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = solid(Color.valueOf("2A4D69"));
        buttonStyle.down = solid(Color.valueOf("1B3A57"));
        buttonStyle.disabled = solid(Color.valueOf("444444"));
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.disabledFontColor = Color.LIGHT_GRAY;

        this.textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = font;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = solid(Color.valueOf("1B2430"));
        textFieldStyle.cursor = solid(Color.WHITE);
        textFieldStyle.selection = solid(Color.valueOf("2A4D69"));
        textFieldStyle.messageFont = font;
        textFieldStyle.messageFontColor = Color.LIGHT_GRAY;
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
