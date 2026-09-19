package dev.kage.client.core.ui.hud;

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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.render.FontFactory;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Cửa sổ đấu tay đôi (2026-08-27) — điểm xếp hạng, người đang chọn để thách đấu, lời thách đấu đang
 * chờ trả lời, và trận đang diễn ra. Mở bằng phím K, neo GIỮA màn hình như
 * {@link GuildPanel}/{@link FriendPanel}.
 *
 * <p>K vì T (chat), P (nhóm), F (bạn bè), G (bang hội) đã có chủ, và A/D/W/Z/Enter là phím gameplay
 * ({@code DesktopInputMapper}).
 *
 * <p>{@code Stage} RIÊNG cùng lý do {@link ChatPanel}/{@link DialoguePanel}: thứ tự nuốt input chỉ
 * đặt được cho cả một {@code Stage} qua {@code InputMultiplexer}. Click vào cửa sổ này phải bị nuốt
 * chứ không rơi xuống thế giới thành lệnh chọn mục tiêu.
 *
 * <p>Widget chỉ VẼ (UI.md §4): không giữ {@code challenge_id}, không biết tên thật (chưa sanitize)
 * của ai, không biết opcode nào. Nó nhận các dòng ĐÃ ghép sẵn thành chuỗi hiển thị và báo ra ý định
 * người chơi vừa bấm — trừ ô nhập tên, thứ duy nhất chuỗi đi RA từ đây (người chơi tự gõ, chưa qua
 * sanitize nên vẫn là tên thật).
 */
public final class DuelPanel implements HudWindow {

    /**
     * Phím mở cửa sổ. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với phím T của {@link ChatPanel} và ESC của {@link DialoguePanel}.
     */
    private static final int OPEN_KEY = Input.Keys.K;

    private static final float PANEL_WIDTH_PX = 480f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float ROW_GAP_PX = 6f;

    private static final float SECTION_PAD_PX = 8f;

    private static final float ACTION_BUTTON_WIDTH_PX = 104f;

    /** Trần ký tự ô nhập tên — khớp {@code DuelController.MAX_NAME_LENGTH} (world/ không với tới). */
    private static final int NAME_INPUT_MAX_LENGTH = 16;

    /** Vàng đồng nhạt — tiêu đề và dòng trạng thái, cùng tông viền HUD của {@link KageSkin}. */
    private static final Color ACCENT_COLOR = Color.valueOf("E0B860");

    /** Trắng ngà — dòng dữ liệu thường. */
    private static final Color ROW_COLOR = Color.valueOf("F2F2F2");

    /** Xám — dòng gợi ý cách đóng cửa sổ, cùng tông {@link FriendPanel}. */
    private static final Color HINT_COLOR = Color.valueOf("9A9A9A");

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final Table panel;
    private final Label ratingLabel;
    private final Label statusLabel;
    private final Label candidateLabel;
    private final Label incomingLabel;
    private final Label inDuelLabel;
    private final Table candidateRow;
    private final Table incomingRow;
    private final Table inDuelRow;
    private final TextField nameInput;

    private Runnable onChallengeCandidate;
    private Consumer<String> onChallengeTyped;
    private Runnable onAccept;
    private Runnable onDecline;
    private Runnable onForfeit;
    private Runnable onOpened;

    /** Xem {@link #setCanOpen}. Mặc định LUÔN cho mở, để lớp này tự chạy được trong test/preview. */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link GuildPanel}/{@link FriendPanel}.
     */
    public DuelPanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.ratingLabel = rowLabel("");
        this.statusLabel = new Label("", skin.labelStyle);
        this.statusLabel.setColor(ACCENT_COLOR);
        this.statusLabel.setWrap(true);
        this.candidateLabel = rowLabel("");
        this.incomingLabel = rowLabel("");
        this.inDuelLabel = rowLabel("");

        this.nameInput = new TextField("", skin.textFieldStyle);
        this.nameInput.setMessageText(text("duel.input.hint"));
        // Gõ được đúng những gì font vẽ được — nếu không, ký tự vừa gõ BIẾN MẤT ngay trong ô nhập
        // của chính mình mà không có báo lỗi nào (xem FontFactory#canRender).
        this.nameInput.setTextFieldFilter((field, character) -> FontFactory.canRender(character));
        this.nameInput.setMaxLength(NAME_INPUT_MAX_LENGTH);
        this.nameInput.setTextFieldListener((field, character) -> {
            if (character == '\r' || character == '\n') {
                submitTypedName();
            }
        });

        this.candidateRow = buildCandidateRow();
        this.incomingRow = buildIncomingRow();
        this.inDuelRow = buildInDuelRow();
        this.panel = buildPanel();
        // Nuốt click rơi vào NỀN cửa sổ (không trúng nút nào): không nuốt thì cú click đó rơi tiếp
        // xuống tầng thế giới và đổi mục tiêu đánh của người chơi sau lưng cửa sổ đang mở — cùng
        // cách FriendPanel/ChatPanel đã chặn.
        panel.addListener(new ClickListener());

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.center).add(panel);
        stage.addActor(container);
        installKeyHandling();
    }

    private Table buildPanel() {
        Table built = new Table();
        built.setBackground(skin.panel);
        built.pad(PANEL_PAD_PX);
        built.add(sectionLabel("duel.title")).width(PANEL_WIDTH_PX).left().row();
        built.add(ratingLabel).width(PANEL_WIDTH_PX).left().padTop(ROW_GAP_PX).row();
        built.add(statusLabel).width(PANEL_WIDTH_PX).left().padTop(ROW_GAP_PX).row();
        built.add(incomingRow)
                .width(PANEL_WIDTH_PX)
                .growX()
                .padTop(SECTION_PAD_PX)
                .row();
        built.add(inDuelRow)
                .width(PANEL_WIDTH_PX)
                .growX()
                .padTop(SECTION_PAD_PX)
                .row();
        built.add(candidateRow)
                .width(PANEL_WIDTH_PX)
                .growX()
                .padTop(SECTION_PAD_PX)
                .row();
        built.add(buildComposer())
                .width(PANEL_WIDTH_PX)
                .growX()
                .padTop(SECTION_PAD_PX)
                .row();
        Label hint = new Label(text("duel.hint.close"), skin.labelStyle);
        hint.setColor(HINT_COLOR);
        built.add(hint).width(PANEL_WIDTH_PX).left().padTop(ROW_GAP_PX).row();
        built.setVisible(false); // chưa mở thì không bày một cửa sổ rỗng giữa màn hình
        return built;
    }

    /** Người vừa click trên màn hình + nút thách đấu người đó. Ẩn khi chưa chọn ai. */
    private Table buildCandidateRow() {
        Table row = new Table();
        row.add(candidateLabel).growX().left();
        row.add(button("duel.action.challenge", () -> fire(onChallengeCandidate)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        row.setVisible(false);
        return row;
    }

    /** Lời thách đấu đang chờ mình trả lời + hai nút Nhận/Từ chối. Ẩn khi không có lời nào. */
    private Table buildIncomingRow() {
        Table row = new Table();
        row.add(incomingLabel).growX().left();
        row.add(button("duel.action.accept", () -> fire(onAccept)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        row.add(button("duel.action.decline", () -> fire(onDecline)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        row.setVisible(false);
        return row;
    }

    /**
     * Trận đang diễn ra + nút Bỏ cuộc. Ẩn khi không ở trong trận nào — bỏ cuộc là THUA THẬT (mất
     * điểm MMR, arena.proto), bày nút đó ra lúc không có trận là mời người chơi bấm nhầm.
     */
    private Table buildInDuelRow() {
        Table row = new Table();
        row.add(inDuelLabel).growX().left();
        row.add(button("duel.action.forfeit", () -> fire(onForfeit)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        row.setVisible(false);
        return row;
    }

    /**
     * Ô gõ tên + nút Thách đấu + nút Đóng.
     *
     * <p>Ô gõ tay tồn tại song song với việc click chọn trên màn hình, và không phải để cho tiện:
     * {@code CharacterSpawnedEvent} không mang tên nhân vật, nên có những người click vào mà client
     * <b>không thể</b> biết tên (xem {@code DuelController}) — gõ tay là lối duy nhất còn lại.
     *
     * <p>Nút Đóng tồn tại dù đã có ESC: cửa sổ này CHẶN toàn bộ phím gameplay khi mở (xem
     * {@link #isOpen()}), nên một lối ra chỉ-bằng-bàn-phím là một cái bẫy trên cảm ứng (UI.md).
     */
    private Table buildComposer() {
        Table composer = new Table();
        composer.add(nameInput)
                .width(PANEL_WIDTH_PX - (2f * (ACTION_BUTTON_WIDTH_PX + ROW_GAP_PX)))
                .growX();
        composer.add(button("duel.action.challenge", this::submitTypedName))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        composer.add(button("duel.action.close", this::close))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        return composer;
    }

    private Label sectionLabel(String key) {
        Label label = new Label(text(key), skin.labelStyle);
        label.setColor(ACCENT_COLOR);
        return label;
    }

    private Label rowLabel(String content) {
        Label label = new Label(content, skin.labelStyle);
        label.setColor(ROW_COLOR);
        return label;
    }

    private TextButton button(String labelKey, Runnable action) {
        TextButton button = new TextButton(text(labelKey), skin.buttonStyle);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    private void submitTypedName() {
        if (onChallengeTyped != null) {
            onChallengeTyped.accept(nameInput.getText());
        }
    }

    private static void fire(Runnable listener) {
        if (listener != null) {
            listener.run();
        }
    }

    /**
     * Phím K mở cửa sổ, ESC đóng.
     *
     * <p>Mở ở <b>{@code keyUp}</b> chứ không {@code keyDown} — giữ phím sinh ra một chuỗi
     * {@code keyDown}/{@code keyTyped} lặp lại; mở ở {@code keyDown} thì ngay khi {@link #nameInput}
     * nhận keyboard focus, những lần lặp còn lại của CHÍNH phím vừa bấm chảy thẳng vào ô nhập thành
     * "kkkk" (đúng cái bẫy {@code ChatPanel#installKeyHandling} đã ghi lại).
     *
     * <p>Đang mở thì NUỐT mọi {@code keyUp}: mọi cửa sổ khác cũng mở ô nhập của nó ở {@code keyUp},
     * và {@code InputMultiplexer} chuyển phím xuống tầng dưới cho tới khi có ai trả {@code true} —
     * không nuốt thì gõ chữ "t" trong ô tên đối thủ sẽ đồng thời bật ô chat lên phía sau
     * (commit 187024f).
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
     * Điều kiện để phím K được phép mở cửa sổ — nửa còn lại của cái bẫy mô tả ở
     * {@link #installKeyHandling()}: chữ "k" người chơi gõ trong ô chat cũng rơi tới {@code Stage}
     * này và sẽ mở cửa sổ đấu tay đôi nếu không ai chặn. Panel không được biết khung chat tồn tại
     * (UI.md §4), nên {@code WorldScreen} là chỗ cung cấp câu trả lời.
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /**
     * Đang mở hay không. {@code WorldScreen} BẮT BUỘC hỏi câu này trước khi đọc phím gameplay: phím
     * di chuyển/đánh được poll thẳng qua {@code Gdx.input.isKeyPressed}, đi vòng qua lưng
     * {@code InputProcessor}, nên {@code Stage} có nuốt phím hay không cũng không ngăn được chữ "a"
     * trong tên đối thủ đồng thời làm nhân vật chạy sang trái.
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    public void open() {
        panel.setVisible(true);
        nameInput.setText("");
        // KHÔNG tự chiếm keyboard focus khi mở. {@code TextField} của libGDX chỉ vẽ
        // messageText khi KHÔNG có focus, nên tự lấy focus là tự xoá mất chữ gợi ý của chính
        // ô đó — người chơi nhìn thấy một ô trống không nhãn, không biết phải gõ gì vào.
        // Thấy tận mắt lúc playtest 2026-08-29: bốn panel cùng dính một dòng lệnh này.
        fire(onOpened);
    }

    /**
     * Mở cửa sổ CHỈ KHI không có cửa sổ nhập liệu nào khác đang mở — dùng cho lời thách đấu ập tới
     * lúc người chơi không hề bấm gì.
     *
     * <p>Tự bật lên đè ngang lưng người khác là cách chắc chắn nhất để phá một cái bẫy phím mà cả
     * dự án đã mất một lần gộp song song để vá (commit 187024f): hai ô nhập cùng mở thì ESC đóng
     * nhầm cửa sổ, và phím gõ vào cái này lại bật cái kia. Hỏi đúng cái cổng chung đó thay vì tự
     * quyết.
     *
     * @return {@code false} nếu không được phép mở — chỗ gọi phải báo cho người chơi bằng đường
     *     khác (chữ nổi), nếu không thì lời thách đấu tới trong im lặng tuyệt đối.
     */
    public boolean openIfAllowed() {
        if (isOpen()) {
            return true;
        }
        if (!canOpen.getAsBoolean()) {
            return false;
        }
        open();
        return true;
    }

    public void close() {
        panel.setVisible(false);
        nameInput.setText("");
        // Bỏ focus THẬT: ô nhập còn giữ keyboard focus là còn nuốt phím dù cửa sổ đã ẩn đi.
        stage.setKeyboardFocus(null);
    }

    /** Gọi mỗi lần cửa sổ mở ra — chỗ nghe có nhiệm vụ hỏi lại server điểm xếp hạng. */
    public void setOnOpened(Runnable listener) {
        this.onOpened = listener;
    }

    /** Bấm Thách đấu ở dòng người vừa chọn trên màn hình — panel không giữ tên thật của họ. */
    public void setOnChallengeCandidate(Runnable listener) {
        this.onChallengeCandidate = listener;
    }

    /** Tên người chơi vừa gõ (Enter hoặc nút) — có thể rỗng, lọc là việc của controller. */
    public void setOnChallengeTyped(Consumer<String> listener) {
        this.onChallengeTyped = listener;
    }

    public void setOnAccept(Runnable listener) {
        this.onAccept = listener;
    }

    public void setOnDecline(Runnable listener) {
        this.onDecline = listener;
    }

    /** Bấm Bỏ cuộc — THUA thật, xem {@link #buildInDuelRow()}. */
    public void setOnForfeit(Runnable listener) {
        this.onForfeit = listener;
    }

    /** Dòng điểm xếp hạng — chuỗi ĐÃ dịch (khoá i18n tra ở coordinator, chỗ biết dữ liệu). */
    public void setRating(String text) {
        ratingLabel.setText(text);
    }

    /** Dòng kết quả thao tác gần nhất — chuỗi ĐÃ dịch, xem {@link #setRating}. */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /** Bày dòng "đã chọn X" + nút thách đấu. Chuỗi ĐÃ ghép sẵn, panel không tự ghép. */
    public void showCandidate(String text) {
        candidateLabel.setText(text);
        candidateRow.setVisible(true);
    }

    public void hideCandidate() {
        candidateRow.setVisible(false);
    }

    /** Bày dòng "X thách đấu bạn" + hai nút trả lời. */
    public void showIncomingChallenge(String text) {
        incomingLabel.setText(text);
        incomingRow.setVisible(true);
    }

    public void hideIncomingChallenge() {
        incomingRow.setVisible(false);
    }

    /** Bày dòng "đang đấu với X" + nút Bỏ cuộc. */
    public void showDuel(String text) {
        inDuelLabel.setText(text);
        inDuelRow.setVisible(true);
    }

    public void hideDuel() {
        inDuelRow.setVisible(false);
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
