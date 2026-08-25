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
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.render.FontFactory;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Cửa sổ Nhóm (2026-08-25) — danh sách thành viên, lời mời đang chờ, và các nút tạo/mời/rời/kick.
 *
 * <p>Neo GIỮA màn hình chứ không vào một góc như {@link ChatPanel}: bốn góc đã hết chỗ
 * ({@link CombatHud} chiếm bốn, chat chiếm góc còn lại), và đây là cửa sổ người chơi MỞ RA rồi ĐÓNG
 * LẠI chứ không phải thứ nhìn liên tục — đúng loại nên che tạm giữa màn hình.
 *
 * <p>{@code Stage} RIÊNG cùng lý do {@link DialoguePanel}/{@link ChatPanel}. Widget chỉ VẼ (UI.md
 * §4): nó không biết lệnh nào hợp lệ, chỉ báo ra ý định qua các {@code setOn*}.
 */
public final class PartyPanel implements Disposable {

    /**
     * Phím bật/tắt cửa sổ. Nằm ở đây chứ không ở {@code GameAction} cùng lập luận với ESC của
     * {@link DialoguePanel} và phím T của {@link ChatPanel}: mở/đóng một cửa sổ không gửi gói nào và
     * không đổi trạng thái thế giới, đó là UI của riêng máy này.
     */
    private static final int TOGGLE_KEY = Input.Keys.P;

    private static final float PANEL_WIDTH_PX = 520f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float NAME_FIELD_WIDTH_PX = 240f;

    /** Xanh lá — thành viên đang trực tuyến. */
    private static final Color ONLINE_COLOR = Color.valueOf("9CE08A");

    /** Xám nhạt — dòng gợi ý cách đóng cửa sổ, không phải nội dung. */
    private static final Color HINT_COLOR = Color.valueOf("A89878");

    /** Xám — ngoại tuyến (nhóm vẫn giữ họ, party.proto: {@code online} là cờ riêng, không phải xoá). */
    private static final Color OFFLINE_COLOR = Color.valueOf("8A8A8A");

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n = KageI18n.load();
    private final Table window;
    private final Label statusLabel;
    private final Table memberList;
    private final Table inviteList;
    private final TextField nameField;
    private final TextButton createButton;
    private final TextButton inviteButton;
    private final TextButton leaveButton;

    private Runnable onCreate;
    private Consumer<String> onInvite;
    private Runnable onLeave;
    private Consumer<String> onKick;
    private LongConsumer onAcceptInvite;
    private LongConsumer onRejectInvite;
    private Runnable onOpened;

    /** Xem {@link #setCanOpen}. Mặc định LUÔN cho mở, để lớp này tự chạy được trong test/preview. */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link DialoguePanel}/{@link ChatPanel}.
     */
    public PartyPanel(KageSkin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.statusLabel = new Label("", skin.labelStyle);
        this.statusLabel.setWrap(true);
        this.memberList = new Table();
        this.inviteList = new Table();

        this.nameField = new TextField("", skin.textFieldStyle);
        // Cùng lý do ChatPanel: gõ được đúng những gì font vẽ được, không thì ký tự vừa gõ biến mất
        // ngay trong ô của chính mình (xem FontFactory#canRender).
        this.nameField.setTextFieldFilter((field, character) -> FontFactory.canRender(character));

        this.createButton = button("party.action.create", () -> fire(onCreate));
        this.inviteButton = button("party.action.invite", this::fireInvite);
        this.leaveButton = button("party.action.leave", () -> fire(onLeave));

        this.window = new Table();
        window.setBackground(skin.panel);
        window.pad(PANEL_PAD_PX);
        window.add(new Label(text("party.title"), skin.labelStyle)).left().row();
        window.add(statusLabel).width(PANEL_WIDTH_PX).padTop(8f).left().row();
        window.add(memberList).width(PANEL_WIDTH_PX).padTop(8f).growX().row();
        window.add(inviteList).width(PANEL_WIDTH_PX).padTop(8f).growX().row();
        window.add(buildActionRow()).width(PANEL_WIDTH_PX).padTop(12f).growX().row();
        // Cách ĐÓNG phải nằm ngay trên cửa sổ. Không có nó thì người chơi mở nhầm một cửa sổ che
        // giữa màn hình và không biết làm sao thoát ra — cùng loại bế tắc mà ESC của DialoguePanel
        // sinh ra để phòng.
        Label hint = new Label(text("party.hint.close"), skin.labelStyle);
        hint.setColor(HINT_COLOR);
        window.add(hint).width(PANEL_WIDTH_PX).padTop(10f).left().row();

        Table container = new Table();
        container.setFillParent(true);
        container.align(Align.center).add(window);
        stage.addActor(container);
        window.setVisible(false);
        installKeyHandling();
    }

    private Table buildActionRow() {
        Table row = new Table();
        row.add(createButton).padRight(6f);
        row.add(leaveButton).padRight(6f);
        row.add(nameField).width(NAME_FIELD_WIDTH_PX).padRight(6f);
        row.add(inviteButton);
        return row;
    }

    /**
     * Phím P bật/tắt, ESC đóng — xem {@link #TOGGLE_KEY}.
     *
     * <p><b>Đang mở thì NUỐT mọi {@code keyUp}</b>, không riêng phím P. Đây là chỗ hai ô nhập chữ
     * nằm trên hai {@code Stage} khác nhau đâm vào nhau: {@code InputMultiplexer} chuyển
     * {@code keyUp} xuống tầng dưới cho tới khi có ai trả {@code true}, mà mỗi panel chỉ nhận ra
     * phím mở của CHÍNH nó — không nuốt thì gõ chữ "t" vào ô tên người mời sẽ bật thêm ô chat.
     *
     * <p>Chiều ngược lại ({@code WorldScreen} đang gõ chat mà bấm "p") do {@link #setCanOpen} chặn.
     * Hai cơ chế cho hai chiều, và không panel nào phải biết panel nào tồn tại.
     */
    private void installKeyHandling() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (isOpen()) {
                    if (keycode == TOGGLE_KEY) {
                        close();
                    }
                    return true; // nuốt: không để phím rơi xuống panel khác đang chờ phím mở của nó
                }
                if (keycode != TOGGLE_KEY || !canOpen.getAsBoolean()) {
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
     * Mở cửa sổ.
     *
     * <p>Bật ở {@code keyUp} cùng lý do {@link ChatPanel}: giữ phím sinh chuỗi sự kiện lặp, và ngay
     * khi {@link #nameField} nhận keyboard focus thì những lần lặp còn lại chảy thẳng vào ô nhập.
     */
    private void open() {
        window.setVisible(true);
        nameField.setText("");
        // Hỏi lại server NGAY lúc mở: party.proto không có event đẩy nào, nên mọi thứ đang hiện đều
        // là ảnh chụp cũ — có thể đã bị kick từ lúc nào không biết (xem javadoc PartyController).
        fire(onOpened);
    }

    /**
     * Điều kiện được phép MỞ, hỏi ngay trước khi mở. Đóng thì không hỏi — người chơi phải luôn thoát
     * ra được. {@code WorldScreen} truyền vào câu hỏi "không khung nào khác đang nhận chữ gõ".
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /**
     * Mở cửa sổ mà KHÔNG bắn {@link #setOnOpened} — chỉ dùng cho công cụ xem trước bố cục bằng ảnh
     * ({@code HudPreviewTool}), nơi không có kết nối nào để trả lời câu hỏi trạng thái. Đường mở
     * THẬT là phím {@link #TOGGLE_KEY}, và nó BẮT BUỘC phải hỏi lại server.
     */
    public void openForPreview() {
        window.setVisible(true);
    }

    public void close() {
        window.setVisible(false);
        nameField.setText("");
        stage.setKeyboardFocus(null);
    }

    /**
     * Cửa sổ đang mở hay không — {@code WorldScreen} phải chặn phím gameplay khi câu này đúng, cùng
     * lý do {@link ChatPanel#isComposing()}: phím gameplay được poll qua
     * {@code Gdx.input.isKeyPressed}, đi vòng qua lưng {@code InputProcessor}, nên {@code Stage} có
     * nuốt phím hay không cũng không ngăn được chữ đang gõ làm nhân vật chạy.
     */
    public boolean isOpen() {
        return window.isVisible();
    }

    /**
     * Gọi mỗi lần cửa sổ vừa MỞ — người nhận phải hỏi lại server toàn bộ trạng thái nhóm, vì
     * party.proto không có event đẩy nào để giữ ảnh chụp cũ cho đúng.
     */
    public void setOnOpened(Runnable listener) {
        this.onOpened = listener;
    }

    public void setOnCreate(Runnable listener) {
        this.onCreate = listener;
    }

    /** Tên nhân vật gõ trong ô — server tự tra và trả {@code TARGET_NOT_FOUND} nếu sai (Điều 24). */
    public void setOnInvite(Consumer<String> listener) {
        this.onInvite = listener;
    }

    public void setOnLeave(Runnable listener) {
        this.onLeave = listener;
    }

    public void setOnKick(Consumer<String> listener) {
        this.onKick = listener;
    }

    public void setOnAcceptInvite(LongConsumer listener) {
        this.onAcceptInvite = listener;
    }

    public void setOnRejectInvite(LongConsumer listener) {
        this.onRejectInvite = listener;
    }

    /**
     * Vẽ lại toàn bộ nội dung. Gọi khi trạng thái ĐỔI (nhận gói mới), không phải mỗi khung — dựng
     * lại actor 60 lần một giây là cấp phát thuần tuý trong game loop (Điều 31).
     *
     * @param status dòng trạng thái đã dịch sẵn ("Chưa vào nhóm nào" / "Nhóm 4 người — bạn là trưởng
     *     nhóm") — panel không tra i18n cho chuyện nó không biết
     * @param members từng thành viên đã ghép sẵn thành chuỗi hiển thị
     * @param canKick có hiện nút Đuổi cạnh mỗi thành viên hay không (chỉ trưởng nhóm)
     * @param invites lời mời đang chờ, đã ghép sẵn chuỗi
     */
    public void setContent(String status, List<MemberRow> members, boolean canKick, List<InviteRow> invites) {
        statusLabel.setText(status);
        memberList.clear();
        for (MemberRow member : members) {
            memberList.add(memberRow(member, canKick)).growX().padBottom(4f).row();
        }
        inviteList.clear();
        for (InviteRow invite : invites) {
            inviteList.add(inviteRow(invite)).growX().padBottom(4f).row();
        }
    }

    /** Một dòng thành viên đã ghép sẵn — panel không biết {@code character_id} để làm gì. */
    public record MemberRow(String displayName, String rawName, boolean online) {}

    /** Một dòng lời mời đã ghép sẵn. */
    public record InviteRow(long inviteId, String displayText) {}

    private Table memberRow(MemberRow member, boolean canKick) {
        Table row = new Table();
        Label label = new Label(member.displayName(), skin.labelStyle);
        label.setColor(member.online() ? ONLINE_COLOR : OFFLINE_COLOR);
        row.add(label).growX().left();
        if (canKick) {
            row.add(button("party.action.kick", () -> {
                        if (onKick != null) {
                            onKick.accept(member.rawName());
                        }
                    }))
                    .right();
        }
        return row;
    }

    private Table inviteRow(InviteRow invite) {
        Table row = new Table();
        row.add(new Label(invite.displayText(), skin.labelStyle)).growX().left();
        row.add(button("party.action.accept", () -> fireInviteAction(onAcceptInvite, invite.inviteId())))
                .padRight(6f);
        row.add(button("party.action.reject", () -> fireInviteAction(onRejectInvite, invite.inviteId())));
        return row;
    }

    /** Bật/tắt nút theo trạng thái nhóm — tạo nhóm khi đã ở trong nhóm là một lượt đi mạng vô nghĩa. */
    public void setActionsEnabled(boolean inParty, boolean isLeader) {
        createButton.setDisabled(inParty);
        leaveButton.setDisabled(!inParty);
        // party.proto: chỉ trưởng nhóm mời được (INVITE_PARTY_MEMBER_OUTCOME_NOT_LEADER).
        inviteButton.setDisabled(!inParty || !isLeader);
    }

    private TextButton button(String labelKey, Runnable action) {
        TextButton button = new TextButton(text(labelKey), skin.buttonStyle);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!button.isDisabled()) {
                    action.run();
                }
            }
        });
        return button;
    }

    private void fireInvite() {
        String name = nameField.getText().strip();
        if (name.isEmpty() || onInvite == null) {
            return; // ô trống — không gửi gói thừa, cùng tinh thần ChatController.compose
        }
        onInvite.accept(name);
        nameField.setText("");
    }

    private static void fireInviteAction(LongConsumer listener, long inviteId) {
        if (listener != null) {
            listener.accept(inviteId);
        }
    }

    private static void fire(Runnable listener) {
        if (listener != null) {
            listener.run();
        }
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        try {
            return i18n.get(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    public Stage stage() {
        return stage;
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    public void render(float deltaTime) {
        stage.act(deltaTime);
        stage.draw();
    }

    /** KHÔNG dispose {@link #skin} — của {@link CombatHud}, xem javadoc constructor. */
    @Override
    public void dispose() {
        stage.getBatch().dispose();
        stage.dispose();
    }
}
