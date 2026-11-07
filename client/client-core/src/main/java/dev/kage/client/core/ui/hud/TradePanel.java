package dev.kage.client.core.ui.hud;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.input.KeyBindings;
import dev.kage.client.core.render.FontFactory;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Cửa sổ giao dịch (trade.proto 1400-1414) — mời theo tên, lời mời đang chờ, và hai lời chào đặt
 * cạnh nhau kèm cờ xác nhận của từng bên. Mở bằng phím N, neo GIỮA màn hình như {@link DuelPanel}.
 *
 * <p>N vì T (chat), I (túi đồ), B (cửa hàng), J (nhiệm vụ), M (thư), P (nhóm), F (bạn), G (bang),
 * K (đấu), H (hệ phái) đã có chủ, còn A/D/W/Z/Enter là phím gameplay ({@code DesktopInputMapper}).
 *
 * <p><b>Hai lời chào phải nhìn thấy CÙNG LÚC.</b> Đây là "hệ thống nguy hiểm nhất" (GAMEPLAY.md §8):
 * trò lừa kinh điển là đổi lời chào ngay trước khi đối phương bấm xác nhận. Server đã chặn bằng cách
 * reset cả hai cờ confirmed mỗi lần lời chào đổi, nhưng người chơi chỉ tin được điều đó nếu họ THẤY
 * cờ của cả hai bên rơi về chưa-xác-nhận. Vì vậy cờ confirmed hiện thành chữ ngay cạnh mỗi lời chào,
 * không phải một dấu nhỏ ở đâu đó.
 *
 * <p>Widget chỉ VẼ (UI.md §4): không giữ {@code session_id}, không biết opcode nào, không tự quyết
 * bấm được hay không. Nó nhận các dòng ĐÃ ghép sẵn thành chuỗi và báo ra ý định vừa bấm — trừ ô nhập
 * tên và ô nhập vàng, hai chỗ duy nhất chuỗi đi RA từ đây.
 */
public final class TradePanel implements HudWindow {

    /**
     * Phím mở cửa sổ. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với phím T của {@link ChatPanel} và K của {@link DuelPanel}.
     */
    private static final int OPEN_KEY = Input.Keys.N;

    /**
     * Chiều cao TỐI ĐA của vùng cuộn vật phẩm của MỖI bên (≈ 3 dòng chữ); dài hơn thì cuộn. Số dòng
     * được DỰNG là {@link ScrollingList#MAX_BUILT_ROWS} — trên sức chứa túi 20 ô, trần thật của một
     * lời chào hợp lệ. Server KHÔNG đếm số món trong lời chào ({@code SetTradeOfferUseCase} lưu
     * nguyên danh sách, chỉ xác thực lúc thi hành), nên phía client vẫn phải có một trần dựng dòng.
     *
     * <p>⚠️ Trước 2026-09-11 là trần {@code MAX_VISIBLE_ITEMS = 6} "cùng lý do trần của MailPanel" —
     * tức lý do "chưa có ScrollPane" đã chết. Ở cửa sổ này nó tệ hơn mọi chỗ khác: đây là hệ thống
     * nguy hiểm nhất (GAMEPLAY.md §8), người chơi phải thấy ĐỦ đối phương chào gì trước khi bấm xác
     * nhận, mà món thứ 7 trở đi chỉ hiện thành "còn N món nữa". Trần đó cũng không giữ được khung
     * trong màn hình: hai bên 6 món + lời mời đang chờ cao <b>886px</b> / 720px
     * ({@code HudLayoutBudgetTool}).
     */
    private static final float ITEM_LIST_MAX_HEIGHT_PX = 84f;

    private static final float PANEL_WIDTH_PX = 520f;

    private static final float PANEL_PAD_PX = 16f;

    /**
     * Nút hành động. 104 → 116 (2026-10-01): "Chấp nhận" cần ~113px kể cả lề — đo bằng
     * {@code HudButtonLabelFitTest}, không bằng mắt.
     */
    static final float ACTION_BUTTON_WIDTH_PX = 116f;

    /**
     * Nút đặt linh thạch RIÊNG một bề rộng: "Đặt linh thạch" (~147px) và "Set spirit stones" (~162px)
     * đều tràn hẳn khỏi nút 104px cũ. Hàng phiên: ô 96 + 166 + 116 + 116 + 3 khe 6 = 512 ≤ 520.
     */
    static final float GOLD_BUTTON_WIDTH_PX = 166f;

    private static final float GOLD_FIELD_WIDTH_PX = 96f;

    private static final int NAME_INPUT_MAX_LENGTH = 16;

    private static final int GOLD_INPUT_MAX_LENGTH = 12;

    private static final Color SECTION_COLOR = new Color(0.85f, 0.72f, 0.36f, 1f);

    private static final Color ROW_COLOR = new Color(0.92f, 0.92f, 0.92f, 1f);

    private final KageSkin skin;
    private final I18NBundle i18n;
    private final Stage stage;
    private final Table panel;

    private final Label statusLabel;
    private final Label inviteLabel;
    private final Label counterpartLabel;
    private final Label myOfferLabel;
    private final Label theirOfferLabel;
    private final Table myItemList;
    private final Table theirItemList;

    private final TextField nameInput;
    private final TextField goldInput;

    private final Table inviteRow;
    private final Table sessionActionRow;

    private Consumer<String> onRequest = name -> {};
    private Runnable onAcceptInvite = () -> {};
    private Runnable onDeclineInvite = () -> {};
    private Consumer<String> onSetGold = gold -> {};
    private Runnable onConfirm = () -> {};
    private Runnable onCancel = () -> {};
    private Runnable onRefresh = () -> {};
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@code CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link DuelPanel}.
     */
    public TradePanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.statusLabel = wrappingLabel(SECTION_COLOR);
        this.inviteLabel = wrappingLabel(ROW_COLOR);
        this.counterpartLabel = wrappingLabel(ROW_COLOR);
        this.myOfferLabel = wrappingLabel(ROW_COLOR);
        this.theirOfferLabel = wrappingLabel(ROW_COLOR);
        this.myItemList = new Table();
        this.theirItemList = new Table();

        this.nameInput = typedField("trade.input.name", NAME_INPUT_MAX_LENGTH, this::submitTypedName);
        this.goldInput = typedField("trade.input.gold", GOLD_INPUT_MAX_LENGTH, this::submitTypedGold);

        this.inviteRow = buildInviteRow();
        this.sessionActionRow = buildSessionActionRow();
        this.panel = buildPanel();
        // Nuốt click rơi vào NỀN cửa sổ: không nuốt thì cú click đó rơi tiếp xuống tầng thế giới và
        // đổi mục tiêu đánh sau lưng cửa sổ đang mở — cùng cách DuelPanel/FriendPanel đã chặn.
        panel.addListener(new ClickListener());

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.center).add(panel);
        stage.addActor(container);
        installKeyHandling();
    }

    private Label wrappingLabel(Color color) {
        Label label = new Label("", skin.labelStyle);
        label.setWrap(true);
        label.setColor(color);
        return label;
    }

    /**
     * Ô nhập chỉ nhận ký tự font VẼ ĐƯỢC — nếu không, ký tự vừa gõ BIẾN MẤT ngay trong ô nhập của
     * chính mình mà không có báo lỗi nào (xem {@link FontFactory#canRender}).
     */
    private TextField typedField(String hintKey, int maxLength, Runnable onEnter) {
        TextField field = new TextField("", skin.textFieldStyle);
        field.setMessageText(text(hintKey));
        field.setTextFieldFilter((widget, character) -> FontFactory.canRender(character));
        field.setMaxLength(maxLength);
        field.setTextFieldListener((widget, character) -> {
            if (character == '\r' || character == '\n') {
                onEnter.run();
            }
        });
        return field;
    }

    private Table buildPanel() {
        Table built = new Table();
        built.setBackground(skin.panel);
        built.pad(PANEL_PAD_PX);
        built.add(sectionTitle("trade.title")).width(PANEL_WIDTH_PX).left().row();
        built.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        built.add(buildRequestRow()).width(PANEL_WIDTH_PX).growX().padTop(10f).row();
        built.add(inviteRow).width(PANEL_WIDTH_PX).growX().padTop(6f).row();
        built.add(counterpartLabel).width(PANEL_WIDTH_PX).left().padTop(10f).row();
        built.add(sectionTitle("trade.section.my_offer"))
                .width(PANEL_WIDTH_PX)
                .left()
                .padTop(8f)
                .row();
        built.add(myOfferLabel).width(PANEL_WIDTH_PX).left().row();
        built.add(ScrollingList.wrap(myItemList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(ITEM_LIST_MAX_HEIGHT_PX)
                .growX()
                .row();
        built.add(sectionTitle("trade.section.their_offer"))
                .width(PANEL_WIDTH_PX)
                .left()
                .padTop(8f)
                .row();
        built.add(theirOfferLabel).width(PANEL_WIDTH_PX).left().row();
        built.add(ScrollingList.wrap(theirItemList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(ITEM_LIST_MAX_HEIGHT_PX)
                .growX()
                .row();
        built.add(sessionActionRow).width(PANEL_WIDTH_PX).growX().padTop(10f).row();
        built.add(closeHint()).width(PANEL_WIDTH_PX).left().padTop(8f).row();
        built.setVisible(false);
        return built;
    }

    private Table buildRequestRow() {
        Table row = new Table();
        row.add(nameInput).growX().padRight(6f);
        row.add(actionButton("trade.action.request", this::submitTypedName)).width(ACTION_BUTTON_WIDTH_PX);
        return row;
    }

    private Table buildInviteRow() {
        Table row = new Table();
        row.add(inviteLabel).growX().padRight(6f);
        row.add(actionButton("trade.action.accept", () -> fire(onAcceptInvite)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padRight(6f);
        row.add(actionButton("trade.action.decline", () -> fire(onDeclineInvite)))
                .width(ACTION_BUTTON_WIDTH_PX);
        row.setVisible(false);
        return row;
    }

    private Table buildSessionActionRow() {
        Table row = new Table();
        row.add(goldInput).width(GOLD_FIELD_WIDTH_PX).padRight(6f);
        row.add(actionButton("trade.action.set_gold", this::submitTypedGold))
                .width(GOLD_BUTTON_WIDTH_PX)
                .padRight(6f);
        row.add(actionButton("trade.action.confirm", () -> fire(onConfirm)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padRight(6f);
        row.add(actionButton("trade.action.cancel", () -> fire(onCancel))).width(ACTION_BUTTON_WIDTH_PX);
        row.setVisible(false);
        return row;
    }

    private Table closeHint() {
        Table row = new Table();
        Label hint = new Label(text("trade.hint.close"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        row.add(hint).left().expandX().padRight(6f);
        row.add(actionButton("trade.action.refresh", () -> fire(onRefresh))).width(ACTION_BUTTON_WIDTH_PX);
        return row;
    }

    private Label sectionTitle(String key) {
        Label label = new Label(text(key), skin.labelStyle);
        label.setColor(SECTION_COLOR);
        return label;
    }

    private TextButton actionButton(String labelKey, Runnable action) {
        TextButton button = new TextButton(text(labelKey), skin.buttonStyle);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
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

    private void submitTypedName() {
        String typed = nameInput.getText().trim();
        if (!typed.isEmpty()) {
            onRequest.accept(typed);
            nameInput.setText("");
        }
    }

    private void submitTypedGold() {
        onSetGold.accept(goldInput.getText());
    }

    /**
     * Mở ở {@code keyUp} chứ không {@code keyDown}; N chỉ MỞ chứ không bật/tắt; đang mở thì NUỐT mọi
     * {@code keyUp} để chữ người chơi gõ trong ô tên không rơi xuống panel khác và bật cửa sổ của nó
     * — cùng khuôn {@link MailPanel}/{@link ShopPanel}.
     */
    private void installKeyHandling() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (isOpen()) {
                    return true;
                }
                if (keycode != KeyBindings.shared().resolve(OPEN_KEY) || !canOpen.getAsBoolean()) {
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
     * Đang mở hay không. {@code PanelHub} BẮT BUỘC hỏi câu này trước khi đọc phím gameplay — xem
     * javadoc {@link MailPanel#isOpen()} về lý do (phím di chuyển đi vòng qua lưng
     * {@code InputProcessor}).
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    /** Mở khung và báo ra ngoài để hỏi lại trạng thái phiên. */
    public void open() {
        if (panel.isVisible()) {
            // Mở lại khi ĐANG mở là không làm gì — cùng bộ chặn của ShopPanel#open, và cùng lý do:
            // onRefresh gửi gói, mà gói trả lời lại vẽ vào chính cửa sổ này.
            return;
        }
        panel.setVisible(true);
        fire(onRefresh);
    }

    public void close() {
        panel.setVisible(false);
        // Bỏ focus THẬT: ô nhập còn giữ keyboard focus là còn nuốt phím dù đã ẩn đi.
        stage.setKeyboardFocus(null);
    }

    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Người chơi gõ tên rồi bấm Mời (hoặc Enter). */
    public void setOnRequest(Consumer<String> listener) {
        this.onRequest = listener;
    }

    public void setOnAcceptInvite(Runnable listener) {
        this.onAcceptInvite = listener;
    }

    public void setOnDeclineInvite(Runnable listener) {
        this.onDeclineInvite = listener;
    }

    /** Người chơi gõ số vàng rồi bấm Đặt vàng — chuỗi THÔ, chỗ nghe tự kiểm. */
    public void setOnSetGold(Consumer<String> listener) {
        this.onSetGold = listener;
    }

    public void setOnConfirm(Runnable listener) {
        this.onConfirm = listener;
    }

    public void setOnCancel(Runnable listener) {
        this.onCancel = listener;
    }

    /** Mở cửa sổ hoặc bấm Làm mới — chỗ nghe có nhiệm vụ hỏi lại server. */
    public void setOnRefresh(Runnable listener) {
        this.onRefresh = listener;
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * Lời mời đang chờ trả lời.
     *
     * @param inviteText chuỗi ĐÃ dịch, hoặc rỗng để ẩn cả hàng (không có lời mời nào)
     */
    public void setInvite(String inviteText) {
        inviteLabel.setText(inviteText);
        inviteRow.setVisible(!inviteText.isEmpty());
    }

    /**
     * Toàn bộ khung nhìn của một phiên đang mở.
     *
     * @param counterpartText tên đối phương + giai đoạn, ĐÃ ghép sẵn; rỗng = không ở phiên nào
     * @param myOfferText vàng mình chào + cờ đã xác nhận hay chưa, ĐÃ ghép sẵn
     * @param theirOfferText vàng đối phương chào + cờ của họ, ĐÃ ghép sẵn
     */
    public void setSession(String counterpartText, String myOfferText, String theirOfferText) {
        counterpartLabel.setText(counterpartText);
        myOfferLabel.setText(myOfferText);
        theirOfferLabel.setText(theirOfferText);
        sessionActionRow.setVisible(!counterpartText.isEmpty());
    }

    /**
     * Danh sách vật phẩm của một bên.
     *
     * @param rows chuỗi ĐÃ dịch cho từng món
     * @param overflowNotice chữ báo "còn N món nữa" khi vượt {@link ScrollingList#MAX_BUILT_ROWS},
     *     rỗng nếu không có
     * @param mine {@code true} thì vẽ vào cột của mình, {@code false} thì cột đối phương
     */
    public void setItems(List<String> rows, String overflowNotice, boolean mine) {
        Table target = mine ? myItemList : theirItemList;
        target.clear();
        // growX() chứ không width(PANEL_WIDTH_PX): danh sách nằm trong vùng cuộn khoá trục ngang,
        // thanh cuộn ăn mất mép phải — dòng rộng bằng khung sẽ bị cắt chữ ở đó (xem ScrollingList#wrap).
        for (String row : rows) {
            Label label = wrappingLabel(ROW_COLOR);
            label.setText(row);
            target.add(label).growX().left().row();
        }
        if (!overflowNotice.isEmpty()) {
            Label notice = wrappingLabel(SECTION_COLOR);
            notice.setText(overflowNotice);
            target.add(notice).growX().left().row();
        }
    }

    private String text(String key) {
        return i18n.get(key);
    }

    /**
     * Chiều cao khung MUỐN có ở trạng thái hiện tại — chỉ dùng cho {@code HudLayoutBudgetTool}, cùng
     * lý do {@link InventoryPanel#preferredHeight()}.
     */
    float preferredHeight() {
        return panel.getPrefHeight();
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

    @Override
    public void dispose() {
        // Stage KHÔNG tự dispose batch truyền vào constructor (ownsBatch=false) — thiếu dòng này là rò
        // một SpriteBatch (mesh + shader) mỗi lần dựng lại màn world (InboundPacketFuzzTest).
        stage.getBatch().dispose();
        stage.dispose();
    }
}
