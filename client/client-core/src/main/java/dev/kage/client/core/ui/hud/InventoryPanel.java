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

    private static final float ROW_BUTTON_WIDTH_PX = 84f;

    /**
     * Nút trong hàng hành động rộng hơn {@link #ROW_BUTTON_WIDTH_PX}: nhãn dài nhất ở đó là
     * "Giao dịch"/"Cường hoá", và ở 84px chúng bị CẮT MẤT CHỮ tràn ra ngoài viền khung — thấy bằng
     * ảnh chụp {@code HudPreviewTool}, không thấy bằng build xanh.
     */
    private static final float ACTION_BUTTON_WIDTH_PX = 104f;

    /** Công tắc Bảo Vệ mang cả nhãn lẫn trạng thái ("Bảo vệ: Tắt") nên rộng hơn nút thường. */
    private static final float PROTECTION_BUTTON_WIDTH_PX = 150f;

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
    private final Label statusLabel;
    private final Label goldLabel;
    private final Label capacityLabel;
    private final Table itemList;
    private final Label detailLabel;
    /**
     * MỘT ô nút cho hành động chính của món đang chọn: "Mặc" với trang bị, "Dùng" với đan dược
     * (ADR-046 D5). Dùng chung một ô chứ không thêm ô thứ sáu vì hàng hành động đã rộng 550px
     * trong khung 560px — thêm một nút 84px là tràn ra ngoài nền, đúng lỗi đã bắt được ở
     * {@code QuestLogPanel}. Và hai hành động này KHÔNG BAO GIỜ cùng có nghĩa: {@code ItemTemplate}
     * cấm một món vừa mặc được vừa dùng được.
     */
    private final TextButton primaryButton;

    private final TextButton sellButton;
    private final TextButton offerButton;
    private final TextButton enhanceButton;
    private final TextButton protectionButton;
    private final Table equipmentList;

    private LongConsumer onSelect;
    private Runnable onEquipSelected;
    private Runnable onUseSelected;
    private Runnable onSellSelected;
    private Runnable onOfferSelected;
    private Runnable onEnhanceSelected;
    private Runnable onToggleProtection;
    private IntConsumer onUnequip;
    private Runnable onOpened;

    /**
     * Có được phép mở bằng phím I lúc này không. Mặc định là LUÔN được — panel không biết trên màn
     * hình còn khung nào khác, xem {@link #setCanOpen}.
     */
    private BooleanSupplier canOpen = () -> true;

    /** Nút chính đang mang nghĩa gì — xem {@link #showDetail}. */
    private PrimaryAction primaryAction = PrimaryAction.NONE;

    /**
     * @param skin bộ style MƯỢN của {@code CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link MailPanel}/{@link GuildPanel}.
     */
    public InventoryPanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());
        this.iconAtlas = loadIconAtlas();

        this.statusLabel = wrappingLabel(SECTION_COLOR);
        this.goldLabel = new Label("", skin.labelStyle);
        goldLabel.setColor(SECTION_COLOR);
        this.capacityLabel = new Label("", skin.labelStyle);
        capacityLabel.setColor(ROW_COLOR);

        this.itemList = new Table();
        this.detailLabel = wrappingLabel(ROW_COLOR);
        this.primaryButton = actionButton("inventory.action.equip", this::firePrimaryAction);
        this.sellButton = actionButton("inventory.action.sell", () -> fire(onSellSelected));
        this.offerButton = actionButton("inventory.action.offer", () -> fire(onOfferSelected));
        this.enhanceButton = actionButton("inventory.action.enhance", () -> fire(onEnhanceSelected));
        this.protectionButton = actionButton("inventory.action.protection_off", () -> fire(onToggleProtection));
        this.equipmentList = new Table();

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
        table.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        table.add(sectionTitle("inventory.section.items"))
                .width(PANEL_WIDTH_PX)
                .left()
                .padTop(10f)
                .row();
        table.add(capacityLabel).width(PANEL_WIDTH_PX).left().row();
        table.add(ScrollingList.wrap(itemList, skin))
                .width(PANEL_WIDTH_PX)
                .height(ITEM_LIST_HEIGHT_PX)
                .growX()
                .padTop(4f)
                .row();
        table.add(detailLabel).width(PANEL_WIDTH_PX).left().padTop(10f).row();
        table.add(buildActionRow()).width(PANEL_WIDTH_PX).growX().padTop(6f).row();
        table.add(sectionTitle("inventory.section.equipment"))
                .width(PANEL_WIDTH_PX)
                .left()
                .padTop(10f)
                .row();
        table.add(ScrollingList.wrap(equipmentList, skin))
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
        row.add(goldLabel).left().expandX();
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
        row.add(primaryButton).width(ROW_BUTTON_WIDTH_PX).padRight(6f);
        row.add(sellButton).width(ROW_BUTTON_WIDTH_PX).padRight(6f);
        row.add(offerButton).width(ACTION_BUTTON_WIDTH_PX).padRight(6f);
        row.add(enhanceButton).width(ACTION_BUTTON_WIDTH_PX).padRight(6f);
        row.add(protectionButton).width(PROTECTION_BUTTON_WIDTH_PX);
        row.add().expandX(); // đẩy cả cụm về TRÁI, thẳng hàng với tên món ở dòng trên
        primaryButton.setVisible(false);
        sellButton.setVisible(false);
        offerButton.setVisible(false);
        enhanceButton.setVisible(false);
        protectionButton.setVisible(false);
        return row;
    }

    private Table closeHint() {
        Table row = new Table();
        Label hint = new Label(text("inventory.hint.close"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        row.add(hint).left().expandX().padRight(6f);
        row.add(actionButton("inventory.action.refresh", () -> fire(onOpened))).width(ACTION_BUTTON_WIDTH_PX);
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
        fire(onOpened);
    }

    public void close() {
        panel.setVisible(false);
    }

    /** Gọi mỗi lần khung mở ra hoặc người chơi bấm Làm mới — chỗ nghe có nhiệm vụ hỏi lại server. */
    public void setOnRefresh(Runnable listener) {
        this.onOpened = listener;
    }

    /** Điều kiện để phím I được phép mở khung — cổng phím CHUNG của mọi cửa sổ, xem {@link MailPanel#setCanOpen}. */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Id vật phẩm người chơi vừa bấm Chọn để xem chi tiết. */
    public void setOnSelect(LongConsumer listener) {
        this.onSelect = listener;
    }

    /** Người chơi bấm Mặc trên item ĐANG CHỌN — không mang id, coordinator tự biết đang chọn ai. */
    public void setOnEquipSelected(Runnable listener) {
        this.onEquipSelected = listener;
    }

    /** Người chơi bấm Bán trên item đang chọn. */
    public void setOnSellSelected(Runnable listener) {
        this.onSellSelected = listener;
    }

    /** Số slot ({@code EquipmentSlot.getNumber()}) người chơi vừa bấm Gỡ. */
    public void setOnUnequip(IntConsumer listener) {
        this.onUnequip = listener;
    }

    /** Dòng trạng thái dưới tiêu đề — chuỗi ĐÃ dịch. */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /** Số vàng cạnh tiêu đề — chuỗi ĐÃ ghép sẵn ("—" khi chưa có gói vàng nào, xem coordinator). */
    public void setGold(String text) {
        goldLabel.setText(text);
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
        capacityLabel.setText(capacityText);
        itemList.clear();
        for (ItemRow row : rows) {
            addItemRow(row);
        }
        if (!overflowNotice.isEmpty()) {
            itemList.add(rowLabel(overflowNotice)).colspan(3).growX().left().row();
        }
    }

    private void addItemRow(ItemRow row) {
        itemList.add(iconActor(row.iconCode())).size(ICON_SIZE_PX).padRight(6f).padBottom(ROW_PAD_PX);
        Label label = rowLabel(row.displayText());
        label.setEllipsis(true);
        itemList.add(label).growX().left().padRight(6f).padBottom(ROW_PAD_PX);
        itemList.add(actionButton("inventory.action.select", () -> emit(onSelect, row.itemId())))
                .width(ROW_BUTTON_WIDTH_PX)
                .padBottom(ROW_PAD_PX);
        itemList.row();
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
        detailLabel.setText(detailText);
        this.primaryAction = primary;
        primaryButton.setVisible(primary != PrimaryAction.NONE);
        if (primary != PrimaryAction.NONE) {
            primaryButton.setText(
                    text(primary == PrimaryAction.EQUIP ? "inventory.action.equip" : "inventory.action.use"));
        }
        sellButton.setVisible(actions.canSell());
        offerButton.setVisible(actions.canOffer());
        enhanceButton.setVisible(actions.canEnhance());
        protectionButton.setVisible(actions.canEnhance());
    }

    /**
     * Hành động chính của món đang chọn. Ba trạng thái loại trừ nhau — xem javadoc
     * {@link #primaryButton} về lý do chúng dùng chung một ô nút.
     */
    public enum PrimaryAction {
        /** Không chọn món nào, hoặc món không mặc được cũng không dùng được. */
        NONE,
        EQUIP,
        USE
    }

    private void firePrimaryAction() {
        fire(primaryAction == PrimaryAction.USE ? onUseSelected : onEquipSelected);
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
        this.onUseSelected = listener;
    }

    /** Người chơi bấm Giao dịch trên item đang chọn — đưa món đó vào lời chào. */
    public void setOnOfferSelected(Runnable listener) {
        this.onOfferSelected = listener;
    }

    /** Người chơi bấm Cường hoá trên item đang chọn. */
    public void setOnEnhanceSelected(Runnable listener) {
        this.onEnhanceSelected = listener;
    }

    /** Người chơi gạt công tắc Bảo Vệ — panel KHÔNG tự giữ trạng thái, xem {@link #setProtection}. */
    public void setOnToggleProtection(Runnable listener) {
        this.onToggleProtection = listener;
    }

    /**
     * Vẽ lại nhãn công tắc Bảo Vệ. Trạng thái bật/tắt sống ở coordinator chứ không ở widget (UI.md
     * §4) — panel chỉ đổi chữ trên nút theo lời nó được bảo.
     */
    public void setProtection(boolean on) {
        protectionButton.setText(text(on ? "inventory.action.protection_on" : "inventory.action.protection_off"));
    }

    /**
     * Vẽ lại khu trang bị — LUÔN đúng 7 dòng (một cho mỗi {@code EquipmentSlot} thật), không cắt
     * bớt: khác túi đồ, số slot trang bị CỐ ĐỊNH nên không cần trần hiển thị.
     */
    public void setEquipment(List<EquipmentRow> rows) {
        equipmentList.clear();
        for (EquipmentRow row : rows) {
            equipmentList
                    .add(rowLabel(row.slotLabel()))
                    .width(140f)
                    .left()
                    .padRight(6f)
                    .padBottom(ROW_PAD_PX);
            Label label = rowLabel(row.displayText());
            label.setEllipsis(true);
            equipmentList.add(label).growX().left().padRight(6f).padBottom(ROW_PAD_PX);
            if (row.equipped()) {
                equipmentList
                        .add(actionButton("inventory.action.unequip", () -> emitInt(onUnequip, row.slotNumber())))
                        .width(ROW_BUTTON_WIDTH_PX)
                        .padBottom(ROW_PAD_PX);
            } else {
                equipmentList.add().width(ROW_BUTTON_WIDTH_PX);
            }
            equipmentList.row();
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
        try {
            return i18n.get(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
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
