package dev.kage.client.core.ui.hud;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Cửa hàng (2026-08-29) — danh mục vật phẩm mua bán được, mở bằng phím B HOẶC từ hội thoại NPC
 * ("Cho tôi xem hàng"), neo GIỮA màn hình. Cùng hình dạng {@link MailPanel} — đọc javadoc lớp đó
 * trước khi sửa gì ở đây, mọi lý do kỹ thuật (Stage riêng, chặn phím gameplay, widget chỉ vẽ) đều
 * lặp lại y hệt.
 *
 * <h2>⚠️ Giới hạn CÓ THẬT của cửa sổ này, không được che giấu</h2>
 *
 * <ul>
 *   <li><b>KHÔNG có giao diện Bán.</b> {@code SellItemCommand} tham chiếu {@code inventory_item.id}
 *       THẬT (shop.proto), thứ chỉ có ý nghĩa khi đứng cạnh một danh sách túi đồ — và giao diện túi
 *       đồ đang được một luồng KHÁC dựng song song với lát cắt này. Đường gửi lệnh Bán + xử lý kết
 *       quả + khoá i18n cho MỌI {@code SellOutcome} đã có đủ ở {@link dev.kage.client.core.world.ShopCoordinator}
 *       ({@code sellItem}), chỉ CHƯA có nút nào ở đây gọi tới nó. Nối nút Bán vào giao diện túi đồ là
 *       việc còn lại sau khi hai luồng gộp — xem javadoc {@code ShopCoordinator}.
 *   <li><b>Một ô số lượng DÙNG CHUNG cho mọi dòng</b>, không phải một ô riêng từng dòng — đổi số
 *       lượng rồi bấm Mua ở dòng nào thì dòng đó dùng đúng số lượng đang gõ. Đơn giản hơn một ô mỗi
 *       dòng, và đúng cách người chơi thực tế dùng (đổi số lượng rồi mua liên tiếp vài món cùng số).
 * </ul>
 */
public final class ShopPanel implements HudWindow {

    /**
     * Phím mở khung. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với phím M ({@link MailPanel}). B ("bazaar"/"buy") vì T/P/F/G/K/H/M đã có chủ.
     */
    private static final int OPEN_KEY = Input.Keys.B;

    private static final float PANEL_WIDTH_PX = 560f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float ROW_PAD_PX = 4f;

    /** Bề rộng CỐ ĐỊNH cho nút Mua trên từng dòng — nhãn dài ngắn khác nhau không làm cột nhảy. */
    static final float ROW_BUTTON_WIDTH_PX = 84f;

    /** Bề rộng cột chữ của một dòng danh mục — chừa đúng chỗ cho nút Mua bên phải. */
    private static final float ROW_TEXT_WIDTH_PX = 400f;

    /** Ô số lượng hẹp hơn hẳn — nó chứa một con số nhỏ, không chứa câu. */
    private static final float QUANTITY_FIELD_WIDTH_PX = 80f;

    /** Khớp {@code ShopController.MAX_QUANTITY} (999) — 3 chữ số là đủ. */
    private static final int QUANTITY_MAX_LENGTH = 3;

    /** Khớp {@code ShopController.MIN_QUANTITY} — giá trị ban đầu của ô số lượng. */
    private static final int DEFAULT_QUANTITY = 1;

    /**
     * Chiều cao TỐI ĐA của vùng cuộn danh mục (≈ 7 dòng 52px). Danh mục dài hơn thì cuộn — số dòng
     * được DỰNG là {@link ScrollingList#MAX_BUILT_ROWS}, không phải con số này.
     *
     * <p>⚠️ Trước 2026-09-11 chỗ này là trần {@code MAX_VISIBLE_ROWS = 8} với lý do "{@code KageSkin}
     * chưa có style {@code ScrollPane}" — lý do đã chết, và trần đó là một LỖI THẬT: server bán 19
     * món ({@code shop_item}, sắp theo {@code item_template_id}) nên 11 món cuối không bao giờ được
     * dựng, không mua được bằng cách nào — kể cả {@code minor_healing_pill} (V36), món dùng được duy
     * nhất của game. Xem {@link ScrollingList}.
     *
     * <p>Đo bằng {@code HudLayoutBudgetTool} ở trạng thái tệ nhất (danh mục đầy, dòng trạng thái
     * dài): con số ghi ở javadoc lớp công cụ đó sau mỗi lần chỉnh.
     */
    private static final float LISTING_MAX_HEIGHT_PX = 364f;

    /** Vàng đồng nhạt — tiêu đề mục, cùng tông viền HUD của {@link KageSkin}. */
    private static final Color SECTION_COLOR = Color.valueOf("E0B860");

    /** Trắng ngà — dòng dữ liệu thường. */
    private static final Color ROW_COLOR = Color.valueOf("F2F2F2");

    /** Vàng sáng — số vàng hiện có, cùng tông với chữ "vàng" trong hòm thư. */
    private static final Color GOLD_COLOR = Color.valueOf("F2D675");

    /**
     * Một dòng danh mục.
     *
     * @param itemTemplateId gửi thẳng lại cho server ở lệnh Mua ({@code BuyItemCommand}).
     * @param displayText chuỗi ĐÃ ghép sẵn (tên qua catalog hoặc dạng số + giá mua + giá bán), panel
     *     không tự ghép — UI.md §4.
     */
    public record ShopRow(long itemTemplateId, String displayText) {}

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final Table panel;
    private final Label statusLabel;
    private final Label goldLabel;
    private final Table listingTable;
    private final TextField quantityField;

    private LongConsumer onBuy;
    private Consumer<String> onQuantityChanged;
    private Runnable onOpened;

    /**
     * Có được phép mở bằng phím B lúc này không. Mặc định là LUÔN được — panel không biết trên màn
     * hình còn khung nào khác, xem {@link #setCanOpen}.
     */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@code CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link MailPanel}.
     */
    public ShopPanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.statusLabel = wrappingLabel(SECTION_COLOR);
        this.goldLabel = new Label("", skin.labelStyle);
        goldLabel.setColor(GOLD_COLOR);

        this.listingTable = new Table();
        this.quantityField = digitsField();

        this.panel = new Table();
        panel.setBackground(skin.panel);
        panel.pad(PANEL_PAD_PX);
        panel.add(titleRow()).width(PANEL_WIDTH_PX).left().row();
        panel.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        panel.add(quantityRow()).width(PANEL_WIDTH_PX).left().padTop(10f).row();
        panel.add(sectionTitle("shop.section.listings"))
                .width(PANEL_WIDTH_PX)
                .left()
                .padTop(10f)
                .row();
        panel.add(ScrollingList.wrap(listingTable, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(LISTING_MAX_HEIGHT_PX)
                .growX()
                .row();
        panel.add(closeHint()).width(PANEL_WIDTH_PX).left().padTop(8f).row();
        panel.setVisible(false);

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

    /** Tiêu đề cửa sổ + số vàng hiện có ngay cạnh — xem {@link #setGoldText}. */
    private Table titleRow() {
        Table row = new Table();
        Label title = new Label(text("shop.title"), skin.labelStyle);
        title.setColor(SECTION_COLOR);
        row.add(title).left().padRight(10f);
        row.add(goldLabel).left().expandX();
        return row;
    }

    /** Ô số lượng DÙNG CHUNG cho mọi dòng — xem javadoc lớp. */
    private Table quantityRow() {
        Table row = new Table();
        Label hint = new Label(text("shop.label.quantity"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        row.add(hint).left().padRight(6f);
        row.add(quantityField).width(QUANTITY_FIELD_WIDTH_PX).left();
        return row;
    }

    /**
     * Ô nhập số lượng — chỉ nhận chữ SỐ, cùng lý do {@code MailPanel#digitsField} chặn dấu trừ ngay ở
     * tầng gõ phím: {@code ShopController} vẫn lọc lại (Điều 24 kiểu client, luật thật ở server), đây
     * chỉ là hàng rào đầu tiên để người chơi không vấp vào một ô số hiện ra chữ.
     */
    private TextField digitsField() {
        TextField field = new TextField(Integer.toString(DEFAULT_QUANTITY), skin.textFieldStyle);
        field.setTextFieldFilter((widget, character) -> Character.isDigit(character));
        field.setMaxLength(QUANTITY_MAX_LENGTH);
        field.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (onQuantityChanged != null) {
                    onQuantityChanged.accept(field.getText());
                }
            }
        });
        return field;
    }

    /**
     * Cách ĐÓNG phải nằm ngay trên cửa sổ — cửa sổ che giữa màn hình và chặn toàn bộ phím gameplay,
     * cùng lý do {@link MailPanel#closeHint}.
     */
    private Table closeHint() {
        Table row = new Table();
        Label hint = new Label(text("shop.hint.close"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        row.add(hint).left().expandX();
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
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /**
     * Phím B mở khung, ESC đóng — cùng cơ chế và cùng cái bẫy đã mô tả ở {@link MailPanel} (mở ở
     * {@code keyUp} chứ không {@code keyDown}; B chỉ MỞ chứ không bật/tắt; đang mở thì NUỐT mọi
     * {@code keyUp} để chữ số người chơi gõ trong ô số lượng không rơi xuống panel khác và bật cửa sổ
     * ở đó).
     */
    private void installKeyHandling() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (isOpen()) {
                    return true; // xem javadoc: chặn phím rơi xuống panel khác trong lúc đang gõ
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
     * Đang mở hay không. {@code WorldScreen}/{@code PanelHub} BẮT BUỘC hỏi câu này trước khi đọc phím
     * gameplay — xem javadoc {@link MailPanel#isOpen()} về lý do (phím di chuyển đi vòng qua lưng
     * {@code InputProcessor}).
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    /**
     * Mở khung và báo ra ngoài để hỏi lại server.
     *
     * <p>Gọi CẢ khi khung được mở từ hội thoại NPC (server đã trả danh mục sẵn) lẫn khi người chơi tự
     * bấm B — hỏi lại lần nữa trong trường hợp đầu là vô hại (server chỉ trả đúng cùng danh mục), và
     * giữ đúng MỘT đường mở duy nhất thay vì hai đường có hành vi khác nhau.
     */
    public void open() {
        if (panel.isVisible()) {
            // Mở lại khi ĐANG mở là không làm gì. Không phải cho gọn: onOpened hỏi lại danh mục,
            // và gói trả lời lại gọi showListings() — nếu đường đó quay về open() thì thành vòng
            // lặp vô tận vừa bắn gói liên tục lên server vừa khiến ESC không bao giờ đóng được cửa
            // sổ (gói kế tiếp mở lại ngay). Đã xảy ra THẬT, xem showListings().
            return;
        }
        panel.setVisible(true);
        if (onOpened != null) {
            onOpened.run();
        }
    }

    /**
     * Hiện cửa sổ vì danh mục VỪA VỀ từ server — khác {@link #open()} ở chỗ KHÔNG hỏi lại danh mục.
     *
     * <p>Đây là lối vào của đường mở từ hội thoại NPC: người chơi bấm "Cho tôi xem hàng", server tự
     * gửi {@code ListShopItemsResponse} mà client chưa hỏi. Dùng {@link #open()} ở đó là tự hỏi
     * thêm một vòng thừa, và trước khi có bộ chặn trong {@code open()} thì đó là một vòng lặp vô
     * tận thật.
     */
    public void showListings() {
        panel.setVisible(true);
    }

    public void close() {
        panel.setVisible(false);
        // Bỏ focus THẬT: ô nhập số lượng còn giữ keyboard focus là còn nuốt phím dù đã ẩn đi.
        stage.setKeyboardFocus(null);
    }

    /** Gọi mỗi lần khung mở ra — chỗ nghe có nhiệm vụ hỏi lại server, xem {@link #open()}. */
    public void setOnOpened(Runnable listener) {
        this.onOpened = listener;
    }

    /**
     * Điều kiện để phím B được phép mở khung — cổng phím CHUNG của mọi cửa sổ, xem
     * {@code PanelHub#noneOpen}. Panel không tự biết trên màn hình còn ô nhập nào khác đang mở (nó
     * không được biết — UI.md §4), nên {@code WorldScreen} là chỗ cung cấp câu trả lời.
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Id vật phẩm của dòng người chơi vừa bấm Mua. */
    public void setOnBuy(LongConsumer listener) {
        this.onBuy = listener;
    }

    /** Chuỗi thô đang nằm trong ô số lượng — lọc/chuẩn hoá là việc của {@code ShopController}. */
    public void setOnQuantityChanged(Consumer<String> listener) {
        this.onQuantityChanged = listener;
    }

    /** Dòng trạng thái dưới tiêu đề — chuỗi ĐÃ dịch (khoá i18n tra ở coordinator, chỗ biết outcome). */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /** Số vàng hiện có — chuỗi ĐÃ ghép sẵn ("Vàng: 1.234" hoặc "Vàng: —" khi chưa nhận gói nào). */
    public void setGoldText(String text) {
        goldLabel.setText(text);
    }

    /**
     * Vẽ lại danh mục. Dựng lại toàn bộ dòng mỗi lần gọi — danh mục chỉ đổi khi server vừa trả lời
     * hoặc catalog tên vật phẩm vừa cập nhật (vài lần một phiên chơi), không phải mỗi khung, nên đây
     * không nằm trong đường cấm cấp phát của game loop (Điều 31); cùng lập luận
     * {@link MailPanel#setMails}.
     *
     * @param rows đã cắt xuống số dòng hiển thị được ở coordinator.
     * @param overflowNoticeOrEmpty chuỗi đã dịch cho danh mục rỗng hoặc phần bị cắt bớt; rỗng nếu
     *     không có gì phải nói thêm.
     */
    public void setListings(List<ShopRow> rows, String overflowNoticeOrEmpty) {
        listingTable.clear();
        for (ShopRow row : rows) {
            addRow(row);
        }
        if (!overflowNoticeOrEmpty.isEmpty()) {
            listingTable
                    .add(rowLabel(overflowNoticeOrEmpty))
                    .colspan(2)
                    .growX()
                    .left()
                    .row();
        }
    }

    private void addRow(ShopRow row) {
        Label label = rowLabel(row.displayText());
        label.setEllipsis(true);
        listingTable.add(label).width(ROW_TEXT_WIDTH_PX).left().padRight(6f).padBottom(ROW_PAD_PX);
        listingTable
                .add(actionButton("shop.action.buy", () -> emit(onBuy, row.itemTemplateId())))
                .width(ROW_BUTTON_WIDTH_PX)
                .padBottom(ROW_PAD_PX);
        listingTable.row();
    }

    private static void emit(LongConsumer listener, long itemTemplateId) {
        if (listener != null) {
            listener.accept(itemTemplateId);
        }
    }

    private Label rowLabel(String displayText) {
        Label label = new Label(displayText, skin.labelStyle);
        label.setColor(ROW_COLOR);
        return label;
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
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

    /** KHÔNG dispose {@link KageSkin} nhận ở constructor — của {@code CombatHud}, xem javadoc đó. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
