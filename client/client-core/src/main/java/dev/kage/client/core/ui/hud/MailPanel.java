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

import dev.kage.client.core.render.FontFactory;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Hòm thư (2026-08-27) — danh sách thư đến / đọc nội dung / nhận thưởng / xoá / soạn thư mới, mở
 * bằng phím M, neo GIỮA màn hình.
 *
 * <p>Giữa màn hình chứ không phải một góc như {@link ChatPanel}/{@link CombatHud}, cùng lập luận
 * {@link GuildPanel}: đây là một cửa sổ quản trị — mở ra là để làm việc với nó, không phải để vừa
 * nhìn vừa chơi. Đó cũng là lý do nó chặn toàn bộ phím gameplay khi mở (xem {@link #isOpen()}).
 *
 * <p>{@code Stage} RIÊNG cùng lý do {@link DialoguePanel}/{@link ChatPanel}: thứ tự nuốt input chỉ
 * đặt được cho cả một {@code Stage} qua {@code InputMultiplexer}. Click vào khung này phải bị nuốt
 * chứ không rơi xuống thế giới thành lệnh chọn mục tiêu.
 *
 * <p>Widget chỉ VẼ (UI.md §4): nó không biết opcode nào, không dựng packet, không sinh khoá
 * idempotency. Nó nhận các dòng ĐÃ ghép sẵn thành chuỗi hiển thị và báo ra ý định người chơi vừa
 * bấm.
 *
 * <h2>⚠️ Giới hạn CÓ THẬT của cửa sổ này, không được che giấu</h2>
 *
 * <ul>
 *   <li><b>Đính kèm chỉ hiện dạng số.</b> {@code MailAttachmentView} chỉ mang
 *       {@code item_template_id} + số lượng, và client CHƯA có catalog vật phẩm nào để tra
 *       tên/icon — đúng <b>Khoảng trống 2</b> của
 *       {@code docs/01-architecture/adr/ADR-035-Client-State-Sync-Opcodes.md}, đang chờ chủ dự án
 *       duyệt. Vì vậy một món đính kèm hiện ra là {@code mail.attachment.raw_item} ("Vật phẩm #12
 *       x3") chứ KHÔNG phải tên món đồ. Đây là một lời thú nhận cố ý: bịa tên vật phẩm ở client
 *       còn tệ hơn hiện một con số, vì con số thì người chơi biết ngay là mình đang thiếu thông
 *       tin, còn cái tên bịa thì không.
 *   <li><b>Người gửi cũng chỉ hiện dạng số.</b> {@code MailView.sender_id} là {@code character_id}
 *       trần; mail.proto không mang tên và client không có gói nào hỏi "id này tên gì". Thư hệ
 *       thống ({@code has_sender = false}) thì có khoá riêng {@code mail.sender.system}.
 *   <li><b>Ô nội dung là một dòng.</b> {@code TextField} của Scene2D không xuống hàng; cột
 *       {@code mail.body} cho tới 1024 ký tự nhưng người chơi gõ chúng trên một hàng cuộn ngang.
 *       Đổi sang {@code TextArea} là một quyết định giao diện riêng, chưa làm ở lát cắt này.
 * </ul>
 */
public final class MailPanel implements HudWindow {

    /**
     * Phím mở khung. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với phím T ({@link ChatPanel}), F ({@link FriendPanel}), G ({@link GuildPanel}).
     *
     * <p>M ("mail") vì T/P/F/G đã có chủ.
     */
    private static final int OPEN_KEY = Input.Keys.M;

    private static final float PANEL_WIDTH_PX = 620f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float ROW_PAD_PX = 4f;

    /** Bề rộng CỐ ĐỊNH cho nút hành động trên từng dòng — nhãn dài ngắn khác nhau không làm cột nhảy. */
    private static final float ROW_BUTTON_WIDTH_PX = 84f;

    /** Bề rộng cột chữ của một dòng thư — chừa đúng chỗ cho ba nút bên phải. */
    private static final float ROW_TEXT_WIDTH_PX = 330f;

    /**
     * Chiều cao TỐI ĐA của vùng cuộn danh sách thư (≈ 3 dòng 52px); dài hơn thì cuộn. Số dòng được
     * DỰNG là {@link ScrollingList#MAX_BUILT_ROWS}.
     *
     * <p>⚠️ Trước 2026-09-11 danh sách không cuộn, trần {@code MailController.MAX_VISIBLE_ROWS = 6}
     * với lý do "không có ScrollPane nên danh sách dài đội khung vượt màn hình". Trần đó vừa GIẤU
     * thư thứ 7 trở đi (không đọc/nhận/xoá được — server không có trần hòm thư nào) vừa KHÔNG làm
     * được việc nó hứa: ở trạng thái tệ nhất (6 thư + một lá dài đúng 1024 ký tự đang mở) khung cao
     * <b>1224px</b> trong màn hình 720px, đo bằng {@code HudLayoutBudgetTool}.
     *
     * <p>Nhỏ hơn các cửa sổ khác vì khung này gánh BA mục (danh sách + đọc + soạn) cùng lúc.
     */
    private static final float MAIL_LIST_MAX_HEIGHT_PX = 156f;

    /**
     * Chiều cao TỐI ĐA của nội dung thư đang đọc (≈ 3 dòng chữ); dài hơn thì cuộn. Cột
     * {@code mail.body} cho tới 1024 ký tự — tự nở theo chữ thì riêng nó đã ~400px, đẩy khung soạn
     * thư và dòng "ESC để đóng" ra khỏi màn hình.
     */
    private static final float READ_BODY_MAX_HEIGHT_PX = 81f;

    /** Ô nhập số vàng hẹp hơn hẳn ba ô chữ — nó chứa số, không chứa câu. */
    private static final float GOLD_FIELD_WIDTH_PX = 110f;

    /** Vàng đồng nhạt — tiêu đề mục, cùng tông viền HUD của {@link KageSkin}. */
    private static final Color SECTION_COLOR = Color.valueOf("E0B860");

    /** Trắng ngà — dòng dữ liệu thường. */
    private static final Color ROW_COLOR = Color.valueOf("F2F2F2");

    /** Xanh lá nhạt — dấu "có thư mới", cùng tông {@code FloatingCombatText.LEVEL_UP}. */
    private static final Color NEW_MAIL_COLOR = Color.valueOf("9CE08A");

    /** Trần ký tự ô tên người nhận — khớp {@code MailController.MAX_RECIPIENT_NAME_LENGTH}. */
    private static final int RECIPIENT_MAX_LENGTH = 16;

    /** Trần ký tự ô tiêu đề — khớp {@code MailController.MAX_SUBJECT_LENGTH} (cột VARCHAR(64), V13). */
    private static final int SUBJECT_MAX_LENGTH = 64;

    /** Trần ký tự ô nội dung — khớp {@code MailController.MAX_BODY_LENGTH} (cột VARCHAR(1024), V13). */
    private static final int BODY_MAX_LENGTH = 1024;

    /** Trần chữ số của ô vàng — 18 chữ số vẫn nằm gọn trong {@code int64} của mail.proto. */
    private static final int GOLD_MAX_LENGTH = 18;

    /**
     * Một dòng thư.
     *
     * @param mailId id server cấp, gửi thẳng lại cho server ở lệnh Nhận/Xoá. Khác tên nhân vật ở
     *     {@link GuildPanel.MemberRow}, đây là số do server sinh nên không qua sanitize và gửi
     *     ngược lại được nguyên vẹn.
     * @param displayText chuỗi ĐÃ ghép sẵn (người gửi + tiêu đề + vàng + đính kèm + đã nhận chưa),
     *     panel không tự ghép.
     * @param claimable có bày nút Nhận cho dòng này không (thư chưa nhận và có gì để nhận).
     */
    public record MailRow(long mailId, String displayText, boolean claimable) {}

    /**
     * Bốn ô nhập của khung soạn thư, đúng như người chơi đang gõ — CHƯA lọc gì.
     *
     * <p>{@code gold} là CHUỖI chứ không phải số: biến "người chơi gõ gì đó vào ô vàng" thành một
     * con số là một luật (rỗng = 0, chữ = từ chối, số âm = từ chối), và luật thì thuộc về
     * {@code MailController} chứ không thuộc về widget.
     */
    public record Composed(String recipient, String subject, String body, String gold) {}

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final Table panel;
    private final Label statusLabel;
    private final Label newMailLabel;
    private final Table mailList;
    private final Label readHeaderLabel;
    private final Label readBodyLabel;
    private final TextField recipientField;
    private final TextField subjectField;
    private final TextField bodyField;
    private final TextField goldField;

    private LongConsumer onRead;
    private LongConsumer onClaim;
    private LongConsumer onDelete;
    private Consumer<Composed> onSend;
    private Runnable onOpened;

    /**
     * Có được phép mở bằng phím M lúc này không. Mặc định là LUÔN được — panel không biết trên màn
     * hình còn khung nào khác, xem {@link #setCanOpen}.
     */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link GuildPanel}/{@link ChatPanel}.
     */
    public MailPanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.statusLabel = wrappingLabel(SECTION_COLOR);
        this.newMailLabel = new Label(text("mail.notice.new"), skin.labelStyle);
        newMailLabel.setColor(NEW_MAIL_COLOR);
        newMailLabel.setVisible(false);

        this.mailList = new Table();
        this.readHeaderLabel = wrappingLabel(SECTION_COLOR);
        this.readBodyLabel = wrappingLabel(ROW_COLOR);

        this.recipientField = inputField("mail.input.recipient", RECIPIENT_MAX_LENGTH);
        this.subjectField = inputField("mail.input.subject", SUBJECT_MAX_LENGTH);
        this.bodyField = inputField("mail.input.body", BODY_MAX_LENGTH);
        this.goldField = digitsField();

        this.panel = new Table();
        panel.setBackground(skin.panel);
        panel.pad(PANEL_PAD_PX);
        panel.add(titleRow()).width(PANEL_WIDTH_PX).left().row();
        panel.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        panel.add(sectionTitle("mail.section.inbox"))
                .width(PANEL_WIDTH_PX)
                .left()
                .padTop(10f)
                .row();
        panel.add(ScrollingList.wrap(mailList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(MAIL_LIST_MAX_HEIGHT_PX)
                .growX()
                .row();
        panel.add(buildReadSection()).width(PANEL_WIDTH_PX).growX().padTop(10f).row();
        panel.add(buildComposeSection())
                .width(PANEL_WIDTH_PX)
                .growX()
                .padTop(10f)
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

    /** Tiêu đề cửa sổ + dấu "có thư mới" ngay cạnh — xem {@link #setNewMailBadge}. */
    private Table titleRow() {
        Table row = new Table();
        Label title = new Label(text("mail.title"), skin.labelStyle);
        title.setColor(SECTION_COLOR);
        row.add(title).left().padRight(10f);
        row.add(newMailLabel).left().expandX();
        return row;
    }

    /**
     * Ô nhập chữ. Bộ lọc {@link FontFactory#canRender} là bắt buộc: thiếu nó thì ký tự vừa gõ BIẾN
     * MẤT ngay trong ô nhập của chính mình mà không có báo lỗi nào.
     */
    private TextField inputField(String hintKey, int maxLength) {
        TextField field = new TextField("", skin.textFieldStyle);
        field.setMessageText(text(hintKey));
        field.setTextFieldFilter((widget, character) -> FontFactory.canRender(character));
        field.setMaxLength(maxLength);
        return field;
    }

    /**
     * Ô nhập số vàng — chỉ nhận chữ SỐ.
     *
     * <p>Chặn luôn dấu trừ ở tầng gõ phím, chứ không chỉ ở {@code MailController}: cột
     * {@code mail.gold_amount} có {@code CHECK (gold_amount >= 0)} (V13) mà server không kiểm trước
     * khi INSERT, nên số âm lọt lên là một {@code SQLException} chứ không phải một outcome.
     */
    private TextField digitsField() {
        TextField field = new TextField("", skin.textFieldStyle);
        field.setMessageText(text("mail.input.gold"));
        field.setTextFieldFilter((widget, character) -> Character.isDigit(character));
        field.setMaxLength(GOLD_MAX_LENGTH);
        return field;
    }

    /** Mục "Nội dung thư" — chỗ bày lá thư người chơi vừa bấm Đọc. */
    private Table buildReadSection() {
        Table section = new Table();
        section.add(sectionTitle("mail.section.read"))
                .width(PANEL_WIDTH_PX)
                .left()
                .row();
        section.add(readHeaderLabel).width(PANEL_WIDTH_PX).left().padTop(4f).row();
        Table body = new Table();
        body.add(readBodyLabel).growX().left();
        section.add(ScrollingList.wrap(body, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(READ_BODY_MAX_HEIGHT_PX)
                .growX()
                .padTop(4f)
                .row();
        return section;
    }

    /** Mục "Soạn thư" — ba ô chữ + một ô số + nút Gửi. */
    private Table buildComposeSection() {
        Table section = new Table();
        section.add(sectionTitle("mail.section.compose"))
                .width(PANEL_WIDTH_PX)
                .left()
                .row();

        Table firstRow = new Table();
        firstRow.add(recipientField).growX().padRight(6f);
        firstRow.add(goldField).width(GOLD_FIELD_WIDTH_PX);
        section.add(firstRow).width(PANEL_WIDTH_PX).growX().padTop(4f).row();

        section.add(subjectField).width(PANEL_WIDTH_PX).growX().padTop(4f).row();

        Table lastRow = new Table();
        lastRow.add(bodyField).growX().padRight(6f);
        lastRow.add(actionButton("mail.action.send", this::submitCompose)).width(ROW_BUTTON_WIDTH_PX);
        section.add(lastRow).width(PANEL_WIDTH_PX).growX().padTop(4f).row();
        return section;
    }

    /**
     * Cách ĐÓNG phải nằm ngay trên cửa sổ, cùng lý do {@link GuildPanel} có dòng này: cửa sổ che
     * giữa màn hình và chặn toàn bộ phím gameplay — mở nhầm mà không biết thoát ra là một bế tắc
     * thật. Nút Làm mới đi kèm vì mail.proto chỉ đẩy {@code NewMailEvent} chứ không đẩy gì khi thư
     * bị xoá/nhận ở một phiên khác.
     */
    private Table closeHint() {
        Table row = new Table();
        Label hint = new Label(text("mail.hint.close"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        row.add(hint).left().expandX().padRight(6f);
        row.add(actionButton("mail.action.refresh", () -> fire(onOpened))).width(ROW_BUTTON_WIDTH_PX);
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

    private void submitCompose() {
        if (onSend != null) {
            onSend.accept(new Composed(
                    recipientField.getText(), subjectField.getText(), bodyField.getText(), goldField.getText()));
        }
    }

    private static void fire(Runnable listener) {
        if (listener != null) {
            listener.run();
        }
    }

    /**
     * Phím M mở khung, ESC đóng — cùng cơ chế và cùng cái bẫy đã mô tả ở
     * {@link GuildPanel} (mở ở {@code keyUp} chứ không {@code keyDown}; M chỉ MỞ chứ không bật/tắt;
     * đang mở thì NUỐT mọi {@code keyUp} để chữ "t"/"p"/"f"/"g" người chơi gõ trong ô nhập không rơi
     * xuống panel khác và bật cửa sổ ở đó).
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
     * Đang mở hay không. {@code WorldScreen} BẮT BUỘC hỏi câu này trước khi đọc phím gameplay: phím
     * di chuyển/đánh được poll thẳng qua {@code Gdx.input.isKeyPressed}, đi vòng qua lưng
     * {@code InputProcessor}, nên {@code Stage} có nuốt phím hay không cũng không ngăn được chữ "a"
     * trong tiêu đề thư đồng thời làm nhân vật chạy sang trái.
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    /**
     * Mở khung và báo ra ngoài để hỏi lại server.
     *
     * <p>Hỏi lại MỖI lần mở là bắt buộc: {@code NewMailEvent} chỉ được đẩy cho người nhận lúc thư
     * vừa tới và chỉ khi họ đang online (mail.proto) — thư đến lúc offline thì không có gói nào báo
     * cả, hòm thư trên màn hình cũ đi mà không ai nói.
     */
    public void open() {
        panel.setVisible(true);
        // KHÔNG tự chiếm keyboard focus khi mở. {@code TextField} của libGDX chỉ vẽ
        // messageText khi KHÔNG có focus, nên tự lấy focus là tự xoá mất chữ gợi ý của chính
        // ô đó — người chơi nhìn thấy một ô trống không nhãn, không biết phải gõ gì vào.
        // Thấy tận mắt lúc playtest 2026-08-29: bốn panel cùng dính một dòng lệnh này.
        fire(onOpened);
    }

    public void close() {
        panel.setVisible(false);
        // Bỏ focus THẬT: ô nhập còn giữ keyboard focus là còn nuốt phím dù đã ẩn đi.
        stage.setKeyboardFocus(null);
    }

    /** Gọi mỗi lần khung mở ra hoặc người chơi bấm Làm mới — chỗ nghe có nhiệm vụ hỏi lại server. */
    public void setOnRefresh(Runnable listener) {
        this.onOpened = listener;
    }

    /**
     * Điều kiện để phím M được phép mở khung — cổng phím CHUNG của mọi cửa sổ, xem
     * {@link GuildPanel#setCanOpen}. Panel không tự biết trên màn hình còn ô nhập nào khác đang mở
     * (nó không được biết — UI.md §4), nên {@code WorldScreen} là chỗ cung cấp câu trả lời.
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Id lá thư người chơi vừa bấm Đọc. */
    public void setOnRead(LongConsumer listener) {
        this.onRead = listener;
    }

    /** Id lá thư người chơi vừa bấm Nhận — khoá idempotency sinh ở coordinator, không ở đây. */
    public void setOnClaim(LongConsumer listener) {
        this.onClaim = listener;
    }

    /** Id lá thư người chơi vừa bấm Xoá. */
    public void setOnDelete(LongConsumer listener) {
        this.onDelete = listener;
    }

    /** Bốn ô nhập lúc bấm Gửi — có thể rỗng hoặc sai, lọc là việc của controller. */
    public void setOnSend(Consumer<Composed> listener) {
        this.onSend = listener;
    }

    /** Dòng trạng thái dưới tiêu đề — chuỗi ĐÃ dịch (khoá i18n tra ở coordinator, chỗ biết outcome). */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * Bày/giấu dấu "có thư mới" cạnh tiêu đề.
     *
     * <p>Không dùng MÀU làm thông tin duy nhất (client/CLAUDE.md): dấu này là một dòng CHỮ, màu chỉ
     * để nó bắt mắt hơn.
     */
    public void setNewMailBadge(boolean visible) {
        newMailLabel.setVisible(visible);
    }

    /**
     * Vẽ lại danh sách thư. Dựng lại toàn bộ dòng mỗi lần gọi — danh sách chỉ đổi khi server vừa trả
     * lời (vài lần một phiên chơi), không phải mỗi khung, nên đây không nằm trong đường cấm cấp phát
     * của game loop (Điều 31); cùng lập luận {@link GuildPanel#setMembers}.
     *
     * @param rows đã cắt xuống số dòng hiển thị được ở coordinator.
     * @param emptyOrOverflowNotice chuỗi đã dịch cho hòm thư rỗng hoặc phần bị cắt bớt; rỗng nếu
     *     không có gì phải nói thêm.
     */
    public void setMails(List<MailRow> rows, String emptyOrOverflowNotice) {
        mailList.clear();
        for (MailRow row : rows) {
            addMailRow(row);
        }
        if (!emptyOrOverflowNotice.isEmpty()) {
            mailList.add(rowLabel(emptyOrOverflowNotice))
                    .colspan(4)
                    .growX()
                    .left()
                    .row();
        }
    }

    private void addMailRow(MailRow row) {
        Label label = rowLabel(row.displayText());
        label.setEllipsis(true);
        mailList.add(label).width(ROW_TEXT_WIDTH_PX).left().padRight(6f).padBottom(ROW_PAD_PX);
        mailList.add(actionButton("mail.action.read", () -> emit(onRead, row.mailId())))
                .width(ROW_BUTTON_WIDTH_PX)
                .padRight(6f)
                .padBottom(ROW_PAD_PX);
        if (row.claimable()) {
            mailList.add(actionButton("mail.action.claim", () -> emit(onClaim, row.mailId())))
                    .width(ROW_BUTTON_WIDTH_PX)
                    .padRight(6f)
                    .padBottom(ROW_PAD_PX);
        } else {
            mailList.add().width(ROW_BUTTON_WIDTH_PX).padRight(6f);
        }
        mailList.add(actionButton("mail.action.delete", () -> emit(onDelete, row.mailId())))
                .width(ROW_BUTTON_WIDTH_PX)
                .padBottom(ROW_PAD_PX);
        mailList.row();
    }

    /**
     * Bày nội dung một lá thư.
     *
     * @param header chuỗi đã dịch: người gửi + tiêu đề + vàng/đính kèm.
     * @param body nội dung thư, đã qua sanitize ở controller.
     */
    public void showMail(String header, String body) {
        readHeaderLabel.setText(header);
        readBodyLabel.setText(body);
    }

    /**
     * Chưa chọn lá nào (hoặc lá đang đọc vừa biến mất khỏi danh sách) — nói ra bằng một lời mời chọn
     * thư, chứ không để nguyên nội dung cũ treo lại trên màn hình.
     *
     * @param hint chuỗi đã dịch.
     */
    public void showNoMail(String hint) {
        readHeaderLabel.setText(hint);
        readBodyLabel.setText("");
    }

    /** Dọn khung soạn thư sau khi server nhận thư — không để người chơi gửi trùng vì tưởng chưa gửi. */
    public void clearComposer() {
        recipientField.setText("");
        subjectField.setText("");
        bodyField.setText("");
        goldField.setText("");
    }

    private static void emit(LongConsumer listener, long mailId) {
        if (listener != null) {
            listener.accept(mailId);
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

    /** KHÔNG dispose {@link KageSkin} nhận ở constructor — của {@link CombatHud}, xem javadoc đó. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
