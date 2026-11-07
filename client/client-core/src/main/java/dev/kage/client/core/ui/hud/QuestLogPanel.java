package dev.kage.client.core.ui.hud;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

import dev.kage.client.core.input.KeyBindings;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Nhật ký nhiệm vụ (ADR-035, opcode 610/611) — danh sách MỌI nhiệm vụ đã nhận + trạng thái + nút
 * Lĩnh thưởng cho nhiệm vụ hoàn thành, mở bằng phím J, neo GIỮA màn hình. Cùng hình dạng
 * {@link MailPanel}: một cửa sổ quản trị, chặn toàn bộ phím gameplay khi mở (xem {@link #isOpen()}).
 *
 * <p><b>Đây là cửa sổ vá đúng một bug thật</b>: trước ADR-035, nhiệm vụ đang theo dõi (hiện ở góc
 * màn hình qua {@link CombatHud#setQuest}) chỉ sống trong biến cục bộ phía client, đọc từ chuỗi
 * {@code action} của hội thoại lúc nhận nhiệm vụ. Đăng xuất rồi đăng nhập lại xoá sạch biến đó, và
 * quest.proto không có opcode nào hỏi lại được "tôi đang làm nhiệm vụ gì" — server thì nhớ đủ
 * (bảng {@code character_quest_progress}), client thì mất trắng, kể cả với một nhiệm vụ đã HOÀN
 * THÀNH và chỉ còn chờ bấm nhận thưởng. Cửa sổ này hỏi lại server ({@code ListQuestsRequest}) mỗi
 * lần mở VÀ mỗi lần vào world, nên nhiệm vụ hoàn thành TRƯỚC lúc relog vẫn hiện ra với nút Lĩnh
 * thưởng còn sống — xem {@code QuestCoordinator}/{@code QuestTracker} (package {@code core.world})
 * về phần trạng thái.
 *
 * <p>{@code Stage} RIÊNG cùng lý do {@link MailPanel}/{@link DialoguePanel}: thứ tự nuốt input chỉ
 * đặt được cho cả một {@code Stage} qua {@code InputMultiplexer}. Click vào khung này phải bị nuốt
 * chứ không rơi xuống thế giới thành lệnh chọn mục tiêu.
 *
 * <p>Widget chỉ VẼ (UI.md §4): nó không biết opcode nào, không dựng packet, không sinh khoá
 * idempotency, không tự quyết nhiệm vụ nào hoàn thành — nó nhận các dòng ĐÃ dịch sẵn thành chuỗi
 * hiển thị và báo ra đúng một ý định ("người chơi vừa bấm Nhận thưởng ở dòng có mã này").
 *
 * <p>⚠️ Không hiện tiến độ chi tiết kiểu "đã giết 2/3": {@code QuestLogEntry} (quest.proto)
 * KHÔNG mang {@code progress_json} — đó là chuỗi đục do script GraalJS tự quản, Java không đọc
 * được. Client chỉ có bốn mốc {@code QuestStatus} để hiển thị (chưa xác định/đang làm/hoàn
 * thành/đã nhận thưởng), và đó là ranh giới CỐ Ý của ADR-035, không phải một khoảng trống cần vá.
 */
public final class QuestLogPanel implements HudWindow {

    /**
     * Phím mở khung. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với phím T ({@link ChatPanel}), P ({@link PartyPanel}), F ({@link FriendPanel}),
     * G ({@link GuildPanel}), K ({@link DuelPanel}), H ({@link FactionPanel}), M ({@link MailPanel}).
     *
     * <p>J ("journal" — nhật ký) vì tám phím trên đã có chủ.
     */
    private static final int OPEN_KEY = Input.Keys.J;

    private static final float PANEL_WIDTH_PX = 520f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float ROW_PAD_PX = 4f;

    /**
     * Bề rộng TỐI THIỂU của nút trên từng dòng — cột không nhảy khi nhãn ngắn, nhưng nhãn dài thì
     * nút nở ra theo.
     *
     * <p>⚠️ Trước 2026-09-05 đây là bề rộng CỐ ĐỊNH ({@code .width(...)}). Playtest chụp được hậu
     * quả: nhãn tiếng Việt "Nhận thưởng" cần ~125px nên **chữ tràn ra NGOÀI nền nút ở cả hai phía**
     * — nút vẽ 96px, chữ thò ra hai bên, trông như lỗi vẽ. Nhãn tiếng Anh ("Claim reward") và
     * "Làm mới" thì vừa, nên chỉ nhìn một ngôn ngữ/một nút là không thấy.
     *
     * <p>Bài học chung cho mọi panel: bề rộng CỐ ĐỊNH tính theo mắt trên MỘT chuỗi là bẫy — chuỗi
     * dịch sang ngôn ngữ khác dài ngắn tuỳ ý. Dùng tối thiểu + để nút tự đo.
     */
    private static final float ROW_BUTTON_MIN_WIDTH_PX = 96f;

    /** Đệm trái/phải trong lòng nút — nền nút ({@code KageSkin.buttonStyle.up}) không tự có padding. */
    private static final float BUTTON_TEXT_PAD_PX = 14f;

    /** Bề rộng cột chữ của một dòng nhiệm vụ — chừa đúng chỗ cho nút bên phải. */
    private static final float ROW_TEXT_WIDTH_PX = 340f;

    /**
     * Chiều cao TỐI ĐA của vùng cuộn nhật ký (≈ 7 dòng 52px); dài hơn thì cuộn. Số dòng được DỰNG
     * là {@link ScrollingList#MAX_BUILT_ROWS} — trước 2026-09-11 là {@code QuestTracker.MAX_VISIBLE_ROWS
     * = 8} với lý do "chưa có ScrollPane" đã chết. Chưa giấu gì thật (server mới seed 5 nhiệm vụ),
     * nhưng nhiệm vụ thứ 9 sẽ biến mất khỏi nhật ký — cùng loại lỗi đã giấu mất đan dược ở túi đồ.
     */
    private static final float QUEST_LIST_MAX_HEIGHT_PX = 364f;

    /** Vàng đồng nhạt — tiêu đề mục, cùng tông viền HUD của {@link KageSkin}. */
    private static final Color SECTION_COLOR = Color.valueOf("E0B860");

    /** Trắng ngà — dòng dữ liệu thường. */
    private static final Color ROW_COLOR = Color.valueOf("F2F2F2");

    /**
     * Một dòng nhật ký.
     *
     * @param questCode mã nhiệm vụ — gửi thẳng lại cho server ở {@code ClaimQuestRewardCommand} nếu
     *     người chơi bấm Nhận thưởng trên dòng này (chính là {@code quest_code} bị mất sau relog mà
     *     cửa sổ này khôi phục lại được — xem javadoc lớp).
     * @param displayText chuỗi ĐÃ ghép sẵn (tên nhiệm vụ + trạng thái), panel không tự ghép.
     * @param claimable có bày nút Lĩnh thưởng cho dòng này không.
     * @param abandonable có bày nút Bỏ cho dòng này không (ADR-054 — chỉ nhiệm vụ đang làm). Một dòng
     *     không bao giờ vừa lĩnh được vừa bỏ được, nên hai nút dùng chung MỘT ô.
     */
    public record QuestRow(String questCode, String displayText, boolean claimable, boolean abandonable) {}

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final Table panel;
    private final Label statusLabel;
    private final Table questList;

    private Consumer<String> onClaim;
    private Consumer<String> onAbandon;
    private Runnable onOpened;

    /**
     * Mã nhiệm vụ đang chờ bấm XÁC NHẬN bỏ (bấm Bỏ lần một), rỗng nếu không có. Xác nhận dựng ngay
     * trong cửa sổ: bỏ nhiệm vụ xoá tiến độ và không trả đồ đã giao, một cú bấm nhầm không được đủ.
     * Chỉ là trạng thái HIỂN THỊ — server vẫn tự kiểm mọi thứ (Điều 15).
     */
    private String armedAbandonQuestCode = "";

    /** Bản sao các dòng đang vẽ — để vẽ lại khi bấm Bỏ lần một đổi nhãn nút. */
    private List<QuestRow> shownRows = List.of();

    private String shownNotice = "";

    /**
     * Có được phép mở bằng phím J lúc này không. Mặc định là LUÔN được — panel không biết trên màn
     * hình còn khung nào khác, xem {@link #setCanOpen}.
     */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link MailPanel}/{@link GuildPanel}.
     */
    public QuestLogPanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.statusLabel = wrappingLabel(SECTION_COLOR);
        this.questList = new Table();

        this.panel = new Table();
        panel.setBackground(skin.panel);
        panel.pad(PANEL_PAD_PX);
        panel.add(titleLabel()).width(PANEL_WIDTH_PX).left().row();
        panel.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(6f).row();
        panel.add(ScrollingList.wrap(questList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(QUEST_LIST_MAX_HEIGHT_PX)
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

    private Label titleLabel() {
        Label title = new Label(text("questlog.title"), skin.labelStyle);
        title.setColor(SECTION_COLOR);
        return title;
    }

    /**
     * Cách ĐÓNG phải nằm ngay trên cửa sổ, cùng lý do {@link MailPanel} có dòng này: cửa sổ che giữa
     * màn hình và chặn toàn bộ phím gameplay. Nút Làm mới đi kèm vì nhật ký chỉ chắc chắn mới nhất
     * ngay sau khi vừa hỏi lại server — quest.proto không đẩy sự kiện nào cho nhiệm vụ đổi trạng
     * thái trong lúc client không theo dõi nó (VD nhận trên máy khác, hoặc trước khi mở cửa sổ này).
     */
    private Table closeHint() {
        Table row = new Table();
        Label hint = new Label(text("questlog.hint.close"), skin.labelStyle);
        hint.setColor(SECTION_COLOR);
        row.add(hint).left().expandX().padRight(6f);
        row.add(actionButton("questlog.action.refresh", () -> fire(onOpened))).minWidth(ROW_BUTTON_MIN_WIDTH_PX);
        return row;
    }

    private TextButton actionButton(String labelKey, Runnable action) {
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

    /**
     * Phím J mở khung, ESC đóng — cùng cơ chế và cùng cái bẫy đã mô tả ở {@link GuildPanel} (mở ở
     * {@code keyUp} chứ không {@code keyDown}; J chỉ MỞ chứ không bật/tắt; đang mở thì NUỐT mọi
     * {@code keyUp} để không có ô nhập nào ở cửa sổ này — nhưng vẫn giữ đúng khuôn để nếu sau này
     * thêm ô tìm kiếm thì hành vi không đổi).
     */
    private void installKeyHandling() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (isOpen()) {
                    return true; // xem javadoc: chặn phím rơi xuống panel khác trong lúc đang mở
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
     * Đang mở hay không. {@code WorldScreen} BẮT BUỘC hỏi câu này trước khi đọc phím gameplay —
     * cùng lý do {@link MailPanel#isOpen()}.
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    /**
     * Mở khung và báo ra ngoài để hỏi lại server.
     *
     * <p>Hỏi lại MỖI lần mở là bắt buộc, đúng lý do tồn tại của cửa sổ này (xem javadoc lớp): nhiệm
     * vụ có thể đã đổi trạng thái từ lần cuối client biết, kể cả TRƯỚC phiên chơi này.
     */
    public void open() {
        panel.setVisible(true);
        fire(onOpened);
    }

    public void close() {
        panel.setVisible(false);
        armedAbandonQuestCode = "";
    }

    /** Gọi mỗi lần khung mở ra hoặc người chơi bấm Làm mới — chỗ nghe có nhiệm vụ hỏi lại server. */
    public void setOnRefresh(Runnable listener) {
        this.onOpened = listener;
    }

    /**
     * Điều kiện để phím J được phép mở khung — cổng phím CHUNG của mọi cửa sổ, xem
     * {@link MailPanel#setCanOpen}.
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Mã nhiệm vụ người chơi vừa XÁC NHẬN bỏ (bấm Bỏ hai lần, ADR-054). */
    public void setOnAbandon(Consumer<String> listener) {
        this.onAbandon = listener;
    }

    /** Mã nhiệm vụ của dòng người chơi vừa bấm Lĩnh thưởng — khoá idempotency sinh ở coordinator. */
    public void setOnClaim(Consumer<String> listener) {
        this.onClaim = listener;
    }

    /** Dòng trạng thái dưới tiêu đề — chuỗi ĐÃ dịch (khoá i18n tra ở coordinator, chỗ biết outcome). */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * Vẽ lại danh sách nhiệm vụ. Dựng lại toàn bộ dòng mỗi lần gọi — nhật ký chỉ đổi khi server vừa
     * trả lời (vài lần một phiên chơi), không phải mỗi khung, nên đây không nằm trong đường cấm cấp
     * phát của game loop (Điều 31) — cùng lập luận {@link MailPanel#setMails}.
     *
     * @param rows đã cắt xuống số dòng hiển thị được ở coordinator.
     * @param emptyOrOverflowNotice chuỗi đã dịch cho nhật ký rỗng hoặc phần bị cắt bớt; rỗng nếu
     *     không có gì phải nói thêm.
     */
    public void setQuests(List<QuestRow> rows, String emptyOrOverflowNotice) {
        shownRows = List.copyOf(rows);
        shownNotice = emptyOrOverflowNotice;
        if (rows.stream().noneMatch(row -> row.abandonable() && row.questCode().equals(armedAbandonQuestCode))) {
            armedAbandonQuestCode = ""; // dòng đang chờ xác nhận đã biến mất / đổi trạng thái
        }
        questList.clear();
        for (QuestRow row : rows) {
            addQuestRow(row);
        }
        if (!emptyOrOverflowNotice.isEmpty()) {
            questList
                    .add(rowLabel(emptyOrOverflowNotice))
                    .colspan(2)
                    .growX()
                    .left()
                    .row();
        }
    }

    private void addQuestRow(QuestRow row) {
        Label label = rowLabel(row.displayText());
        label.setEllipsis(true);
        questList.add(label).width(ROW_TEXT_WIDTH_PX).left().padRight(6f).padBottom(ROW_PAD_PX);
        if (row.claimable()) {
            questList
                    .add(actionButton("questlog.action.claim", () -> emit(row.questCode())))
                    .minWidth(ROW_BUTTON_MIN_WIDTH_PX)
                    .padBottom(ROW_PAD_PX);
        } else if (row.abandonable()) {
            boolean armed = row.questCode().equals(armedAbandonQuestCode);
            questList
                    .add(actionButton(
                            armed ? "questlog.action.abandon_confirm" : "questlog.action.abandon",
                            () -> onAbandonClicked(row.questCode())))
                    .minWidth(ROW_BUTTON_MIN_WIDTH_PX)
                    .padBottom(ROW_PAD_PX);
        } else {
            questList.add().minWidth(ROW_BUTTON_MIN_WIDTH_PX).padBottom(ROW_PAD_PX);
        }
        questList.row();
    }

    /** Lần một: đổi nhãn thành "bấm lần nữa"; lần hai trên CÙNG dòng: báo ra ngoài. */
    private void onAbandonClicked(String questCode) {
        if (!questCode.equals(armedAbandonQuestCode)) {
            armedAbandonQuestCode = questCode;
            setQuests(shownRows, shownNotice);
            return;
        }
        armedAbandonQuestCode = "";
        setQuests(shownRows, shownNotice);
        if (onAbandon != null) {
            onAbandon.accept(questCode);
        }
    }

    private void emit(String questCode) {
        if (onClaim != null) {
            onClaim.accept(questCode);
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

    /** KHÔNG dispose {@link KageSkin} nhận ở constructor — của {@link CombatHud}, xem javadoc đó. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
