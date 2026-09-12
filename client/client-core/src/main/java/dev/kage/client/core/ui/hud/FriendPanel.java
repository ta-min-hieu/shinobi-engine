package dev.kage.client.core.ui.hud;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

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
 * Cửa sổ bạn bè (2026-08-25) — lời mời đang chờ + danh sách bạn + ô gõ tên để kết bạn, neo GIỮA màn
 * hình (đây là cửa sổ người chơi chủ động mở rồi đóng, không phải thông tin liếc qua như HUD).
 *
 * <p>{@code Stage} RIÊNG cùng lý do {@link ChatPanel}/{@link DialoguePanel}: thứ tự nuốt input chỉ
 * đặt được cho cả một {@code Stage} qua {@code InputMultiplexer}, không đặt được cho một actor bên
 * trong. Click vào cửa sổ này phải bị nuốt chứ không rơi xuống thế giới thành lệnh chọn mục tiêu.
 *
 * <p>Widget chỉ VẼ (UI.md §4): nó không giữ {@code character_id} hay {@code friendship_id} nào,
 * không biết tên thật (chưa sanitize) của ai, chỉ báo ra CHỈ SỐ dòng vừa bấm — đúng quy ước
 * {@code option_index} của {@link DialoguePanel}, và ở đây còn có thêm một lý do: tên hiển thị đã bị
 * {@code ChatController.sanitize} đổi ký tự không vẽ được thành {@code ?}, gửi ngược chuỗi đó lên
 * server là gửi một cái tên KHÁC với tên thật.
 */
public final class FriendPanel implements HudWindow {

    /**
     * Phím mở cửa sổ. Nằm ở đây chứ không ở {@code GameAction} vì đây KHÔNG phải ý định gameplay —
     * cùng lập luận với phím T của {@link ChatPanel} và ESC của {@link DialoguePanel}: không đổi
     * trạng thái thế giới, chỉ mở/dọn UI của chính máy này.
     */
    private static final int OPEN_KEY = Input.Keys.F;

    private static final float PANEL_WIDTH_PX = 460f;

    private static final float PANEL_PAD_PX = 16f;

    private static final float SECTION_PAD_PX = 8f;

    private static final float ROW_GAP_PX = 6f;

    /**
     * Chiều cao TỐI ĐA của vùng cuộn lời mời (≈ 3 dòng 52px); dài hơn thì cuộn. Số dòng được DỰNG
     * là {@link ScrollingList#MAX_BUILT_ROWS}; phần vượt trần đó vẫn nói ra bằng dòng "còn N dòng nữa".
     *
     * <p>Khác sáu cửa sổ kia, MỐI LO cũ ở đây vẫn đúng: danh sách bạn bè không có trần phía server
     * (V12 không có cột sức chứa, không {@code LIMIT}), và cửa sổ neo giữa màn hình nở cả hai phía —
     * danh sách tự nở theo số bạn là đẩy ô kết bạn ra khỏi vùng nhìn thấy. Nhưng CÁCH CHỮA cũ — trần
     * {@code MAX_VISIBLE_ROWS = 8} cho MỖI mục — không chữa được: hai mục 8 dòng cùng lúc cao
     * <b>1044px</b> trong màn hình 720px ({@code HudLayoutBudgetTool}), còn người bạn thứ 9 thì không
     * xoá được và lời mời thứ 9 không trả lời được. Chặn CHIỀU CAO bằng vùng cuộn mới chữa đúng chỗ.
     */
    private static final float REQUEST_LIST_MAX_HEIGHT_PX = 156f;

    /** Xem {@link #REQUEST_LIST_MAX_HEIGHT_PX} — ≈ 5 dòng, danh sách bạn dài hơn danh sách lời mời. */
    private static final float FRIEND_LIST_MAX_HEIGHT_PX = 260f;

    private static final float NAME_COLUMN_WIDTH_PX = 250f;

    /**
     * Cột tên HẸP HƠN ở dòng bạn bè, vì dòng đó còn phải chứa chữ "Trực tuyến"/"Ngoại tuyến" bên
     * cạnh. Dùng chung một bề rộng với dòng lời mời thì tên dài đẩy chữ trạng thái ra ngoài khung và
     * nó bị cắt cụt thành "Ng" — thấy thật trên ảnh {@code runHudPreview}.
     */
    private static final float FRIEND_NAME_COLUMN_WIDTH_PX = 210f;

    private static final float ACTION_BUTTON_WIDTH_PX = 92f;

    /** Trần ký tự ô nhập tên — khớp {@code FriendController.MAX_NAME_LENGTH} (world/ không với tới). */
    private static final int NAME_INPUT_MAX_LENGTH = 16;

    /** Xanh lá nhạt — đang trực tuyến. Cùng tông {@link ChatPanel} dùng cho kênh Khu vực. */
    private static final Color ONLINE_COLOR = Color.valueOf("9CE08A");

    /** Xám — ngoại tuyến. KHÔNG dùng màu làm thông tin DUY NHẤT (client/CLAUDE.md): có chữ đi kèm. */
    private static final Color OFFLINE_COLOR = Color.valueOf("9A9A9A");

    /** Vàng đồng nhạt — dòng thông báo kết quả, cùng tông viền HUD. */
    private static final Color NOTICE_COLOR = Color.valueOf("E0B860");

    /** Một dòng bạn bè để VẼ — không mang id, xem javadoc lớp. */
    public record FriendRow(String displayName, boolean online) {}

    /** Một dòng lời mời để VẼ — không mang {@code friendship_id}, xem javadoc lớp. */
    public record RequestRow(String requesterName) {}

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;
    private final Table panel;
    private final Table friendList;
    private final Table requestList;
    private final TextField nameInput;
    private final Label notice;

    private Consumer<String> onSendRequest;
    private IntConsumer onAccept;
    private IntConsumer onReject;
    private IntConsumer onRemove;
    private Runnable onOpened;

    /** Xem {@link #setCanOpen}. Mặc định LUÔN cho mở, để lớp này tự chạy được trong test/preview. */
    private BooleanSupplier canOpen = () -> true;

    /**
     * @param skin bộ style MƯỢN của {@link CombatHud} — lớp này KHÔNG dispose nó, cùng quy ước
     *     {@link ChatPanel}/{@link DialoguePanel}.
     */
    public FriendPanel(KageSkin skin) {
        this.skin = skin;
        this.i18n = KageI18n.load();
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());

        this.friendList = new Table();
        this.requestList = new Table();
        this.notice = new Label("", skin.labelStyle);
        this.notice.setColor(NOTICE_COLOR);
        this.notice.setWrap(true);

        this.nameInput = new TextField("", skin.textFieldStyle);
        this.nameInput.setMessageText(text("friend.input.hint"));
        // Gõ được đúng những gì font vẽ được — nếu không, ký tự vừa gõ BIẾN MẤT ngay trong ô nhập
        // mà không có báo lỗi nào (xem FontFactory#canRender). Tên nhân vật cho phép cả chữ Hán
        // (server: [\p{L}\p{N}_]+), nên đây không phải trường hợp giả định.
        this.nameInput.setTextFieldFilter((field, character) -> FontFactory.canRender(character));
        this.nameInput.setMaxLength(NAME_INPUT_MAX_LENGTH);
        this.nameInput.setTextFieldListener((field, character) -> {
            if (character == '\r' || character == '\n') {
                submitRequest();
            }
        });

        this.panel = buildPanel();
        // Nuốt click rơi vào NỀN cửa sổ (không trúng nút nào): không nuốt thì cú click đó rơi tiếp
        // xuống tầng thế giới và đổi mục tiêu đánh của người chơi sau lưng cửa sổ đang mở. Dùng
        // ClickListener trần vì nó đã trả true ở touchDown sẵn — tự override touchDown là viết một
        // hàm 5 tham số, vượt ParameterNumber max=4 (cùng lối đi ChatPanel đã chọn).
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
        built.add(label("friend.title", NOTICE_COLOR)).left().row();
        built.add(label("friend.section.requests", Color.WHITE))
                .padTop(SECTION_PAD_PX)
                .left()
                .row();
        built.add(ScrollingList.wrap(requestList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(REQUEST_LIST_MAX_HEIGHT_PX)
                .growX()
                .row();
        built.add(label("friend.section.friends", Color.WHITE))
                .padTop(SECTION_PAD_PX)
                .left()
                .row();
        built.add(ScrollingList.wrap(friendList, skin))
                .width(PANEL_WIDTH_PX)
                .maxHeight(FRIEND_LIST_MAX_HEIGHT_PX)
                .growX()
                .row();
        built.add(buildComposer())
                .padTop(SECTION_PAD_PX)
                .width(PANEL_WIDTH_PX)
                .growX()
                .row();
        built.add(notice).padTop(ROW_GAP_PX).width(PANEL_WIDTH_PX).left().row();
        built.add(label("friend.hint.close", OFFLINE_COLOR))
                .padTop(ROW_GAP_PX)
                .left()
                .row();
        built.setVisible(false); // chưa mở thì không bày một cửa sổ rỗng giữa màn hình
        return built;
    }

    /**
     * Ô nhập tên + nút Kết bạn + nút Đóng.
     *
     * <p>Nút Đóng tồn tại dù đã có ESC: cửa sổ này CHẶN toàn bộ phím gameplay khi mở (xem
     * {@link #isOpen()}), nên một lối ra chỉ-bằng-bàn-phím là một cái bẫy trên cảm ứng (UI.md) —
     * đúng lý do {@link DialoguePanel} không để cây hội thoại là lối ra duy nhất của nó.
     */
    private Table buildComposer() {
        Table composer = new Table();
        composer.add(nameInput)
                .width(PANEL_WIDTH_PX - (2f * (ACTION_BUTTON_WIDTH_PX + ROW_GAP_PX)))
                .growX();
        composer.add(button("friend.action.add", this::submitRequest))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        composer.add(button("friend.action.close", this::close))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        return composer;
    }

    /**
     * Phím F mở cửa sổ, ESC đóng.
     *
     * <p>Mở ở <b>{@code keyUp}</b> chứ không {@code keyDown} — giữ phím sinh ra một chuỗi
     * {@code keyDown}/{@code keyTyped} lặp lại; mở ở {@code keyDown} thì ngay khi {@link #nameInput}
     * nhận keyboard focus, những lần lặp còn lại của CHÍNH phím vừa bấm chảy thẳng vào ô nhập thành
     * "ffff" (đúng cái bẫy {@code ChatPanel#installKeyHandling} đã ghi lại).
     *
     * <p>Đang mở thì NUỐT mọi {@code keyUp}: {@link ChatPanel} đứng SAU trong
     * {@code InputMultiplexer} và cũng mở ô nhập của nó ở {@code keyUp} — không nuốt thì gõ chữ "t"
     * trong ô nhập tên sẽ đồng thời bật ô chat lên phía sau.
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
     * Điều kiện KHÔNG được mở cửa sổ bằng phím — người chơi đang gõ ở một ô nhập khác (chat) hoặc
     * đang nói chuyện với NPC. Cần thiết vì phím F chỉ là một ký tự bình thường trong ô nhập của
     * người ta, mà {@code Stage} này không nhìn thấy được keyboard focus của {@code Stage} khác.
     */
    public void setCanOpen(BooleanSupplier condition) {
        this.canOpen = condition;
    }

    /** Tên người chơi vừa gõ xong (Enter hoặc nút) — có thể rỗng, lọc là việc của controller. */
    public void setOnSendRequest(Consumer<String> listener) {
        this.onSendRequest = listener;
    }

    /** CHỈ SỐ dòng lời mời vừa bấm Đồng ý — xem javadoc lớp về việc widget không giữ id. */
    public void setOnAccept(IntConsumer listener) {
        this.onAccept = listener;
    }

    /** CHỈ SỐ dòng lời mời vừa bấm Từ chối. */
    public void setOnReject(IntConsumer listener) {
        this.onReject = listener;
    }

    /** CHỈ SỐ dòng bạn bè vừa bấm Xoá. */
    public void setOnRemove(IntConsumer listener) {
        this.onRemove = listener;
    }

    /**
     * Cửa sổ vừa mở — chỗ nhận phải hỏi lại server hai danh sách. Không có gói nào báo "danh sách
     * bạn bè vừa đổi" (friend.proto chỉ có 2 event online/offline), nên dữ liệu cũ tới đâu chỉ có
     * cách hỏi lại mới biết.
     */
    public void setOnOpened(Runnable listener) {
        this.onOpened = listener;
    }

    /**
     * Đang mở hay không. {@code WorldScreen} BẮT BUỘC hỏi câu này trước khi đọc phím gameplay: phím
     * di chuyển/đánh được poll thẳng qua {@code Gdx.input.isKeyPressed}, đi vòng qua lưng
     * {@code InputProcessor}, nên {@code Stage} có nuốt phím hay không cũng không ngăn được chữ "a"
     * vừa gõ đồng thời làm nhân vật chạy sang trái.
     */
    public boolean isOpen() {
        return panel.isVisible();
    }

    public void open() {
        panel.setVisible(true);
        nameInput.setText("");
        setNotice("");
        // KHÔNG tự chiếm keyboard focus khi mở. {@code TextField} của libGDX chỉ vẽ
        // messageText khi KHÔNG có focus, nên tự lấy focus là tự xoá mất chữ gợi ý của chính
        // ô đó — người chơi nhìn thấy một ô trống không nhãn, không biết phải gõ gì vào.
        // Thấy tận mắt lúc playtest 2026-08-29: bốn panel cùng dính một dòng lệnh này.
        if (onOpened != null) {
            onOpened.run();
        }
    }

    public void close() {
        panel.setVisible(false);
        nameInput.setText("");
        // Bỏ focus THẬT: ô nhập còn giữ keyboard focus là còn nuốt phím dù cửa sổ đã ẩn đi.
        stage.setKeyboardFocus(null);
    }

    /** Dòng kết quả thao tác gần nhất (chuỗi ĐÃ dịch — khoá i18n tra ở coordinator, chỗ biết kết quả). */
    public void setNotice(String text) {
        notice.setText(text);
    }

    /**
     * Vẽ lại danh sách bạn. Dựng lại toàn bộ dòng mỗi lần gọi — chỉ chạy khi server trả danh sách
     * mới hoặc có người vào/ra (vài lần một phiên chơi), không phải mỗi khung (Điều 31).
     *
     * @param rows CŨ TRƯỚC theo đúng thứ tự controller giữ; chỉ số dòng ở đây chính là chỉ số báo
     *     ngược lại qua {@link #setOnRemove}
     */
    public void setFriends(List<FriendRow> rows) {
        friendList.clear();
        if (rows.isEmpty()) {
            friendList.add(emptyLabel("friend.list.empty")).left().row();
            return;
        }
        int shown = Math.min(rows.size(), ScrollingList.MAX_BUILT_ROWS);
        for (int index = 0; index < shown; index++) {
            friendList.add(friendRow(rows.get(index), index)).growX().row();
        }
        addOverflowLine(friendList, rows.size() - shown);
    }

    /** Cùng quy ước {@link #setFriends} — chỉ số dòng là thứ báo về qua accept/reject. */
    public void setPendingRequests(List<RequestRow> rows) {
        requestList.clear();
        if (rows.isEmpty()) {
            requestList.add(emptyLabel("friend.requests.empty")).left().row();
            return;
        }
        int shown = Math.min(rows.size(), ScrollingList.MAX_BUILT_ROWS);
        for (int index = 0; index < shown; index++) {
            requestList.add(requestRow(rows.get(index), index)).growX().row();
        }
        addOverflowLine(requestList, rows.size() - shown);
    }

    private void addOverflowLine(Table list, int hidden) {
        if (hidden <= 0) {
            return;
        }
        Label label = new Label(i18n.format("friend.list.more", hidden), skin.labelStyle);
        label.setColor(OFFLINE_COLOR);
        list.add(label).left().row();
    }

    private Table friendRow(FriendRow row, int index) {
        Table line = new Table();
        // Tên và trạng thái là HAI ô riêng, không phải một chuỗi ghép: tên do người khác đặt nên độ
        // dài không đoán trước được, và nếu ghép chung thì tên dài sẽ đẩy chữ trạng thái ra khỏi
        // khung. Tên có dấu "…" khi tràn; trạng thái thì LUÔN hiện đủ vì nó là thông tin, không phải
        // trang trí.
        Label label = new Label(displayName(row.displayName()), skin.labelStyle);
        label.setEllipsis(true);
        label.setColor(row.online() ? ONLINE_COLOR : OFFLINE_COLOR);
        Label status =
                new Label(text(row.online() ? "friend.status.online" : "friend.status.offline"), skin.labelStyle);
        status.setColor(row.online() ? ONLINE_COLOR : OFFLINE_COLOR);
        line.add(label).width(FRIEND_NAME_COLUMN_WIDTH_PX).left();
        line.add(status).padLeft(ROW_GAP_PX).left().expandX();
        line.add(button("friend.action.remove", () -> fire(onRemove, index)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        return line;
    }

    private Table requestRow(RequestRow row, int index) {
        Table line = new Table();
        line.add(new Label(displayName(row.requesterName()), skin.labelStyle))
                .width(NAME_COLUMN_WIDTH_PX)
                .left();
        line.add(button("friend.action.accept", () -> fire(onAccept, index)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        line.add(button("friend.action.reject", () -> fire(onReject, index)))
                .width(ACTION_BUTTON_WIDTH_PX)
                .padLeft(ROW_GAP_PX);
        return line;
    }

    private static void fire(IntConsumer listener, int index) {
        if (listener != null) {
            listener.accept(index);
        }
    }

    /**
     * Tên trống là chuyện CÓ THẬT chứ không phải phòng xa: gateway ghép tên bằng
     * {@code characterFacade.findById(...).orElse("")} khi dựng {@code FriendView} — nhân vật bị xoá
     * mà bản ghi bạn bè còn lại sẽ về đây thành chuỗi rỗng, tức là một dòng chỉ có mỗi nút Xoá.
     */
    private String displayName(String name) {
        return name.isEmpty() ? text("friend.name.unknown") : name;
    }

    private void submitRequest() {
        String typed = nameInput.getText();
        nameInput.setText("");
        if (onSendRequest != null) {
            onSendRequest.accept(typed);
        }
    }

    private Label label(String key, Color color) {
        Label created = new Label(text(key), skin.labelStyle);
        created.setColor(color);
        return created;
    }

    private Label emptyLabel(String key) {
        return label(key, OFFLINE_COLOR);
    }

    private TextButton button(String labelKey, Runnable action) {
        TextButton created = new TextButton(text(labelKey), skin.buttonStyle);
        created.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return created;
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
