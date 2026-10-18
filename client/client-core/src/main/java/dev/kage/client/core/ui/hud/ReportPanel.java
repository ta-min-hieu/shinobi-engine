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
 * Cửa sổ CHỈ ĐỌC dạng "tiêu đề + (tuỳ chọn) hàng tab + danh sách dòng chữ + Làm mới" — dùng chung cho
 * Bảng nhân vật (ADR-056, phím C) và Bia Anh Danh (ADR-057, phím L). Cùng hình dạng
 * {@link QuestLogPanel}: neo giữa màn hình, {@code Stage} riêng, chặn phím gameplay khi mở.
 *
 * <p>Widget chỉ VẼ (UI.md §4): nhận các dòng ĐÃ dịch sẵn, báo ra đúng hai ý định — "vừa mở / bấm Làm
 * mới" và "vừa chọn tab số i". Không biết opcode nào.
 */
public final class ReportPanel implements HudWindow {

    private static final float PANEL_WIDTH_PX = 520f;
    private static final float PANEL_PAD_PX = 16f;
    private static final float ROW_PAD_PX = 3f;
    private static final float BUTTON_MIN_WIDTH_PX = 96f;
    private static final float BUTTON_TEXT_PAD_PX = 14f;
    private static final float LIST_MAX_HEIGHT_PX = 364f;

    private static final Color SECTION_COLOR = Color.valueOf("E0B860");
    private static final Color ROW_COLOR = Color.valueOf("F2F2F2");
    /** Dòng được nhấn mạnh (chính mình trên bảng xếp hạng) — kèm tiền tố chữ, không chỉ dựa vào màu. */
    private static final Color HIGHLIGHT_COLOR = Color.valueOf("8FE08F");

    /**
     * Một dòng.
     *
     * @param text chuỗi ĐÃ dịch
     * @param highlighted vẽ nổi bật (vd dòng của chính mình) — người gọi tự thêm dấu chữ nếu cần
     */
    public record Row(String text, boolean highlighted) {}

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final int openKey;
    private final Table panel;
    private final Label statusLabel;
    private final Table rowList;
    private final Table tabRow = new Table();
    private final List<String> tabKeys;

    private Runnable onRefresh;
    private IntConsumer onTabSelected = index -> {};
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — KHÔNG dispose
     * @param titleKey khoá i18n tiêu đề
     * @param openKey phím mở ({@code Input.Keys}) — phải chưa có chủ (xem {@link QuestLogPanel#OPEN_KEY})
     * @param tabKeys khoá i18n của từng tab; rỗng = không có hàng tab
     */
    public ReportPanel(KageSkin skin, String titleKey, int openKey, List<String> tabKeys) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.openKey = openKey;
        this.tabKeys = List.copyOf(tabKeys);
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());
        this.statusLabel = new Label("", skin.labelStyle);
        statusLabel.setWrap(true);
        statusLabel.setColor(SECTION_COLOR);
        this.rowList = new Table();

        Label title = new Label(text(titleKey), skin.labelStyle);
        title.setColor(SECTION_COLOR);
        this.panel = new Table();
        panel.setBackground(skin.panel);
        panel.pad(PANEL_PAD_PX);
        panel.add(title).width(PANEL_WIDTH_PX).left().row();
        if (!this.tabKeys.isEmpty()) {
            for (int i = 0; i < this.tabKeys.size(); i++) {
                int index = i;
                tabRow.add(button(this.tabKeys.get(i), () -> onTabSelected.accept(index)))
                        .minWidth(BUTTON_MIN_WIDTH_PX)
                        .padRight(6f);
            }
            panel.add(tabRow).left().padTop(6f).row();
        }
        panel.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        panel.add(ScrollingList.wrap(rowList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(LIST_MAX_HEIGHT_PX)
                .growX()
                .padTop(10f)
                .row();
        panel.add(footer()).width(PANEL_WIDTH_PX).left().padTop(8f).row();
        panel.setVisible(false);

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.center).add(panel);
        stage.addActor(container);
        installKeyHandling();
    }

    private Table footer() {
        Table row = new Table();
        Label hint = new Label(text("questlog.hint.close"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        row.add(hint).left().expandX().padRight(6f);
        row.add(button("questlog.action.refresh", () -> fire(onRefresh))).minWidth(BUTTON_MIN_WIDTH_PX);
        return row;
    }

    private TextButton button(String labelKey, Runnable action) {
        TextButton button = new TextButton(text(labelKey), skin.buttonStyle);
        button.padLeft(BUTTON_TEXT_PAD_PX).padRight(BUTTON_TEXT_PAD_PX);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    private static void fire(Runnable listener) {
        if (listener != null) {
            listener.run();
        }
    }

    /** Cùng cơ chế phím của {@link QuestLogPanel#installKeyHandling}: mở ở {@code keyUp}, ESC đóng. */
    private void installKeyHandling() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (isOpen()) {
                    return true;
                }
                if (keycode != openKey || !canOpen.getAsBoolean()) {
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

    public boolean isOpen() {
        return panel.isVisible();
    }

    /** Mở và báo ra ngoài để hỏi lại server — số trên các bảng này luôn có thể đã cũ. */
    public void open() {
        panel.setVisible(true);
        fire(onRefresh);
    }

    public void close() {
        panel.setVisible(false);
    }

    public void setOnRefresh(Runnable listener) {
        this.onRefresh = listener;
    }

    public void setOnTabSelected(IntConsumer listener) {
        this.onTabSelected = listener;
    }

    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Dòng trạng thái dưới tiêu đề/tab — chuỗi ĐÃ dịch. */
    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    /** Vẽ lại danh sách — chỉ gọi khi server vừa trả lời, không mỗi khung. */
    public void setRows(List<Row> rows) {
        rowList.clear();
        for (Row row : rows) {
            Label label = new Label(row.text(), skin.labelStyle);
            label.setColor(row.highlighted() ? HIGHLIGHT_COLOR : ROW_COLOR);
            label.setEllipsis(true);
            rowList.add(label)
                    .width(PANEL_WIDTH_PX - 24f)
                    .left()
                    .padBottom(ROW_PAD_PX)
                    .row();
        }
    }

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

    /** KHÔNG dispose {@link KageSkin} nhận ở constructor — của {@link CombatHud}. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
