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
    private static final float CHANNEL_BUTTON_WIDTH_PX = 104f;

    /** Trắng ngà — kênh Thế giới, và mặc định cho kênh không nhận ra. */
    private static final Color WORLD_COLOR = Color.valueOf("F2F2F2");

    /** Xanh lá nhạt — kênh Khu vực (chỉ người quanh mình nghe thấy). */
    private static final Color AREA_COLOR = Color.valueOf("9CE08A");

    /** Hồng — tin nhắn riêng, quy ước gần như phổ quát ở MMO. */
    private static final Color PRIVATE_COLOR = Color.valueOf("F58AD0");

    /** Vàng đồng nhạt — dòng của hệ thống (lý do gửi hỏng), tông của viền HUD. */
    private static final Color SYSTEM_COLOR = Color.valueOf("E0B860");

    private final Stage stage;
    private final Table panel;
    private final List<Label> lineLabels = new ArrayList<>();
    private final Table composer;
    private final TextButton channelButton;
    private final TextField input;

    private Consumer<String> onSend;

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

        Table log = new Table();
        for (int index = 0; index < maxLines; index++) {
            Label label = new Label("", skin.labelStyle);
            label.setWrap(true);
            label.setAlignment(Align.left);
            lineLabels.add(label);
            log.add(label).width(PANEL_WIDTH_PX).growX().left().row();
        }

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
        panel.setBackground(skin.panel);
        panel.pad(PANEL_PAD_PX);
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
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (keycode != OPEN_KEY || isComposing() || !canOpen.getAsBoolean()) {
                    return false;
                }
                openComposer();
                return true;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ESCAPE || !isComposing()) {
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

    private void submit() {
        String typed = input.getText();
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
        // Nhiều dòng hơn số Label dựng sẵn thì hiện MỚI NHẤT và bỏ phần cũ, không ném ngoại lệ:
        // ChatController hiện cấp đúng MAX_LINES, nhưng lớp này public và không có gì bắt hai con số
        // đó phải khớp mãi mãi.
        int shown = Math.min(renderedLines.size(), lineLabels.size());
        int skipped = renderedLines.size() - shown;
        int offset = lineLabels.size() - shown;
        for (int index = 0; index < lineLabels.size(); index++) {
            Label label = lineLabels.get(index);
            int source = index - offset;
            if (source < 0) {
                label.setText(""); // nhật ký chưa đầy — chừa dòng trống ở TRÊN, chữ mới luôn ở đáy
                continue;
            }
            label.setText(renderedLines.get(skipped + source));
            label.setColor(colorOf(channelNames.get(skipped + source)));
        }
        panel.setVisible(!renderedLines.isEmpty() || isComposing());
    }

    /** Loại dòng chat, chỉ để chọn màu — panel không cần biết enum protobuf. */
    public enum LineKind {
        WORLD,
        AREA,
        PRIVATE,
        SYSTEM
    }

    private static Color colorOf(LineKind kind) {
        return switch (kind) {
            case AREA -> AREA_COLOR;
            case PRIVATE -> PRIVATE_COLOR;
            case SYSTEM -> SYSTEM_COLOR;
            case WORLD -> WORLD_COLOR;
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
