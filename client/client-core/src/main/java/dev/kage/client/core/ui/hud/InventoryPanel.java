package dev.kage.client.core.ui.hud;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Túi đồ + trang bị (ADR-035, opcode 501-504/507-508) — mở bằng phím I, neo GIỮA màn hình.
 *
 * <p>Giữa màn hình chứ không phải một góc, cùng lập luận {@link MailPanel}/{@link GuildPanel}: đây
 * là cửa sổ quản trị, mở ra là để làm việc với nó — chặn toàn bộ phím gameplay khi mở
 * (xem {@link #isOpen()}).
 *
 * <p>{@code Stage} RIÊNG + phím mở/đóng/nuốt input cùng cơ chế {@link MailPanel} hệt từng chữ — xem
 * javadoc ở đó về lý do và về cái bẫy "gõ chữ trong ô nhập panel khác vô tình mở panel này".
 *
 * <p>Widget chỉ VẼ (UI.md §4): nó không biết opcode, không dựng packet, không tra catalog vật phẩm.
 * Nó nhận các dòng ĐÃ ghép sẵn thành chuỗi hiển thị (tên vật phẩm, số lượng, mức cường hoá) và báo ra
 * ý định người chơi vừa bấm (chọn item / mặc / gỡ). Vì cùng lý do đó, nó KHÔNG import bất kỳ kiểu
 * protobuf nào — slot trang bị đi qua như một {@code int} trần ({@code EquipmentSlot.getNumber()}),
 * {@link InventoryCoordinator} phía {@code world} mới là chỗ biết ý nghĩa con số đó.
 *
 * <h2>Icon vật phẩm (ADR-028)</h2>
 *
 * Tra theo quy ước tên vùng atlas {@code icon_<code>} (RESOUCE.md §4: {@code icon_kunai.png} ↔
 * {@code item_template.code = 'kunai'}), atlas MEDIUM tier — cùng {@code SkillVfxRenderer}. Bản
 * checkout chưa chạy pipeline art (chưa có {@code atlases/med/main.atlas}) hoặc vùng chưa được vẽ
 * thì để TRỐNG, KHÔNG vẽ hộp màu bịa (Điều 24 — không đoán thay server/art).
 */
public final class InventoryPanel implements HudWindow {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryPanel.class);

    /**
     * Phím mở khung. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với phím M ({@link MailPanel}). I ("inventory") vì T/P/F/G/K/H/M đã có chủ.
     */
    private static final int OPEN_KEY = Input.Keys.I;

    private static final float PANEL_WIDTH_PX = 560f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float ROW_PAD_PX = 4f;

    /**
     * Nút trên từng dòng. 84 → 88 (2026-10-01): "Unequip" cần ~87px kể cả lề ({@code
     * HudButtonLabelFitTest}). Hàng hành động: 2 × 88 + 2 × 104 + 150 + 4 khe 6 = 558 ≤ 560.
     */
    static final float ROW_BUTTON_WIDTH_PX = 88f;

    /**
     * Bề rộng cột tên ô trang bị ("Pháp khí", "Ngọc bội 1"...). Cố định để cột mô tả bên phải không
     * nhảy theo độ dài tên ô.
     */
    private static final float SLOT_LABEL_WIDTH_PX = 140f;

    /**
     * Nút trong hàng hành động rộng hơn {@link #ROW_BUTTON_WIDTH_PX}: nhãn dài nhất ở đó là
     * "Giao dịch"/"Cường hoá", và ở 84px chúng bị CẮT MẤT CHỮ tràn ra ngoài viền khung — thấy bằng
     * ảnh chụp {@code HudPreviewTool}, không thấy bằng build xanh.
     */
    static final float ACTION_BUTTON_WIDTH_PX = 104f;

    /** Công tắc Bảo Vệ mang cả nhãn lẫn trạng thái ("Bảo vệ: Tắt") nên rộng hơn nút thường. */
    static final float PROTECTION_BUTTON_WIDTH_PX = 150f;

    /**
     * Chiều cao CỐ ĐỊNH của vùng cuộn danh sách túi, và của vùng cuộn khu trang bị.
     *
     * <p>Vì sao phải chặn cứng: khung này để cả hai danh sách tự nở theo nội dung thì ở trạng thái
     * túi đầy nó cao <b>1238px</b> — đo bằng {@code HudPreviewTool#preferredHeight}, trong khi cửa
     * sổ game là 720px. Nghĩa là người chơi có túi đầy KHÔNG với tới nổi khu trang bị lẫn hàng nút
     * bên dưới. Hai con số dưới đây giữ tổng chiều cao khung nằm gọn trong màn hình 720p ở trạng
     * thái tệ nhất; phần dài hơn thì cuộn.
     */
    private static final float ITEM_LIST_HEIGHT_PX = 190f;

    private static final float EQUIPMENT_LIST_HEIGHT_PX = 130f;

    private static final float ICON_SIZE_PX = 32f;

    /** Vàng đồng nhạt — tiêu đề mục, cùng tông viền HUD của {@link KageSkin}. */
    private static final Color SECTION_COLOR = Color.valueOf("E0B860");

    /** Trắng ngà — dòng dữ liệu thường. */
    private static final Color ROW_COLOR = Color.valueOf("F2F2F2");

    /** Atlas MEDIUM tier — khớp {@code dev.kage.client.core.world.GraphicsTier.MEDIUM.folderName}. */
    private static final String ICON_ATLAS_PATH = "atlases/med/main.atlas";

    /**
     * Một dòng túi đồ.
     *
     * @param itemId {@code inventory_item.id} THẬT, báo ra khi người chơi bấm Chọn.
     * @param iconCode {@code item_template.code}, rỗng khi chưa có catalog — tra vùng atlas
     *     {@code icon_<code>}; rỗng thì KHÔNG vẽ icon (xem javadoc lớp).
     * @param displayText chuỗi ĐÃ ghép sẵn (tên + số lượng + mức cường hoá), panel không tự ghép.
     */
    public record ItemRow(long itemId, String iconCode, String displayText) {}

    /**
     * Một dòng trang bị (7 slot cố định).
     *
     * @param slotNumber {@code EquipmentSlot.getNumber()} trần — panel không biết enum protobuf
     *     (xem javadoc lớp), chỉ vọng lại đúng con số này khi báo ra ý định Gỡ.
     * @param slotLabel tên slot ĐÃ dịch (VD "Vũ khí").
     * @param displayText tên vật phẩm đang mặc ĐÃ dịch, hoặc chuỗi "(trống)" đã dịch.
     * @param equipped có gì đang mặc ở slot này không — quyết định có bày nút Gỡ hay không.
     */
    public record EquipmentRow(int slotNumber, String slotLabel, String displayText, boolean equipped) {}

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final TextureAtlas iconAtlas;
    private final Table panel;

    // Bốn nhóm widget/trạng thái thay cho 25 field rời (dòng 58(d), chủ dự án duyệt 2026-10-17 — trần
    // ≤10 field mỗi lớp). Mỗi nhóm là một vùng THẬT của cửa sổ, không phải gom cho đủ số.
    private final Header header;
    private final ListArea lists;
    private final ActionRow actionRow;
    private final Listeners listeners = new Listeners();

    /** Dòng trạng thái + số vàng + sức chứa túi — đầu cửa sổ. */
    private record Header(Label status, Label gold, Label capacity) {}

    /** Danh sách vật phẩm, ô mô tả món đang chọn, và danh sách đồ đang mặc. */
    private record ListArea(Table items, Label detail, Table equipment) {}

    /**
     * Hàng nút hành động cho món đang chọn. {@code primary} là MỘT ô cho hành động chính: "Mặc" với
     * trang bị, "Dùng" với đan dược (ADR-046 D5). Dùng chung một ô chứ không thêm ô thứ sáu vì hàng hành
     * động đã rộng 550px trong khung 560px — thêm một nút 84px là tràn ra ngoài nền, đúng lỗi đã bắt được
     * ở {@code QuestLogPanel}. Và hai hành động này KHÔNG BAO GIỜ cùng có nghĩa: {@code ItemTemplate} cấm
     * một món vừa mặc được vừa dùng được.
     */
    private record ActionRow(
            TextButton primary, TextButton sell, TextButton offer, TextButton enhance, TextButton protection) {}

    /** Mọi callback gắn từ ngoài vào — đặt qua các {@code setOn...}, đọc lúc người chơi bấm. */
    private static final class Listeners {
        private LongConsumer onSelect;
        private Runnable onEquipSelected;
        private Runnable onUseSelected;
        private Runnable onSellSelected;
        private Runnable onOfferSelected;
        private Runnable onEnhanceSelected;
        private Runnable onToggleProtection;
        private Runnable onDismantleSelected;
        private Runnable onLockSelected;
        private Runnable onSortSelected;
        private Runnable onJunkSelected;
        private IntConsumer onUnequip;
        private Runnable onOpened;

        /**
         * Có được phép mở bằng phím I lúc này không. Mặc định là LUÔN được — panel không biết trên màn
         * hình còn khung nào khác, xem {@link InventoryPanel#setCanOpen}.
         */
        private BooleanSupplier canOpen = () -> true;
    }

    /** Nút chính đang mang nghĩa gì — xem {@link #showDetail}. */
    private PrimaryAction primaryAction = PrimaryAction.NONE;

    /** ADR-159 — "Khoá"/"Mở khoá" món đang chọn; chữ đổi theo {@link #setLockState}. */
    private TextButton lockButton;

    /**
     * @param skin bộ style MƯỢN của {@code CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link MailPanel}/{@link GuildPanel}.
     */
    public InventoryPanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());
        this.iconAtlas = loadIconAtlas();

        Label goldLabel = new Label("", skin.labelStyle);
        goldLabel.setColor(SECTION_COLOR);
        Label capacityLabel = new Label("", skin.labelStyle);
        capacityLabel.setColor(ROW_COLOR);
        this.header = new Header(wrappingLabel(SECTION_COLOR), goldLabel, capacityLabel);
        this.lists = new ListArea(new Table(), wrappingLabel(ROW_COLOR), new Table());
        this.actionRow = new ActionRow(
                actionButton("inventory.action.equip", this::firePrimaryAction),
                actionButton("inventory.action.sell", () -> fire(listeners.onSellSelected)),
                actionButton("inventory.action.offer", () -> fire(listeners.onOfferSelected)),
                actionButton("inventory.action.enhance", () -> fire(listeners.onEnhanceSelected)),
                actionButton("inventory.action.protection_off", () -> fire(listeners.onToggleProtection)));

        this.panel = buildPanel();

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.center).add(panel);
        stage.addActor(container);

        installKeyHandling();
    }

    /**
     * Dựng thân cửa sổ. Tách khỏi constructor THUẦN để giữ nó dưới ngưỡng {@code MethodLength} của
     * Checkstyle — PHẢI gọi SAU khi mọi widget con đã gán, vì nó xếp chính chúng vào bảng.
     */
    private Table buildPanel() {
        Table table = new Table();
        table.setBackground(skin.panel);
        table.pad(PANEL_PAD_PX);
        table.add(titleRow()).width(PANEL_WIDTH_PX).left().row();
        table.add(header.status()).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        table.add(sectionTitle("inventory.section.items"))
                .width(PANEL_WIDTH_PX)
                .left()
                .padTop(10f)
                .row();
        table.add(header.capacity()).width(PANEL_WIDTH_PX).left().row();
        table.add(ScrollingList.wrap(lists.items(), skin))
                .width(PANEL_WIDTH_PX)
                .height(ITEM_LIST_HEIGHT_PX)
                .growX()
                .padTop(4f)
                .row();
        table.add(lists.detail()).width(PANEL_WIDTH_PX).left().padTop(10f).row();
        table.add(buildActionRow()).width(PANEL_WIDTH_PX).growX().padTop(6f).row();
        table.add(sectionTitle("inventory.section.equipment"))
                .width(PANEL_WIDTH_PX)
                .left()
                .padTop(10f)
                .row();
        table.add(ScrollingList.wrap(lists.equipment(), skin))
                .width(PANEL_WIDTH_PX)
                .height(EQUIPMENT_LIST_HEIGHT_PX)
                .growX()
                .padTop(4f)
                .row();
        table.add(closeHint()).width(PANEL_WIDTH_PX).left().padTop(8f).row();
        table.setVisible(false);
        return table;
    }

    /** Tự TẮT icon khi thiếu atlas (bản checkout chưa chạy pipeline art) — cùng {@code SkillVfxRenderer}. */
    private static TextureAtlas loadIconAtlas() {
        FileHandle file = Gdx.files.internal(ICON_ATLAS_PATH);
        if (!file.exists()) {
            LOG.warn("Không thấy atlas '{}' — icon vật phẩm để trống, phần còn lại vẫn chạy", file.path());
            return null;
        }
        return new TextureAtlas(file);
    }

    private Label wrappingLabel(Color color) {
        Label label = new Label("", skin.labelStyle);
        label.setWrap(true);
        label.setColor(color);
        return label;
    }

    /** Tiêu đề cửa sổ + số vàng ngay cạnh — túi đồ là chỗ đầu tiên hiện con số này (ADR-035, 209). */
    private Table titleRow() {
        Table row = new Table();
        Label title = new Label(text("inventory.title"), skin.labelStyle);
        title.setColor(SECTION_COLOR);
        row.add(title).left().padRight(10f);
        row.add(header.gold()).left().expandX();
        return row;
    }

    /**
     * Hàng hành động của item ĐANG CHỌN: Mặc/Dùng · Bán 1 · Giao dịch · Cường hoá · công tắc Bảo Vệ.
     * Toàn bộ ẩn cho tới khi người chơi chọn một món (xem {@link #showDetail}).
     *
     * <p>Nằm ở HÀNG RIÊNG, dưới tên món, chứ không cùng dòng với nó: năm ô điều khiển cạnh một nhãn
     * tên không đủ chỗ trong {@link #PANEL_WIDTH_PX}, và cách bố trí cũ đã làm hai nút bị cắt mất
     * chữ. Chiều rộng cộng lại (84+84+104+104+150 + 4×6 khoảng cách = 550) vừa khít 560px.
     *
     * <p><b>KHÔNG hiện chi phí hay tỉ lệ cường hoá</b>: bảng {@code EnhancementTable} sống phía
     * server và không packet nào gửi nó sang client, nên mọi con số hiện ở đây đều là bịa (Điều 24).
     * Người chơi biết kết quả qua dòng trạng thái, biết vàng đã trừ qua {@code GoldChangedEvent} —
     * cả hai đều do server nói.
     */
    private Table buildActionRow() {
        Table row = new Table();
        row.add(actionRow.primary()).width(ROW_BUTTON_WIDTH_PX).padRight(6f);
        row.add(actionRow.sell()).width(ROW_BUTTON_WIDTH_PX).padRight(6f);
        row.add(actionRow.offer()).width(ACTION_BUTTON_WIDTH_PX).padRight(6f);
        row.add(actionRow.enhance()).width(ACTION_BUTTON_WIDTH_PX).padRight(6f);
        row.add(actionRow.protection()).width(PROTECTION_BUTTON_WIDTH_PX);
        row.add().expandX(); // đẩy cả cụm về TRÁI, thẳng hàng với tên món ở dòng trên
        actionRow.primary().setVisible(false);
        actionRow.sell().setVisible(false);
        actionRow.offer().setVisible(false);
        actionRow.enhance().setVisible(false);
        actionRow.protection().setVisible(false);
        return row;
    }

    private Table closeHint() {
        Table block = new Table();
        Label hint = new Label(text("inventory.hint.close"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        block.add(hint).left().expandX().row();
        // ADR-160 — hàng nút riêng dưới chữ gợi ý (chung một hàng với chữ thì tràn).
        Table row = new Table();
        block.add(row).right().padTop(4f);
        row.add(actionButton("inventory.action.sort", () -> fire(listeners.onSortSelected)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padRight(6f);
        // ADR-170 — mở cửa sổ bán rác (liệt kê trước, bấm mới bán). 5 × 104 + 4 khe 6 = 544 ≤ 560.
        row.add(actionButton("inventory.action.junk", () -> fire(listeners.onJunkSelected)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padRight(6f);
        // ADR-097 — phân giải món đang chọn; ở hàng này vì hàng hành động đã kín bề rộng khung.
        row.add(actionButton("inventory.action.dismantle", () -> fire(listeners.onDismantleSelected)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padRight(6f);
        // ADR-159 — khoá/mở khoá món đang chọn.
        this.lockButton = actionButton("inventory.action.lock", () -> fire(listeners.onLockSelected));
        lockButton.setVisible(false);
        row.add(lockButton).width(ACTION_BUTTON_WIDTH_PX).padRight(6f);
        row.add(actionButton("inventory.action.refresh", () -> fire(listeners.onOpened)))
                .width(ACTION_BUTTON_WIDTH_PX);
        return block;
    }

    /** ADR-170 — người chơi bấm Bán rác: hỏi server danh sách sẽ bán, cửa sổ bán rác tự mở khi có câu trả lời. */
    public void setOnJunkSelected(Runnable listener) {
        listeners.onJunkSelected = listener;
    }

    /** ADR-160 — người chơi bấm Sắp xếp: server gom chồng và xếp lại túi. */
    public void setOnSortSelected(Runnable listener) {
        listeners.onSortSelected = listener;
    }

    /**
     * ADR-159 — nút khoá cho món đang chọn: ẩn khi không chọn gì; chữ "Mở khoá" khi món đó đang khoá. Khoá được hay
     * không vẫn là server quyết; client chỉ đọc cờ {@code locked} server gửi trong danh sách túi.
     */
    public void setLockState(boolean visible, boolean locked) {
        lockButton.setVisible(visible);
        lockButton.setText(text(locked ? "inventory.action.unlock" : "inventory.action.lock"));
    }

    /** ADR-159 — người chơi bấm Khoá/Mở khoá trên món đang chọn. */
    public void setOnLockSelected(Runnable listener) {
        listeners.onLockSelected = listener;
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

    private static void fire(Runnable listener) {
        if (listener != null) {
            listener.run();
        }
    }

    /**
     * Phím I mở khung, ESC đóng — cùng cơ chế và cùng cái bẫy đã mô tả ở {@link MailPanel}: mở ở
     * {@code keyUp} chứ không {@code keyDown}; I chỉ MỞ chứ không bật/tắt; đang mở thì NUỐT mọi
     * {@code keyUp}.
     */
    private void installKeyHandling() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (isOpen()) {
                    return true;
                }
                if (keycode != OPEN_KEY || !listeners.canOpen.getAsBoolean()) {
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
     * Đang mở hay không. {@code WorldScreen} BẮT BUỘC hỏi câu này trước khi đọc phím gameplay — cùng
     * lý do {@link MailPanel#isOpen()}.
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    /**
     * Mở khung và báo ra ngoài để hỏi lại server — túi đồ có thể đã đổi từ lần mở trước (nhặt đồ,
     * mặc/gỡ ở một phiên khác không tồn tại ở game này, nhưng vẫn hỏi lại cho chắc, cùng {@link MailPanel#open}).
     */
    public void open() {
        panel.setVisible(true);
        fire(listeners.onOpened);
    }

    public void close() {
        panel.setVisible(false);
    }

    /** Gọi mỗi lần khung mở ra hoặc người chơi bấm Làm mới — chỗ nghe có nhiệm vụ hỏi lại server. */
    public void setOnRefresh(Runnable listener) {
        listeners.onOpened = listener;
    }

    /** Điều kiện để phím I được phép mở khung — cổng phím CHUNG của mọi cửa sổ, xem {@link MailPanel#setCanOpen}. */
    public void setCanOpen(BooleanSupplier condition) {
        listeners.canOpen = condition;
    }

    /** Id vật phẩm người chơi vừa bấm Chọn để xem chi tiết. */
    public void setOnSelect(LongConsumer listener) {
        listeners.onSelect = listener;
    }

    /** Người chơi bấm Mặc trên item ĐANG CHỌN — không mang id, coordinator tự biết đang chọn ai. */
    public void setOnEquipSelected(Runnable listener) {
        listeners.onEquipSelected = listener;
    }

    /** ADR-097 — nút "Phân giải" cho món đang chọn. */
    public void setOnDismantleSelected(Runnable listener) {
        listeners.onDismantleSelected = listener;
    }

    /** Người chơi bấm Bán trên item đang chọn. */
    public void setOnSellSelected(Runnable listener) {
        listeners.onSellSelected = listener;
    }

    /** Số slot ({@code EquipmentSlot.getNumber()}) người chơi vừa bấm Gỡ. */
    public void setOnUnequip(IntConsumer listener) {
        listeners.onUnequip = listener;
    }

    /** Dòng trạng thái dưới tiêu đề — chuỗi ĐÃ dịch. */
    public void setStatus(String text) {
        header.status().setText(text);
    }

    /** Số vàng cạnh tiêu đề — chuỗi ĐÃ ghép sẵn ("—" khi chưa có gói vàng nào, xem coordinator). */
    public void setGold(String text) {
        header.gold().setText(text);
    }

    /**
     * Vẽ lại lưới túi. Dựng lại toàn bộ dòng mỗi lần gọi — chỉ đổi khi server vừa trả lời, không mỗi
     * khung hình, nên không nằm trong đường cấm cấp phát của game loop (Điều 31), cùng
     * {@link MailPanel#setMails}.
     *
     * @param rows đã cắt xuống số dòng hiển thị được ở coordinator.
     * @param capacityText chuỗi ĐÃ dịch "n/sức chứa".
     * @param overflowNotice chuỗi đã dịch cho phần bị cắt bớt hoặc túi rỗng, rỗng nếu không cần nói gì.
     */
    public void setItems(List<ItemRow> rows, String capacityText, String overflowNotice) {
        header.capacity().setText(capacityText);
        lists.items().clear();
        for (ItemRow row : rows) {
            addItemRow(row);
        }
        if (!overflowNotice.isEmpty()) {
            lists.items()
                    .add(rowLabel(overflowNotice))
                    .colspan(3)
                    .growX()
                    .left()
                    .row();
        }
    }

    private void addItemRow(ItemRow row) {
        lists.items()
                .add(iconActor(row.iconCode()))
                .size(ICON_SIZE_PX)
                .padRight(6f)
                .padBottom(ROW_PAD_PX);
        Label label = rowLabel(row.displayText());
        label.setEllipsis(true);
        lists.items().add(label).growX().minWidth(0f).left().padRight(6f).padBottom(ROW_PAD_PX);
        lists.items()
                .add(actionButton("inventory.action.select", () -> emit(listeners.onSelect, row.itemId())))
                .width(ROW_BUTTON_WIDTH_PX)
                .padBottom(ROW_PAD_PX);
        lists.items().row();
    }

    /**
     * Vùng icon của một item, hoặc một ô TRỐNG (không phải hộp màu bịa) khi thiếu atlas hoặc thiếu
     * vùng {@code icon_<code>} — xem javadoc lớp.
     */
    private Actor iconActor(String code) {
        TextureRegion region = findIconRegion(code);
        return region == null ? new Actor() : new Image(region);
    }

    private TextureRegion findIconRegion(String code) {
        if (iconAtlas == null || code.isEmpty()) {
            return null;
        }
        return iconAtlas.findRegion("icon_" + code);
    }

    /**
     * Chi tiết item đang chọn + nút Mặc.
     *
     * @param detailText chuỗi ĐÃ dịch (tên đầy đủ + số lượng + mức cường hoá), hoặc lời mời chọn một
     *     item khi chưa chọn gì.
     * @param primary nút chính mang nghĩa gì — {@link PrimaryAction#NONE} thì giấu nút (chưa chọn gì,
     *     hoặc món không mặc/dùng được theo catalog).
     * @param actions ba nút còn lại (Bán/Giao dịch/Cường hoá) — xem {@link Actions}.
     */
    public void showDetail(String detailText, PrimaryAction primary, Actions actions) {
        lists.detail().setText(detailText);
        this.primaryAction = primary;
        actionRow.primary().setVisible(primary != PrimaryAction.NONE);
        if (primary != PrimaryAction.NONE) {
            actionRow
                    .primary()
                    .setText(text(primary == PrimaryAction.EQUIP ? "inventory.action.equip" : "inventory.action.use"));
        }
        actionRow.sell().setVisible(actions.canSell());
        actionRow.offer().setVisible(actions.canOffer());
        actionRow.enhance().setVisible(actions.canEnhance());
        actionRow.protection().setVisible(actions.canEnhance());
    }

    /**
     * Hành động chính của món đang chọn. Ba trạng thái loại trừ nhau — xem javadoc
     * {@link ActionRow} về lý do chúng dùng chung một ô nút.
     */
    public enum PrimaryAction {
        /** Không chọn món nào, hoặc món không mặc được cũng không dùng được. */
        NONE,
        EQUIP,
        USE
    }

    private void firePrimaryAction() {
        fire(primaryAction == PrimaryAction.USE ? listeners.onUseSelected : listeners.onEquipSelected);
    }

    /**
     * Ba nút phụ thuộc trạng thái NGOÀI ô chi tiết. Gói thành record vì {@code showDetail} đã chạm
     * ngưỡng {@code ParameterNumber} của Checkstyle (max=4) — cùng lý do {@code PlayContext} tồn tại.
     *
     * @param canSell luôn bật khi đã chọn một món: bán được hay không là phán quyết của SERVER
     *     ({@code SELL_OUTCOME_ITEM_NOT_SELLABLE}, Điều 24)
     * @param canOffer chỉ bật khi ĐANG ở trong một phiên giao dịch — khác {@code canSell}, đây là
     *     thứ client biết chắc, và bày một nút chỉ để nhận về "bạn không ở phiên nào" là vô nghĩa
     * @param canEnhance cùng luật {@code canSell}: bật với MỌI món đang chọn. Cường hoá được hay
     *     không (đã +15? đủ vàng?) là phán quyết của SERVER ({@code ENHANCE_OUTCOME_MAX_LEVEL},
     *     {@code ENHANCE_OUTCOME_INSUFFICIENT_GOLD}) — client không có bảng chi phí để đoán trước
     */
    public record Actions(boolean canSell, boolean canOffer, boolean canEnhance) {}

    /** Người chơi bấm "Dùng" trên item đang chọn (ADR-046 D5 — đan dược). */
    public void setOnUseSelected(Runnable listener) {
        listeners.onUseSelected = listener;
    }

    /** Người chơi bấm Giao dịch trên item đang chọn — đưa món đó vào lời chào. */
    public void setOnOfferSelected(Runnable listener) {
        listeners.onOfferSelected = listener;
    }

    /** Người chơi bấm Cường hoá trên item đang chọn. */
    public void setOnEnhanceSelected(Runnable listener) {
        listeners.onEnhanceSelected = listener;
    }

    /** Người chơi gạt công tắc Bảo Vệ — panel KHÔNG tự giữ trạng thái, xem {@link #setProtection}. */
    public void setOnToggleProtection(Runnable listener) {
        listeners.onToggleProtection = listener;
    }

    /**
     * Vẽ lại nhãn công tắc Bảo Vệ. Trạng thái bật/tắt sống ở coordinator chứ không ở widget (UI.md
     * §4) — panel chỉ đổi chữ trên nút theo lời nó được bảo.
     */
    public void setProtection(boolean on) {
        actionRow.protection().setText(text(on ? "inventory.action.protection_on" : "inventory.action.protection_off"));
    }

    /**
     * Vẽ lại khu trang bị — LUÔN đúng 7 dòng (một cho mỗi {@code EquipmentSlot} thật), không cắt
     * bớt: khác túi đồ, số slot trang bị CỐ ĐỊNH nên không cần trần hiển thị.
     */
    public void setEquipment(List<EquipmentRow> rows) {
        lists.equipment().clear();
        for (EquipmentRow row : rows) {
            lists.equipment()
                    .add(rowLabel(row.slotLabel()))
                    .width(SLOT_LABEL_WIDTH_PX)
                    .left()
                    .padRight(6f)
                    .padBottom(ROW_PAD_PX);
            Label label = rowLabel(row.displayText());
            label.setEllipsis(true);
            // minWidth(0): không có nó, ô lấy bề rộng MONG MUỐN của nhãn (dòng dài nhất của mô tả
            // hai dòng, ~370px) làm bề rộng TỐI THIỂU, nên cả hàng đòi 604px trong một vùng cuộn
            // 550px. Trục ngang bị khoá nên không có thanh cuộn: bảng bị căn giữa và tràn ra CẢ HAI
            // mép — người chơi thấy "áp khí"/"o bào" thay vì "Pháp khí"/"Đạo bào", và nút Gỡ cụt.
            // Cho ô co được thì setEllipsis ở trên mới thật sự có việc để làm.
            lists.equipment()
                    .add(label)
                    .growX()
                    .minWidth(0f)
                    .left()
                    .padRight(6f)
                    .padBottom(ROW_PAD_PX);
            if (row.equipped()) {
                lists.equipment()
                        .add(actionButton(
                                "inventory.action.unequip", () -> emitInt(listeners.onUnequip, row.slotNumber())))
                        .width(ROW_BUTTON_WIDTH_PX)
                        .padBottom(ROW_PAD_PX);
            } else {
                lists.equipment().add().width(ROW_BUTTON_WIDTH_PX);
            }
            lists.equipment().row();
        }
    }

    private static void emit(LongConsumer listener, long itemId) {
        if (listener != null) {
            listener.accept(itemId);
        }
    }

    private static void emitInt(IntConsumer listener, int slotNumber) {
        if (listener != null) {
            listener.accept(slotNumber);
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
     * Chiều cao khung MUỐN có ở trạng thái hiện tại. Chỉ dùng cho {@code HudLayoutBudgetTool}: "túi
     * đầy có tràn khỏi màn hình không" là một con số phải ĐO, không phải một cảm giác nhìn ảnh — khung
     * này từng cao 1238px trong màn hình 720px mà build vẫn xanh (xem {@link #ITEM_LIST_HEIGHT_PX}).
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
        if (iconAtlas != null) {
            iconAtlas.dispose();
        }
    }
}
