package dev.kage.client.core.ui.hud;

import java.util.ArrayList;
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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.input.KeyBindings;
import dev.kage.client.core.render.FontFactory;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Khung chat (2026-08-25) — nhật ký vài dòng gần nhất + ô nhập, neo GÓC TRÁI-DƯỚI (góc duy nhất
 * {@link CombatHud} còn để trống: nó đã chiếm trên-trái, trên, trên-phải, giữa-dưới, dưới-phải).
 *
 * <p>{@code Stage} RIÊNG cùng lý do {@link DialoguePanel}: thứ tự nuốt input chỉ đặt được cho cả một
 * {@code Stage} qua {@code InputMultiplexer}, không đặt được cho một actor bên trong. Click vào
 * nhật ký phải mở ô nhập chứ không rơi xuống thế giới thành lệnh chọn mục tiêu.
 *
 * <p>Widget chỉ VẼ (UI.md §4): nó không biết kênh nào hợp lệ, không tự dựng packet, chỉ báo ra chuỗi
 * người chơi vừa gõ qua {@link #setOnSend}. {@code ChatController} + {@code ChatCoordinator} lo phần
 * còn lại.
 */
public final class ChatPanel implements HudWindow {

    /** ADR-363 — số dòng hiện mặc định (như trước khi có lệnh đổi). */
    public static final int DEFAULT_VISIBLE_LINES = 6;

    /** ADR-363 — ít nhất bấy nhiêu dòng. */
    public static final int MIN_VISIBLE_LINES = 4;

    /** ADR-363 — nhiều nhất bấy nhiêu dòng hiện cùng lúc (nhật ký giữ nhiều hơn để cuộn, ADR-395). */
    public static final int VISIBLE_LINES_CEILING = 12;

    /**
     * Phím mở ô nhập. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với ESC của {@link DialoguePanel}: không gửi gói nào, không đổi trạng thái thế
     * giới, chỉ dọn/mở UI của chính máy này.
     *
     * <p>Không dùng Enter (phím quen thuộc nhất cho việc này ở game khác) vì Enter đã là
     * {@code USE_SKILL} theo yêu cầu của chủ dự án 2026-08-09 — đổi nó là lấy mất một phím gameplay
     * đã chốt để đưa cho UI.
     */
    private static final int OPEN_KEY = Input.Keys.T;

    private static final float PANEL_WIDTH_PX = 420f;

    /**
     * Bao lâu không có dòng mới thì nhật ký tự ẩn (ô nhập đang đóng).
     *
     * <p>Không phải chuyện thẩm mỹ, và cũng không phải "cho giống game khác": khung này rộng 420px
     * neo TRÁI-dưới, tức nó che hẳn <b>một phần ba trái</b> vùng chơi ở cửa sổ 1280px — và con quái
     * đang đánh mình thường đứng đúng sau nó. Buổi playtest 2026-08-30 thấy chữ "−18" bay lên mà
     * không thấy ai đánh, và suýt kết luận nhầm là art quái không hiện (ROADMAP mục 48).
     *
     * <p>12 giây: đủ dài để đọc hết một dòng vừa tới, đủ ngắn để không che lúc đánh nhau. Dòng mới
     * hoặc mở ô nhập (phím T) là hiện lại ngay.
     */
    private static final float IDLE_HIDE_SECONDS = 12f;

    /** ADR-292 — mờ dần trong bấy nhiêu giây cuối trước khi ẩn hẳn (thay vì biến mất cái rụp). */
    private static final float FADE_SECONDS = 2f;

    /** ADR-291 — màu dòng đang ghim: vàng nhạt, khác mọi kênh. */
    private static final Color PINNED_COLOR = Color.valueOf("FFE08A");

    private static final float PANEL_PAD_PX = 10f;

    private static final float LEFT_MARGIN_PX = 14f;

    /**
     * Chừa chỗ cho thanh gợi ý phím của {@link CombatHud} (neo {@code Align.bottom}) — cùng con số
     * {@code DialoguePanel} đang dùng.
     *
     * <p>Không phải chuyện thẩm mỹ: thanh gợi ý neo GIỮA-dưới còn khung này neo TRÁI-dưới, nên
     * khoảng cách ngang giữa hai cái CO LẠI theo bề rộng cửa sổ. Ở 1280px chúng chỉ còn cách nhau
     * vài pixel (đo trên ảnh {@code runHudPreview}); hẹp hơn là chồng lên nhau thật. Đẩy khung chat
     * lên CAO HƠN cả thanh gợi ý thì bề rộng cửa sổ không còn liên quan nữa.
     */
    private static final float BOTTOM_MARGIN_PX = 90f;

    /**
     * Bề rộng CỐ ĐỊNH của nút đổi kênh. Không để {@link Table} tự co theo chữ: nhãn dài nhất
     * ("Thế giới") bị cắt cụt thành "Thế giớ" trên ảnh chụp preview đầu tiên, và bề rộng nhảy theo
     * kênh đang chọn làm ô nhập giật sang trái/phải mỗi lần bấm.
     */
    static final float CHANNEL_BUTTON_WIDTH_PX = 104f;

    /** Trắng ngà — kênh Thế giới, và mặc định cho kênh không nhận ra. */
    private static final Color WORLD_COLOR = Color.valueOf("F2F2F2");

    /** Xanh lá nhạt — kênh Khu vực (chỉ người quanh mình nghe thấy). */
    private static final Color AREA_COLOR = Color.valueOf("9CE08A");

    /** Hồng — tin nhắn riêng, quy ước gần như phổ quát ở MMO. */
    private static final Color PRIVATE_COLOR = Color.valueOf("F58AD0");

    /** Vàng đồng nhạt — dòng của hệ thống (lý do gửi hỏng), tông của viền HUD. */
    private static final Color SYSTEM_COLOR = Color.valueOf("E0B860");
    /** ADR-152 — loa thế giới: cam sáng, khác vàng nhạt của dòng hệ thống; tiền tố [Loa] mang nghĩa, màu chỉ phụ. */
    private static final Color BROADCAST_COLOR = Color.valueOf("FF9F43");

    /** ADR-245 — tin nhắc tên mình: xanh lơ sáng, khác hẳn mọi kênh. */
    private static final Color MENTION_COLOR = Color.valueOf("5FE7FF");

    /** ADR-364 — tin của bạn bè. */
    private static final Color FRIEND_COLOR = Color.valueOf("B6F27A");

    private final Stage stage;
    private final Table panel;
    private final List<Label> lineLabels = new ArrayList<>();
    private final Table composer;
    private final TextButton channelButton;
    private final TextField input;
    private final SentLineHistory sentLines = new SentLineHistory(); // ADR-262
    private final Label pinnedLabel; // ADR-291
    private final FadedDrawable background; // ADR-381

    /** ADR-380 — Tab: chữ đang gõ ⇒ chữ đã hoàn thành (trả nguyên nếu không có gì để hoàn thành). */
    private java.util.function.UnaryOperator<String> completer = java.util.function.UnaryOperator.identity();

    /** ADR-292 — giây rảnh trước khi ẩn; {@code 0} = không bao giờ ẩn. */
    private float idleHideSeconds = IDLE_HIDE_SECONDS;

    private Consumer<String> onSend;

    /** Giây kể từ dòng chat gần nhất. Bắt đầu ở "đã lâu lắm rồi" nên khung rỗng không tự hiện. */
    private float sinceLastLine = Float.MAX_VALUE;

    /** ADR-333 — ẩn bằng phím {@code anchat}. */
    private boolean hidden;

    /** ADR-363 — bảng chứa các nhãn đang hiện. */
    private final Table logTable;

    /** ADR-395 — nhật ký đang vẽ (giữ để cuộn mà không phải hỏi lại controller). */
    private List<String> shownLines = List.of();

    private List<LineKind> shownKinds = List.of();

    /** ADR-395 — đang lùi lại bao nhiêu dòng so với cuối nhật ký (0 = dòng mới nhất ở đáy). */
    private int scrollBack;

    private int visibleLines = DEFAULT_VISIBLE_LINES;

    /** ADR-339 — màu riêng người chơi đặt cho từng loại dòng. */
    private final java.util.EnumMap<LineKind, Color> lineColors = new java.util.EnumMap<>(LineKind.class);

    private Runnable onChannelToggle;

    /** Xem {@link #setCanOpen}. Mặc định LUÔN cho mở, để lớp này tự chạy được trong test/preview. */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link DialoguePanel}.
     * @param maxLines số dòng nhật ký, do {@code ChatController} quyết (nó là nơi giữ nhật ký thật);
     *     panel dựng SẴN đúng bấy nhiêu {@link Label} và tái dùng, không tạo actor mỗi lần có tin.
     */
    public ChatPanel(KageSkin skin, int maxLines) {
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.pinnedLabel = pinnedLabel(skin);
        this.logTable = buildLog(skin, maxLines);
        Table log = logTable;
        setVisibleLines(DEFAULT_VISIBLE_LINES); // ADR-363

        this.channelButton = new TextButton("", skin.buttonStyle);
        this.input = new TextField("", skin.textFieldStyle);
        // Gõ được đúng những gì font vẽ được — nếu không, ký tự vừa gõ sẽ BIẾN MẤT ngay trong ô nhập
        // của chính mình mà không có báo lỗi nào (xem FontFactory#canRender).
        this.input.setTextFieldFilter((field, character) -> FontFactory.canRender(character));
        this.input.setMaxLength(maxBodyLengthGuard());

        this.composer = new Table();
        composer.add(channelButton).width(CHANNEL_BUTTON_WIDTH_PX).padRight(6f);
        composer.add(input).width(PANEL_WIDTH_PX - CHANNEL_BUTTON_WIDTH_PX - 6f).growX();
        composer.setVisible(false);

        this.panel = new Table();
        this.background = new FadedDrawable(skin.panel);
        panel.setBackground(background); // ADR-381
        panel.pad(PANEL_PAD_PX);
        panel.add(pinnedLabel)
                .width(PANEL_WIDTH_PX)
                .growX()
                .left()
                .padBottom(4f)
                .row(); // ADR-291
        panel.add(log).width(PANEL_WIDTH_PX).growX().row();
        panel.add(composer).padTop(6f).width(PANEL_WIDTH_PX).growX().row();
        panel.setVisible(false); // chưa có dòng nào thì không bày một khung rỗng ra giữa màn hình

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.bottomLeft).add(panel).padLeft(LEFT_MARGIN_PX).padBottom(BOTTOM_MARGIN_PX);
        stage.addActor(container);

        installKeyHandling();
        installClickToCompose();
        channelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (onChannelToggle != null) {
                    onChannelToggle.run();
                }
            }
        });
    }

    /**
     * ADR-363 — số dòng nhật ký HIỆN (kẹp trong [{@value #MIN_VISIBLE_LINES}, số nhãn dựng sẵn]). Dòng mới nhất luôn ở
     * đáy, nên chỉ cần đưa {@code n} nhãn cuối vào bảng — {@link #setLines} không đổi.
     */
    public void setVisibleLines(int lines) {
        int upper = Math.min(VISIBLE_LINES_CEILING, lineLabels.size());
        int shown = Math.clamp(lines, Math.min(MIN_VISIBLE_LINES, upper), upper);
        visibleLines = shown;
        logTable.clearChildren();
        for (int index = lineLabels.size() - shown; index < lineLabels.size(); index++) {
            logTable.add(lineLabels.get(index))
                    .width(PANEL_WIDTH_PX)
                    .growX()
                    .left()
                    .row();
        }
        logTable.invalidateHierarchy();
    }

    /** Dựng SẴN {@code maxLines} nhãn nhật ký (tái dùng, không tạo actor mỗi lần có tin). */
    private Table buildLog(KageSkin skin, int maxLines) {
        Table log = new Table();
        for (int index = 0; index < maxLines; index++) {
            Label label = new Label("", skin.labelStyle);
            label.setWrap(true);
            label.setAlignment(Align.left);
            lineLabels.add(label);
            log.add(label).width(PANEL_WIDTH_PX).growX().left().row();
        }
        return log;
    }

    /** ADR-291 — nhãn dòng ghim ở đầu khung, ẩn khi chưa ghim gì. */
    private static Label pinnedLabel(KageSkin skin) {
        Label label = new Label("", skin.labelStyle);
        label.setWrap(true);
        label.setColor(PINNED_COLOR);
        label.setVisible(false);
        return label;
    }

    /**
     * Trần ký tự của ô nhập — chặn tại chỗ thay vì để người chơi gõ 400 chữ rồi mới bị server từ
     * chối. Hằng số nằm ở {@code ChatController} (world/) mà lớp này ở ui/hud/ nên không với tới
     * được; 200 là con số của {@code ChatMessage.MAX_BODY_LENGTH} phía server, và
     * {@code ChatController.compose} vẫn kiểm lại lần nữa nên đây chỉ là hàng rào dễ chịu, không
     * phải nguồn chân lý.
     */
    private static int maxBodyLengthGuard() {
        return 200;
    }

    /**
     * Phím T mở ô nhập, ESC đóng, Enter gửi.
     *
     * <p>Mở ở <b>{@code keyUp}</b> chứ không {@code keyDown} — và đó không phải chuyện thẩm mỹ. Giữ
     * phím sinh ra một chuỗi {@code keyDown}/{@code keyTyped} lặp lại; nếu mở ở {@code keyDown} thì
     * ngay khi {@link #input} nhận keyboard focus, những lần lặp còn lại của CHÍNH phím vừa bấm sẽ
     * chảy thẳng vào ô nhập thành "tttt". {@code keyUp} chỉ bắn đúng một lần khi nhả tay, lúc đó
     * mọi lần lặp đã trôi qua trong khi chưa ai có focus.
     */
    private void installKeyHandling() {
        input.setFocusTraversal(false); // ADR-380 — Tab hoàn thành lệnh, không nhảy ô
        installScrollKeys();
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (keycode == KeyBindings.shared().keyOf(KeyBindings.Binding.HIDE_CHAT)
                        && !isComposing()
                        && canOpen.getAsBoolean()) {
                    hidden = !hidden; // ADR-333
                    return true;
                }
                if (keycode != KeyBindings.shared().resolve(OPEN_KEY) || isComposing() || !canOpen.getAsBoolean()) {
                    return false;
                }
                openComposer();
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (!isComposing()) {
                    return false;
                }
                if (keycode == Input.Keys.UP || keycode == Input.Keys.DOWN) { // ADR-262 — gọi lại tin đã gõ
                    (keycode == Input.Keys.UP ? sentLines.previous() : sentLines.next()).ifPresent(line -> {
                        input.setText(line);
                        input.setCursorPosition(line.length());
                    });
                    return true;
                }
                if (keycode == Input.Keys.TAB) { // ADR-380
                    complete();
                    return true;
                }
                if (keycode != Input.Keys.ESCAPE) {
                    return false;
                }
                closeComposer();
                return true;
            }
        });
        input.setTextFieldListener((field, character) -> {
            if (character == '\r' || character == '\n') {
                submit();
            }
        });
    }

    /**
     * Chạm vào nhật ký cũng mở ô nhập — lối vào duy nhất không cần bàn phím (UI.md: cảm ứng).
     *
     * <p>{@link ClickListener} chứ không phải {@link InputListener} thô: nó đã trả {@code true} ở
     * {@code touchDown} sẵn, tức là click vào khung chat bị NUỐT và không rơi xuống tầng thế giới
     * thành lệnh chọn mục tiêu — đúng thứ cần, mà không phải tự override một hàm 5 tham số
     * (CODING-STANDARDS {@code ParameterNumber} max=4).
     */
    private void installClickToCompose() {
        panel.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!isComposing()) {
                    openComposer();
                }
            }
        });
    }

    /** ADR-395 — PageUp/PageDown cuộn nhật ký một trang (số dòng hiện − 1), đang gõ hay không cũng được. */
    private void installScrollKeys() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.PAGE_UP && keycode != Input.Keys.PAGE_DOWN) {
                    return false;
                }
                int page = Math.max(1, visibleLines - 1);
                scroll(keycode == Input.Keys.PAGE_UP ? page : -page);
                return true;
            }
        });
    }

    private void complete() {
        String completed = completer.apply(input.getText());
        input.setText(completed);
        input.setCursorPosition(completed.length());
    }

    /** ADR-380 — nối bộ hoàn thành lệnh cho phím Tab. */
    public void setCompleter(java.util.function.UnaryOperator<String> complete) {
        this.completer = complete;
    }

    /** ADR-381 — độ đậm nền khung chat 0..1 (chữ không đổi). */
    public void setBackgroundOpacity(float opacity) {
        background.setOpacity(opacity);
    }

    /** ADR-381 — độ đậm nền đang dùng. */
    public float backgroundOpacity() {
        return background.opacity();
    }

    private void submit() {
        String typed = input.getText();
        sentLines.remember(typed); // ADR-262
        // Đóng TRƯỚC khi báo ra ngoài: người nhận có thể mở lại ô nhập (VD báo lỗi rồi cho gõ tiếp),
        // và nó phải thắng, không bị lệnh đóng này chạy sau đè mất.
        closeComposer();
        if (onSend != null) {
            onSend.accept(typed);
        }
    }

    /**
     * Điều kiện được phép MỞ ô nhập, hỏi ngay trước khi mở — chặn chiều "đang mở một cửa sổ khác mà
     * bấm T". Chiều ngược lại do chính các cửa sổ đó nuốt {@code keyUp} khi đang mở.
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Chuỗi người chơi vừa gõ xong (bấm Enter) — có thể rỗng, lọc là việc của controller. */
    public void setOnSend(Consumer<String> listener) {
        this.onSend = listener;
    }

    /** Người chơi bấm nút đổi kênh — panel không biết có những kênh nào, xem javadoc lớp. */
    public void setOnChannelToggle(Runnable listener) {
        this.onChannelToggle = listener;
    }

    /**
     * Đang gõ hay không. {@code WorldScreen} BẮT BUỘC hỏi câu này trước khi đọc phím gameplay: phím
     * di chuyển/đánh được poll thẳng qua {@code Gdx.input.isKeyPressed}, đi vòng qua lưng
     * {@code InputProcessor}, nên {@code Stage} có nuốt phím hay không cũng không ngăn được chữ "a"
     * vừa gõ đồng thời làm nhân vật chạy sang trái.
     */
    public boolean isComposing() {
        return composer.isVisible();
    }

    public void openComposer() {
        input.setText("");
        composer.setVisible(true);
        panel.setVisible(true);
        stage.setKeyboardFocus(input);
    }

    public void closeComposer() {
        // Đếm lại từ 0: vừa đóng ô nhập mà nhật ký biến mất ngay thì người chơi mất luôn câu mình
        // vừa gửi khỏi tầm mắt (xem IDLE_HIDE_SECONDS).
        sinceLastLine = 0f;
        composer.setVisible(false);
        input.setText("");
        // Bỏ focus THẬT: ô nhập còn giữ keyboard focus là còn nuốt phím dù đã ẩn đi.
        stage.setKeyboardFocus(null);
        panel.setVisible(hasAnyLine());
    }

    /** Nhật ký đang có chữ hay không — dòng MỚI NHẤT nằm ở ĐÁY, xem {@link #setLines}. */
    private boolean hasAnyLine() {
        return !lineLabels.get(lineLabels.size() - 1).getText().isEmpty();
    }

    /** Nhãn nút đổi kênh (chuỗi đã dịch — panel không tra i18n cho kênh, controller biết kênh nào). */
    public void setChannelLabel(String label) {
        channelButton.setText(label);
    }

    /**
     * Vẽ lại toàn bộ nhật ký. Gọi khi CÓ TIN MỚI, không phải mỗi khung: {@code setText} của
     * {@link Label} dựng lại glyph layout, thứ không đáng làm 60 lần một giây cho chữ không đổi
     * (Điều 31).
     *
     * @param renderedLines từng dòng đã ghép sẵn thành chuỗi hiển thị, CŨ trước MỚI sau
     * @param channelNames mã kênh của từng dòng, song song {@code renderedLines} — quyết định màu
     */
    public void setLines(List<String> renderedLines, List<LineKind> channelNames) {
        shownLines = List.copyOf(renderedLines);
        shownKinds = List.copyOf(channelNames);
        scrollBack = clampScroll(scrollBack, renderedLines.size(), visibleLines); // ADR-395 — giữ chỗ đang xem
        paintLines();
        // Dòng mới ⇒ hiện lại và đếm giờ từ đầu (xem IDLE_HIDE_SECONDS).
        sinceLastLine = 0f;
        panel.getColor().a = 1f;
        panel.setVisible(!renderedLines.isEmpty() || isComposing());
    }

    /**
     * Vẽ nhật ký vào các nhãn dựng sẵn, kết thúc ở dòng {@code size - scrollBack}. Nhiều dòng hơn số Label thì hiện MỚI
     * NHẤT và bỏ phần cũ, không ném ngoại lệ (lớp này public, không gì bắt hai con số phải khớp mãi mãi).
     */
    private void paintLines() {
        int end = shownLines.size() - scrollBack;
        int shown = Math.min(end, lineLabels.size());
        int skipped = end - shown;
        int offset = lineLabels.size() - shown;
        for (int index = 0; index < lineLabels.size(); index++) {
            Label label = lineLabels.get(index);
            int source = index - offset;
            if (source < 0) {
                label.setText(""); // nhật ký chưa đầy — chừa dòng trống ở TRÊN, chữ mới luôn ở đáy
                continue;
            }
            label.setText(shownLines.get(skipped + source));
            label.setColor(colorOf(shownKinds.get(skipped + source)));
        }
    }

    /** ADR-395 — cuộn {@code delta} dòng (dương = lùi về tin cũ); kẹp trong nhật ký đang có. */
    public void scroll(int delta) {
        scrollBack = clampScroll(scrollBack + delta, shownLines.size(), visibleLines);
        paintLines();
        sinceLastLine = 0f;
        panel.getColor().a = 1f;
        panel.setVisible(!shownLines.isEmpty() || isComposing());
    }

    /** ADR-395 — số dòng đang lùi lại so với cuối nhật ký. */
    public int scrollBack() {
        return scrollBack;
    }

    /** Kẹp số dòng lùi lại: 0 … (tổng − số dòng hiện). Tĩnh để test. */
    public static int clampScroll(int wanted, int total, int visible) {
        return Math.clamp(wanted, 0, Math.max(0, total - visible));
    }

    /** ADR-291 — ghim một dòng lên đầu khung ({@code ""} = bỏ ghim). */
    public void setPinnedLine(String line) {
        pinnedLabel.setText(line);
        pinnedLabel.setVisible(!line.isEmpty());
    }

    /** ADR-333 — khung chat đang bị ẩn bằng phím (vẫn hiện khi đang gõ). */
    public boolean isHidden() {
        return hidden;
    }

    /** ADR-333 — ẩn / hiện khung chat. */
    public void setHidden(boolean hide) {
        this.hidden = hide;
    }

    /** ADR-292 — đổi số giây rảnh trước khi khung tự mờ rồi ẩn; {@code 0} = luôn hiện. */
    public void setIdleHideSeconds(float seconds) {
        this.idleHideSeconds = Math.max(0f, seconds);
        sinceLastLine = 0f;
        panel.getColor().a = 1f;
        panel.setVisible(hasAnyLine() || isComposing());
    }

    /** ADR-294 — cỡ chữ nhật ký chat (hệ số so với font gốc). */
    public void setFontScale(float scale) {
        for (Label label : lineLabels) {
            label.setFontScale(scale);
        }
        pinnedLabel.setFontScale(scale);
        panel.invalidateHierarchy();
    }

    /** Loại dòng chat, chỉ để chọn màu — panel không cần biết enum protobuf. */
    public enum LineKind {
        WORLD,
        AREA,
        PRIVATE,
        SYSTEM,
        /** ADR-152 — loa thế giới (Truyền Âm Phù). */
        BROADCAST,
        /** ADR-245 — tin có nhắc tên mình. */
        MENTION,
        /** ADR-364 — tin của một người bạn (kênh công khai). */
        FRIEND
    }

    /** ADR-339 — đặt màu riêng cho một loại dòng; rỗng = trả về màu mặc định. Áp từ lần vẽ danh sách kế tiếp. */
    public void setLineColor(LineKind kind, java.util.Optional<Color> color) {
        if (color.isPresent()) {
            lineColors.put(kind, new Color(color.get()));
        } else {
            lineColors.remove(kind);
        }
    }

    private Color colorOf(LineKind kind) {
        Color custom = lineColors.get(kind);
        return custom != null ? custom : defaultColorOf(kind);
    }

    /** Màu mặc định của từng loại dòng. */
    public static Color defaultColorOf(LineKind kind) {
        return switch (kind) {
            case AREA -> AREA_COLOR;
            case PRIVATE -> PRIVATE_COLOR;
            case SYSTEM -> SYSTEM_COLOR;
            case WORLD -> WORLD_COLOR;
            case BROADCAST -> BROADCAST_COLOR;
            case MENTION -> MENTION_COLOR;
            case FRIEND -> FRIEND_COLOR;
        };
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
        // Cộng dồn ở render chứ không ở fixedUpdate: đây là thời gian NGƯỜI XEM cảm nhận, không
        // phải thời gian mô phỏng — và lớp này không có đường nào vào vòng tick logic.
        if (sinceLastLine < Float.MAX_VALUE) {
            sinceLastLine += deltaTime;
        }
        panel.getColor().a = idleAlpha(isComposing() ? 0f : sinceLastLine, idleHideSeconds);
        if (panel.getColor().a <= 0f) {
            panel.setVisible(false);
        }
        stage.act(deltaTime);
        if (!hidden || isComposing()) { // ADR-333 — ẩn thì không vẽ, trừ khi đang gõ
            stage.draw();
        }
    }

    /**
     * ADR-292 — độ đậm của khung sau {@code idle} giây rảnh: 1 cho tới {@value #FADE_SECONDS} giây cuối, rồi giảm đều
     * về 0 ở mốc ẩn. {@code hideAfter == 0} = không bao giờ ẩn. Tĩnh để test.
     */
    public static float idleAlpha(float idle, float hideAfter) {
        if (hideAfter <= 0f || idle <= hideAfter - FADE_SECONDS) {
            return 1f;
        }
        return Math.clamp((hideAfter - idle) / FADE_SECONDS, 0f, 1f);
    }

    /** KHÔNG dispose {@link KageSkin} nhận ở constructor — của {@link CombatHud}, xem javadoc đó. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
