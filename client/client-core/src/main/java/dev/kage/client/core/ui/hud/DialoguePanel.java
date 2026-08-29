package dev.kage.client.core.ui.hud;

import java.util.List;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Khung hội thoại NPC (2026-08-09) — lời thoại + các lựa chọn, neo giữa-dưới màn hình.
 *
 * <p>{@code Stage} RIÊNG chứ không phải một actor trong {@code Stage} của {@link CombatHud}: hội
 * thoại phải nuốt click trước HUD và trước cả thế giới (đang nói chuyện mà bấm trúng nút Đánh phía
 * sau là hỏng), và thứ tự đó do vị trí trong {@code InputMultiplexer} quyết định — thứ chỉ đặt được
 * cho cả một {@code Stage}, không đặt được cho một actor bên trong.
 *
 * <p>Widget chỉ VẼ, không quyết định gì (UI.md §4): nó không biết {@code action} của lựa chọn, không
 * biết hội thoại đi tiếp đâu, chỉ báo ra CHỈ SỐ đã bấm qua {@link #setOnOptionSelected}.
 * {@code DialogueController} + {@code WorldScreen} lo phần còn lại.
 *
 * <p>Mọi chữ đều tra qua i18n theo khoá server gửi ({@code text_key}/{@code label_key}) — client
 * không bao giờ hiển thị chuỗi thô từ mạng (client/CLAUDE.md "Hardcode chuỗi hiển thị").
 */
public final class DialoguePanel implements HudWindow {

    private static final float PANEL_WIDTH_PX = 720f;

    private static final float PANEL_PAD_PX = 18f;

    private static final float BOTTOM_MARGIN_PX = 90f;

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final Table panel;
    private final Label textLabel;
    private final Table optionList;

    private IntConsumer onOptionSelected;

    /** Người chơi tự đóng khung (ESC) — trạng thái hội thoại phía ngoài phải biết mà dọn theo. */
    private Runnable onClosed;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó (cùng quy ước
     *     {@code CombatFeedbackPresenter} mượn {@code font()}). Dựng KageSkin thứ hai là sinh lại
     *     toàn bộ font FreeType chỉ để có đúng cùng một bộ màu.
     */
    public DialoguePanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.textLabel = new Label("", skin.labelStyle);
        this.textLabel.setWrap(true); // lời thoại dài phải xuống dòng, không tràn ra ngoài khung
        this.textLabel.setAlignment(Align.topLeft);
        this.optionList = new Table();

        this.panel = new Table();
        panel.setBackground(skin.panel);
        panel.pad(PANEL_PAD_PX);
        panel.add(textLabel).width(PANEL_WIDTH_PX).growX().row();
        panel.add(optionList).padTop(12f).width(PANEL_WIDTH_PX).growX().row();

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.bottom).add(panel).padBottom(BOTTOM_MARGIN_PX);
        stage.addActor(container);
        panel.setVisible(false);
        installEscapeToClose();
    }

    /**
     * ESC đóng khung hội thoại.
     *
     * <p>Không đi qua {@code GameAction} như phím gameplay (client/CLAUDE.md) vì đây không phải ý
     * định gameplay: nó không gửi gói nào, không đổi trạng thái thế giới, chỉ dọn UI của chính máy
     * này — server vốn không giữ phiên hội thoại nào để mà báo ({@code GetDialogueNodeUseCase} tra
     * lại cây script mỗi lần gọi).
     *
     * <p>Lý do tồn tại là một trạng thái KẸT có thật: mọi lối ra khỏi khung này đều là một lựa chọn
     * trong cây script, nên một node quên nhánh {@code "end"} sẽ nhốt người chơi trong khung thoại
     * không đóng được — mà cây script là dữ liệu, sửa được mà không cần đụng client, tức là client
     * không được phép giả định nó luôn đúng.
     */
    private void installEscapeToClose() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ESCAPE || !isVisible()) {
                    return false;
                }
                hide();
                if (onClosed != null) {
                    onClosed.run();
                }
                return true;
            }
        });
    }

    /** Nhận CHỈ SỐ lựa chọn vừa bấm — xem javadoc lớp về việc widget không biết action là gì. */
    public void setOnOptionSelected(IntConsumer listener) {
        this.onOptionSelected = listener;
    }

    /** Gọi khi người chơi tự đóng bằng ESC — xem {@link #installEscapeToClose}. */
    public void setOnClosed(Runnable listener) {
        this.onClosed = listener;
    }

    /**
     * Hiện một node. Dựng lại toàn bộ danh sách nút mỗi lần gọi — node đổi là đổi cả số lượng lẫn nội
     * dung lựa chọn, và một cây hội thoại chỉ nhảy node khi người chơi bấm (vài lần một phiên chơi),
     * không phải mỗi khung, nên đây không nằm trong đường cấm cấp phát của game loop (Điều 31).
     *
     * @param textKey khoá i18n của lời thoại, do server gửi.
     * @param optionLabelKeys khoá i18n của từng lựa chọn, THEO ĐÚNG THỨ TỰ server gửi — chỉ số trong
     *     danh sách này chính là {@code option_index} gửi ngược lại (dialogue.proto).
     */
    public void show(String textKey, List<String> optionLabelKeys) {
        textLabel.setText(text(textKey));
        optionList.clear();
        for (int index = 0; index < optionLabelKeys.size(); index++) {
            optionList
                    .add(optionButton(optionLabelKeys.get(index), index))
                    .growX()
                    .row();
        }
        panel.setVisible(true);
    }

    public void hide() {
        panel.setVisible(false);
        optionList.clear();
    }

    public boolean isVisible() {
        return panel.isVisible();
    }

    private TextButton optionButton(String labelKey, int index) {
        TextButton button = new TextButton(text(labelKey), skin.buttonStyle);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (onOptionSelected != null) {
                    onOptionSelected.accept(index);
                }
            }
        });
        return button;
    }

    /**
     * Tra bản dịch, và nếu chưa có thì hiện CHÍNH khoá đó.
     *
     * <p>Nội dung hội thoại nằm trong script GraalJS phía server ({@code town_elder.js}) còn bản dịch
     * nằm trong bundle phía client — hai file khác nhau, hai nhịp cập nhật khác nhau, nên "thêm một
     * nhánh hội thoại mới mà quên dịch" là chuyện SẼ xảy ra. Ném {@code MissingResourceException}
     * giữa lúc đang nói chuyện sẽ giết cả màn hình; hiện khoá thô cho người làm nội dung thấy ngay
     * mình thiếu gì thì vừa không sập vừa tự tố cáo.
     */
    private String text(String key) {
        try {
            return i18n.get(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    @Override
    public Stage stage() {
        return stage;
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render(float deltaTime) {
        stage.act(deltaTime);
        stage.draw();
    }

    /** KHÔNG dispose {@link #skin} — của {@link CombatHud}, xem javadoc constructor. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
