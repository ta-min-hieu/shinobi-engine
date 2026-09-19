package dev.kage.client.core.ui.hud;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
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
 * Cửa sổ chọn hệ phái ở Lv10 (2026-08-27) — mở bằng phím H, neo GIỮA màn hình như
 * {@link PartyPanel}/{@link GuildPanel}.
 *
 * <p>Không có ô nhập chữ nào: đây là một lựa chọn từ danh sách đóng, và là một lựa chọn ĐỔI ĐỜI
 * nhân vật (hệ phái quyết định chiêu thức lẫn model 3D). Vì vậy nó vẫn chặn toàn bộ phím gameplay
 * khi mở, cùng lý do {@link GuildPanel#isOpen()} — bấm nhầm một nút ở đây tốn hơn nhiều so với bấm
 * nhầm trong khung bang hội.
 *
 * <p>{@code Stage} RIÊNG cùng lý do {@link DialoguePanel}/{@link ChatPanel}. Widget chỉ VẼ (UI.md
 * §4): nó không biết {@code Faction} là gì, không dựng packet — nó nhận các dòng ĐÃ dịch sẵn và báo
 * ra CHỈ SỐ dòng người chơi vừa bấm, đúng cách {@link PartyPanel.InviteRow} báo ra {@code inviteId}.
 */
public final class FactionPanel implements HudWindow {

    /**
     * Phím mở. H = "hệ (phái)"; T/F/G/P đã có chủ (chat, bạn bè, bang hội, nhóm). Nằm ở đây chứ
     * không ở {@code GameAction} vì mở một cửa sổ không phải ý định gameplay — cùng lập luận
     * {@link ChatPanel}/{@link GuildPanel}.
     */
    private static final int OPEN_KEY = Input.Keys.H;

    private static final float PANEL_WIDTH_PX = 520f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float CHOICE_BUTTON_WIDTH_PX = 112f;

    /** Vàng đồng nhạt — tiêu đề, cùng tông viền HUD của {@link KageSkin}. */
    private static final Color TITLE_COLOR = Color.valueOf("E0B860");

    /** Xám nhạt — dòng gợi ý cách đóng, không phải nội dung. */
    private static final Color HINT_COLOR = Color.valueOf("A89878");

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n = KageI18n.load();
    private final Table panel;
    private final Label statusLabel;
    private final Table choiceRow;

    private IntConsumer onSelect;

    /** Xem {@link #setCanOpen}. Mặc định LUÔN cho mở, để lớp này tự chạy được trong preview/test. */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link DialoguePanel}/{@link ChatPanel}.
     */
    public FactionPanel(KageSkin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        Label title = new Label(text("faction.title"), skin.labelStyle);
        title.setColor(TITLE_COLOR);

        Label header = new Label(text("faction.header.choose"), skin.labelStyle);
        header.setWrap(true);

        this.statusLabel = new Label("", skin.labelStyle);
        statusLabel.setWrap(true);
        statusLabel.setColor(TITLE_COLOR);

        this.choiceRow = new Table();

        Label hint = new Label(text("faction.hint.close"), skin.labelStyle);
        hint.setColor(HINT_COLOR);

        this.panel = new Table();
        panel.setBackground(skin.panel);
        panel.pad(PANEL_PAD_PX);
        panel.add(title).width(PANEL_WIDTH_PX).left().row();
        panel.add(header).width(PANEL_WIDTH_PX).left().padTop(8f).row();
        panel.add(choiceRow).width(PANEL_WIDTH_PX).growX().padTop(12f).row();
        panel.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(10f).row();
        panel.add(hint).width(PANEL_WIDTH_PX).left().padTop(10f).row();
        panel.setVisible(false);

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.center).add(panel);
        stage.addActor(container);

        installKeyHandling();
    }

    /**
     * Phím H mở, phím H hoặc ESC đóng.
     *
     * <p>Bật/tắt được bằng CÙNG một phím (khác {@link GuildPanel}, giống {@link PartyPanel}): cửa sổ
     * này không có ô nhập chữ nào, nên không có chuyện chữ "h" người chơi đang gõ tự đóng cửa sổ.
     *
     * <p><b>Đang mở thì NUỐT mọi {@code keyUp}</b>, không riêng phím H — {@code InputMultiplexer}
     * chuyển {@code keyUp} xuống tầng dưới cho tới khi có ai trả {@code true}, và mỗi panel chỉ nhận
     * ra phím mở của CHÍNH nó; không nuốt thì một phím bấm ở đây sẽ mở thêm cửa sổ khác nằm dưới.
     */
    private void installKeyHandling() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (isOpen()) {
                    if (keycode == OPEN_KEY) {
                        close();
                    }
                    return true; // xem javadoc: không để phím rơi xuống panel khác đang chờ phím mở
                }
                if (keycode != OPEN_KEY || !canOpen.getAsBoolean()) {
                    return false;
                }
                open();
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ESCAPE || !isOpen()) {
                    return false;
                }
                close();
                return true;
            }
        });
    }

    /**
     * Điều kiện được phép MỞ, hỏi ngay trước khi mở — {@code WorldScreen} truyền vào câu hỏi ghép
     * hai vế: "không khung nào khác đang nhận chữ gõ" VÀ "nhân vật này còn được mời chọn hệ".
     *
     * <p>Đóng thì không hỏi: người chơi phải luôn thoát ra được.
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Chỉ số dòng người chơi vừa bấm, tính theo thứ tự danh sách đưa vào {@link #setChoices}. */
    public void setOnSelect(IntConsumer listener) {
        this.onSelect = listener;
    }

    /**
     * Vẽ lại hàng nút chọn. Gọi khi danh sách ĐỔI (một lần lúc dựng, thực tế), không phải mỗi khung
     * — dựng lại actor 60 lần một giây là cấp phát thuần tuý trong game loop (Điều 31).
     *
     * @param displayTexts tên từng hệ, ĐÃ dịch sẵn — panel không tra i18n cho dữ liệu nó không biết.
     */
    public void setChoices(List<String> displayTexts) {
        choiceRow.clear();
        for (int index = 0; index < displayTexts.size(); index++) {
            int choiceIndex = index;
            choiceRow
                    .add(button(displayTexts.get(index), () -> select(choiceIndex)))
                    .width(CHOICE_BUTTON_WIDTH_PX)
                    .padRight(8f);
        }
    }

    /** Dòng trạng thái dưới hàng nút — chuỗi ĐÃ dịch (khoá i18n tra ở coordinator, chỗ biết outcome). */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * Đang mở hay không. {@code WorldScreen} BẮT BUỘC hỏi câu này trước khi đọc phím gameplay: phím
     * di chuyển/đánh được poll thẳng qua {@code Gdx.input.isKeyPressed}, đi vòng qua lưng
     * {@code InputProcessor} — xem {@link GuildPanel#isOpen()}.
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    /** Mở cửa sổ — dùng cho công cụ xem trước bố cục ({@code HudPreviewTool}) và cho phím H. */
    public void open() {
        panel.setVisible(true);
    }

    public void close() {
        panel.setVisible(false);
        stage.setKeyboardFocus(null);
    }

    private void select(int index) {
        if (onSelect != null) {
            onSelect.accept(index);
        }
    }

    private TextButton button(String label, Runnable action) {
        TextButton button = new TextButton(label, skin.buttonStyle);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
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

    /** KHÔNG dispose {@link KageSkin} nhận ở constructor — của {@link CombatHud}, xem javadoc đó. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
