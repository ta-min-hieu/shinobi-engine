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
 * Khung bang hội (2026-08-25) — tạo bang / xin gia nhập / duyệt đơn / danh sách thành viên, mở bằng
 * phím G, neo GIỮA màn hình.
 *
 * <p>Giữa màn hình chứ không phải một góc như {@link ChatPanel}/{@link CombatHud}: bốn góc đã có
 * chủ, và khác khung chat (đọc liếc qua trong lúc đánh nhau), đây là một cửa sổ quản trị — mở ra là
 * để làm việc với nó, không phải để vừa nhìn vừa chơi. Đó cũng là lý do nó chặn toàn bộ phím
 * gameplay khi mở (xem {@link #isOpen()}).
 *
 * <p>{@code Stage} RIÊNG cùng lý do {@link DialoguePanel}/{@link ChatPanel}: thứ tự nuốt input chỉ
 * đặt được cho cả một {@code Stage} qua {@code InputMultiplexer}. Click vào khung này phải bị nuốt
 * chứ không rơi xuống thế giới thành lệnh chọn mục tiêu.
 *
 * <p>Widget chỉ VẼ (UI.md §4): nó không biết opcode nào, không dựng packet, không biết ai được phép
 * đuổi ai. Nó nhận các dòng ĐÃ ghép sẵn thành chuỗi hiển thị và báo ra ý định người chơi vừa bấm.
 * Chữ tĩnh của chính khung (tiêu đề, nhãn nút) thì tra i18n tại chỗ theo khoá CỐ ĐỊNH của lớp này —
 * cùng cách {@link DialoguePanel} làm, vì đó là chữ của giao diện chứ không phải dữ liệu từ mạng.
 */
public final class GuildPanel implements HudWindow {

    /**
     * Phím mở khung. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với phím T của {@link ChatPanel} và ESC của {@link DialoguePanel}.
     *
     * <p>G ("guild") vì T (chat) và P (nhóm) đã có chủ.
     */
    private static final int OPEN_KEY = Input.Keys.G;

    private static final float PANEL_WIDTH_PX = 560f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float ROW_PAD_PX = 4f;

    /**
     * Bề rộng CỐ ĐỊNH cho nút hành động trên từng dòng — nhãn dài ngắn khác nhau không được làm cột
     * nhảy. 92 → 100 (2026-10-01): "Lập bang" chạm sát viền — đo bằng {@code HudButtonLabelFitTest}.
     */
    static final float ROW_BUTTON_WIDTH_PX = 100f;

    /**
     * Chiều cao TỐI ĐA của vùng cuộn thành viên (≈ 3 dòng 52px) và của vùng cuộn đơn xin gia nhập
     * (≈ 2 dòng); dài hơn thì cuộn. Số dòng được DỰNG là {@link ScrollingList#MAX_BUILT_ROWS} — server
     * không có trần thành viên hay trần đơn nào (V14 không có cột sức chứa, không {@code LIMIT}).
     *
     * <p>⚠️ Trước 2026-09-11 là trần {@code GuildController.MAX_VISIBLE_ROWS = 8} cho MỖI danh sách
     * với lý do "chưa có ScrollPane nên danh sách dài đội khung vượt màn hình". Hai danh sách 8 dòng
     * cùng lúc (trạng thái của bang chủ) cao <b>1261px</b> trong màn hình 720px — trần đó chưa bao giờ
     * làm được việc nó hứa, chỉ giấu thành viên thứ 9 và đơn thứ 9 (không duyệt được).
     */
    private static final float MEMBER_LIST_MAX_HEIGHT_PX = 156f;

    /** Xem {@link #MEMBER_LIST_MAX_HEIGHT_PX}. Thấp hơn vì chỉ bang chủ thấy, và chỉ lúc có đơn. */
    private static final float PENDING_LIST_MAX_HEIGHT_PX = 104f;

    /** Vàng đồng nhạt — tiêu đề mục, cùng tông viền HUD của {@link KageSkin}. */
    private static final Color SECTION_COLOR = Color.valueOf("E0B860");

    /** Trắng ngà — dòng dữ liệu thường. */
    private static final Color ROW_COLOR = Color.valueOf("F2F2F2");

    /**
     * Một dòng thành viên.
     *
     * @param displayText chuỗi ĐÃ ghép sẵn (tên + vai trò + trực tuyến), panel không tự ghép.
     * @param kickTargetName tên nhân vật gửi kèm {@code KickGuildMemberCommand} — guild.proto đuổi
     *     theo TÊN chứ không theo id, nên đây là tên thô chứ không phải chuỗi hiển thị.
     * @param kickable có bày nút Đuổi cho dòng này không (chỉ leader, và không phải chính mình).
     */
    public record MemberRow(String displayText, String kickTargetName, boolean kickable) {}

    /** Một dòng đơn xin gia nhập — {@code requestId} là thứ duy nhất gửi lại server (guild.proto). */
    public record RequestRow(long requestId, String displayText) {}

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final Table panel;
    private final Label statusLabel;
    private final Label headerLabel;
    private final Label progressLabel;
    private final Table joinForm;
    private final TextField nameField;
    private final Table guildSection;
    private final Table memberList;
    private final Table pendingSection;
    private final Table pendingList;
    private final TextButton leaveButton;
    private final TextButton disbandButton;

    private Consumer<String> onCreate;
    private Consumer<String> onJoin;
    private Consumer<String> onKick;
    private LongConsumer onApprove;
    private LongConsumer onReject;
    private Runnable onLeave;
    private Runnable onDisband;
    private Runnable onDonate;
    private Runnable onOpened;

    /**
     * Có được phép mở bằng phím G lúc này không. Mặc định là LUÔN được — panel không biết trên màn
     * hình còn khung nào khác, xem {@link #setCanOpen}.
     */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link DialoguePanel}/{@link ChatPanel}.
     */
    public GuildPanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.headerLabel = new Label("", skin.labelStyle);
        this.progressLabel = new Label("", skin.labelStyle);
        progressLabel.setWrap(true);
        this.statusLabel = new Label("", skin.labelStyle);
        statusLabel.setWrap(true);
        statusLabel.setColor(SECTION_COLOR);

        this.nameField = new TextField("", skin.textFieldStyle);
        nameField.setMessageText(text("guild.name.hint"));
        // Gõ được đúng những gì font vẽ được — nếu không, ký tự vừa gõ BIẾN MẤT ngay trong ô nhập
        // của chính mình mà không có báo lỗi nào (xem FontFactory#canRender).
        nameField.setTextFieldFilter((field, character) -> FontFactory.canRender(character));
        nameField.setMaxLength(maxNameLengthGuard());

        this.joinForm = buildJoinForm();
        this.memberList = new Table();
        this.pendingList = new Table();
        this.leaveButton = actionButton("guild.action.leave", () -> fire(onLeave));
        this.disbandButton = actionButton("guild.action.disband", () -> fire(onDisband));
        this.pendingSection = buildPendingSection();
        this.guildSection = buildGuildSection();

        this.panel = new Table();
        panel.setBackground(skin.panel);
        panel.pad(PANEL_PAD_PX);
        panel.add(title()).width(PANEL_WIDTH_PX).left().row();
        panel.add(headerLabel).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        panel.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        panel.add(joinForm).width(PANEL_WIDTH_PX).growX().padTop(10f).row();
        panel.add(guildSection).width(PANEL_WIDTH_PX).growX().padTop(10f).row();
        panel.setVisible(false);

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.center).add(panel);
        stage.addActor(container);

        installKeyHandling();
    }

    /**
     * Trần ký tự của ô nhập — chặn tại chỗ thay vì để người chơi gõ 60 chữ rồi mới biết hỏng. Hằng
     * số thật nằm ở {@code GuildController.MAX_NAME_LENGTH} (world/) mà lớp này ở ui/hud/ nên không
     * với tới được; 32 là chiều rộng cột {@code guild.name} trong V14, và
     * {@code GuildController.composeGuildName} vẫn kiểm lại lần nữa nên đây chỉ là hàng rào dễ chịu,
     * không phải nguồn chân lý.
     */
    private static int maxNameLengthGuard() {
        return 32;
    }

    private Label title() {
        Label label = new Label(text("guild.title"), skin.labelStyle);
        label.setColor(SECTION_COLOR);
        return label;
    }

    /** Ô nhập tên + 2 nút — dùng chung cho tạo bang và xin gia nhập (cả hai chỉ cần một cái tên). */
    private Table buildJoinForm() {
        Table form = new Table();
        form.add(nameField).growX().padRight(6f);
        form.add(actionButton("guild.action.create", () -> submitName(onCreate)))
                .width(ROW_BUTTON_WIDTH_PX)
                .padRight(6f);
        form.add(actionButton("guild.action.join", () -> submitName(onJoin))).width(ROW_BUTTON_WIDTH_PX);
        return form;
    }

    private Table buildPendingSection() {
        Table section = new Table();
        section.add(sectionTitle("guild.section.pending"))
                .width(PANEL_WIDTH_PX)
                .left()
                .row();
        section.add(ScrollingList.wrap(pendingList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(PENDING_LIST_MAX_HEIGHT_PX)
                .growX()
                .row();
        return section;
    }

    private Table buildGuildSection() {
        Table section = new Table();
        section.add(progressLabel).width(PANEL_WIDTH_PX).left().padBottom(6f).row();
        section.add(sectionTitle("guild.section.members"))
                .width(PANEL_WIDTH_PX)
                .left()
                .row();
        section.add(ScrollingList.wrap(memberList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(MEMBER_LIST_MAX_HEIGHT_PX)
                .growX()
                .row();
        section.add(pendingSection).width(PANEL_WIDTH_PX).growX().padTop(10f).row();

        Table footer = new Table();
        footer.add(leaveButton).width(ROW_BUTTON_WIDTH_PX).padRight(6f);
        footer.add(disbandButton).width(ROW_BUTTON_WIDTH_PX).padRight(6f);
        footer.add(actionButton("guild.action.donate", () -> fire(onDonate)))
                .width(ROW_BUTTON_WIDTH_PX)
                .padRight(6f);
        footer.add(actionButton("guild.action.refresh", () -> fire(onOpened))).width(ROW_BUTTON_WIDTH_PX);
        section.add(footer).width(PANEL_WIDTH_PX).left().padTop(10f).row();
        // Cách ĐÓNG phải nằm ngay trên cửa sổ, cùng lý do PartyPanel/FriendPanel đã có dòng này:
        // cửa sổ che giữa màn hình và chặn toàn bộ phím gameplay, mở nhầm mà không biết thoát ra là
        // một bế tắc thật.
        Label hint = new Label(text("guild.hint.close"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        section.add(hint).width(PANEL_WIDTH_PX).left().padTop(8f).row();
        return section;
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

    private void submitName(Consumer<String> listener) {
        if (listener != null) {
            listener.accept(nameField.getText());
        }
    }

    private static void fire(Runnable listener) {
        if (listener != null) {
            listener.run();
        }
    }

    /**
     * Phím G mở khung, ESC đóng.
     *
     * <p>Mở ở <b>{@code keyUp}</b> chứ không {@code keyDown} — và đó không phải chuyện thẩm mỹ, xem
     * {@link ChatPanel} về cùng cái bẫy: giữ phím sinh chuỗi {@code keyDown}/{@code keyTyped} lặp
     * lại, mở ở {@code keyDown} thì ngay khi {@link #nameField} nhận keyboard focus, những lần lặp
     * còn lại của CHÍNH phím vừa bấm chảy thẳng vào ô nhập thành "gggg".
     *
     * <p>G chỉ MỞ chứ không bật/tắt: khung đang mở thì ô nhập đang có focus, và một phím vừa mở vừa
     * đóng sẽ đóng khung ngay lúc người chơi gõ chữ "g" trong tên bang.
     *
     * <p><b>Đang mở thì NUỐT mọi {@code keyUp}</b>, không chỉ phím G. Đây là chỗ hai ô nhập text
     * trên hai {@code Stage} khác nhau đâm vào nhau lần đầu trong dự án: {@code InputMultiplexer}
     * chuyển {@code keyUp} xuống tầng dưới cho tới khi có ai trả {@code true}, mà mỗi panel chỉ nhận
     * ra phím MỞ của chính nó — nên gõ chữ "t" trong tên bang sẽ rơi xuống {@link ChatPanel} và mở ô
     * chat. Nuốt hết khi đang mở là cách chặn chuyện đó mà không panel nào phải biết panel nào tồn
     * tại. Không nuốt {@code keyDown} vì ESC (đóng khung) nằm ở đó.
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
     * trong tên bang đồng thời làm nhân vật chạy sang trái.
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    /**
     * Mở khung và báo ra ngoài để hỏi lại server.
     *
     * <p>Hỏi lại MỖI lần mở là bắt buộc chứ không phải thừa: guild.proto không có event đẩy nào, nên
     * dữ liệu trên màn hình cũ đi ngay khi người khác thao tác (xem {@code GuildController}).
     */
    public void open() {
        nameField.setText("");
        panel.setVisible(true);
        // KHÔNG tự chiếm keyboard focus khi mở. {@code TextField} của libGDX chỉ vẽ
        // messageText khi KHÔNG có focus, nên tự lấy focus là tự xoá mất chữ gợi ý của chính
        // ô đó — người chơi nhìn thấy một ô trống không nhãn, không biết phải gõ gì vào.
        // Thấy tận mắt lúc playtest 2026-08-29: bốn panel cùng dính một dòng lệnh này.
        fire(onOpened);
    }

    public void close() {
        panel.setVisible(false);
        nameField.setText("");
        // Bỏ focus THẬT: ô nhập còn giữ keyboard focus là còn nuốt phím dù đã ẩn đi.
        stage.setKeyboardFocus(null);
    }

    /** Gọi mỗi lần khung mở ra hoặc người chơi bấm Làm mới — chỗ nghe có nhiệm vụ hỏi lại server. */
    public void setOnRefresh(Runnable listener) {
        this.onOpened = listener;
    }

    /**
     * Điều kiện để phím G được phép mở khung — nửa còn lại của cái bẫy mô tả ở
     * {@link #installKeyHandling()}: {@code Stage} này đứng TRƯỚC {@link ChatPanel} trong
     * {@code InputMultiplexer}, nên chữ "g" người chơi gõ trong ô chat cũng rơi tới đây và sẽ mở
     * khung bang hội nếu không ai chặn.
     *
     * <p>Panel không tự biết chuyện đó (nó không được biết khung chat tồn tại — UI.md §4), nên
     * {@code WorldScreen} — nơi duy nhất nhìn thấy cả hai — là chỗ cung cấp câu trả lời.
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Tên bang người chơi vừa gõ khi bấm Tạo bang — có thể rỗng, lọc là việc của controller. */
    public void setOnCreate(Consumer<String> listener) {
        this.onCreate = listener;
    }

    /** Tên bang người chơi vừa gõ khi bấm Xin gia nhập — xem {@link #setOnCreate}. */
    public void setOnJoin(Consumer<String> listener) {
        this.onJoin = listener;
    }

    /** Tên nhân vật bị đuổi (guild.proto đuổi theo TÊN, xem {@link MemberRow}). */
    public void setOnKick(Consumer<String> listener) {
        this.onKick = listener;
    }

    public void setOnApprove(LongConsumer listener) {
        this.onApprove = listener;
    }

    public void setOnReject(LongConsumer listener) {
        this.onReject = listener;
    }

    public void setOnLeave(Runnable listener) {
        this.onLeave = listener;
    }

    /** ADR-068 — nút "Góp" (một bậc linh thạch). */
    public void setOnDonate(Runnable listener) {
        this.onDonate = listener;
    }

    /** ADR-068/069 — cấp bang, cống hiến, nhiệm vụ tuần; chuỗi ĐÃ dịch. */
    public void setProgress(String text) {
        progressLabel.setText(text);
    }

    public void setOnDisband(Runnable listener) {
        this.onDisband = listener;
    }

    /** Dòng trạng thái dưới tiêu đề — chuỗi ĐÃ dịch (khoá i18n tra ở coordinator, chỗ biết outcome). */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * Chưa ở bang nào: bày form tạo/xin gia nhập, giấu mọi thứ của bang.
     *
     * @param header chuỗi đã dịch, giải thích tình trạng hiện tại.
     */
    public void showNoGuild(String header) {
        headerLabel.setText(header);
        joinForm.setVisible(true);
        guildSection.setVisible(false);
        memberList.clear();
        pendingList.clear();
    }

    /**
     * Đang ở trong một bang: giấu form, bày danh sách.
     *
     * <p>Leader thấy nút Giải tán, thành viên thường thấy nút Rời bang — KHÔNG bày cả hai. Server trả
     * {@code LEADER_CANNOT_LEAVE}/{@code NOT_LEADER} cho nửa còn lại, tức bày ra là bày một nút chắc
     * chắn hỏng (cùng lập luận {@code ChatCoordinator} dùng để không bày kênh Bang hội khi chưa có
     * bang).
     *
     * @param header chuỗi đã dịch: tên bang + vai trò của mình.
     * @param leader mình có phải leader không — theo lời server, không phải client tự suy.
     */
    public void showGuild(String header, boolean leader) {
        headerLabel.setText(header);
        joinForm.setVisible(false);
        guildSection.setVisible(true);
        pendingSection.setVisible(leader);
        leaveButton.setVisible(!leader);
        disbandButton.setVisible(leader);
    }

    /**
     * Vẽ lại danh sách thành viên. Dựng lại toàn bộ dòng mỗi lần gọi — danh sách chỉ đổi khi server
     * vừa trả lời (vài lần một phiên chơi), không phải mỗi khung, nên đây không nằm trong đường cấm
     * cấp phát của game loop (Điều 31); cùng lập luận {@link DialoguePanel#show}.
     *
     * @param rows đã cắt xuống số dòng hiển thị được ở coordinator.
     * @param overflowNotice chuỗi đã dịch cho phần bị cắt bớt, hoặc rỗng nếu không cắt gì.
     */
    public void setMembers(List<MemberRow> rows, String overflowNotice) {
        memberList.clear();
        for (MemberRow row : rows) {
            // minWidth(0) + ellipsis: tên bang/tên thành viên dài đòi bề rộng lớn hơn vùng cuộn,
            // mà trục ngang bị khoá nên không có thanh cuộn — bảng bị căn giữa và tràn ra CẢ HAI
            // mép, ăn mất chữ đầu của tên lẫn nút bên phải. Đo được bằng HudLayoutBudgetTool
            // (2026-09-12: Bang hội tràn 22px), không thấy bằng build xanh.
            Label memberLabel = rowLabel(row.displayText());
            memberLabel.setEllipsis(true);
            memberList.add(memberLabel).growX().minWidth(0f).left().padBottom(ROW_PAD_PX);
            if (row.kickable()) {
                memberList
                        .add(actionButton("guild.action.kick", () -> kick(row.kickTargetName())))
                        .width(ROW_BUTTON_WIDTH_PX)
                        .padBottom(ROW_PAD_PX);
            } else {
                memberList.add().width(ROW_BUTTON_WIDTH_PX);
            }
            memberList.row();
        }
        appendOverflow(memberList, overflowNotice);
    }

    /**
     * Vẽ lại danh sách đơn xin gia nhập — chỉ leader nhìn thấy mục này ({@link #showGuild}).
     *
     * @param rows đã cắt xuống số dòng hiển thị được ở coordinator.
     * @param overflowNotice chuỗi đã dịch cho phần bị cắt bớt, hoặc rỗng nếu không cắt gì.
     */
    public void setPendingRequests(List<RequestRow> rows, String overflowNotice) {
        pendingList.clear();
        for (RequestRow row : rows) {
            // Cùng lý do ô tên ở setMembers, và hàng này còn chật hơn: HAI nút bên phải.
            Label requestLabel = rowLabel(row.displayText());
            requestLabel.setEllipsis(true);
            pendingList.add(requestLabel).growX().minWidth(0f).left().padBottom(ROW_PAD_PX);
            pendingList
                    .add(actionButton("guild.action.approve", () -> approve(row.requestId())))
                    .width(ROW_BUTTON_WIDTH_PX)
                    .padRight(6f)
                    .padBottom(ROW_PAD_PX);
            pendingList
                    .add(actionButton("guild.action.reject", () -> reject(row.requestId())))
                    .width(ROW_BUTTON_WIDTH_PX)
                    .padBottom(ROW_PAD_PX);
            pendingList.row();
        }
        appendOverflow(pendingList, overflowNotice);
    }

    private void appendOverflow(Table list, String overflowNotice) {
        if (!overflowNotice.isEmpty()) {
            list.add(rowLabel(overflowNotice)).colspan(3).growX().left().row();
        }
    }

    private Label rowLabel(String displayText) {
        Label label = new Label(displayText, skin.labelStyle);
        label.setColor(ROW_COLOR);
        return label;
    }

    private void kick(String targetName) {
        if (onKick != null) {
            onKick.accept(targetName);
        }
    }

    private void approve(long requestId) {
        if (onApprove != null) {
            onApprove.accept(requestId);
        }
    }

    private void reject(long requestId) {
        if (onReject != null) {
            onReject.accept(requestId);
        }
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

    /** KHÔNG dispose {@link KageSkin} nhận ở constructor — của {@link CombatHud}, xem javadoc đó. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
