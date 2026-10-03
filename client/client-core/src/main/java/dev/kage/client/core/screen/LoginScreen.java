package dev.kage.client.core.screen;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.net.GameConnection;
import dev.kage.client.core.ui.theme.ClientLanguage;
import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.client.core.ui.theme.LanguageSettings;
import dev.kage.client.core.ui.theme.LanguageStore;
import dev.kage.client.core.world.PlayContext;
import dev.kage.engine.core.GameScreen;
import dev.kage.engine.core.KageEngine;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Màn hình đầu tiên người chơi thấy (GameScreen javadoc: "Splash → Loading → Login → ... →
 * InGame") — nhập username, tự tạo+kết nối {@link GameConnection}, đăng nhập
 * ({@code ConnectRequest}) rồi xin danh sách nhân vật ({@code ListCharactersRequest}) trước khi
 * chuyển sang {@link CharacterSelectScreen}.
 *
 * <p>KHÔNG có mật khẩu — MVP cut có chủ đích (system.proto {@code ConnectRequest} chỉ có username,
 * xem ADR-017). {@link GameConnection#start()} chạy nền và tự retry backoff, nên chờ
 * {@link GameConnection#isConnected()} true rồi mới gửi packet đầu tiên (CLIENT.md §8: xử lý packet
 * đầu tick, không block render thread bằng vòng lặp đồng bộ như {@code NetworkBootstrap} cũ).
 *
 * <p><b>Chọn ngôn ngữ</b> (song ngữ Việt–Anh, quyết định chủ dự án 2026-10-01) nằm ở đây — màn hình
 * DUY NHẤT người chơi luôn đi qua trước khi vào thế giới, và là chỗ duy nhất đổi được ngôn ngữ (xem
 * {@link KageI18n}). Đổi xong màn này tự nạp lại bundle và viết lại mọi chữ đang hiện; mọi màn dựng
 * sau đó tự nạp đúng ngôn ngữ mới.
 */
public final class LoginScreen implements GameScreen {

    private static final Logger LOG = LoggerFactory.getLogger(LoginScreen.class);

    private static final String GATEWAY_HOST = "127.0.0.1";
    private static final int GATEWAY_PORT = 7777;
    private static final float CONNECT_TIMEOUT_SECONDS = 10f;
    private static final float STEP_TIMEOUT_SECONDS = 5f;

    /** Nút chọn ngôn ngữ — đo nhãn ở cả hai ngôn ngữ bằng {@code ScreenButtonLabelFitTest}. */
    static final float LANGUAGE_BUTTON_WIDTH_PX = 132f;

    /** Nút Kết nối — hằng thay cho số trần để {@code ScreenButtonLabelFitTest} đo được nhãn ở cả hai ngôn ngữ. */
    static final float CONNECT_BUTTON_WIDTH_PX = 160f;

    private static final float USERNAME_FIELD_WIDTH_PX = 280f;
    private static final float CONTROL_HEIGHT_PX = 48f;
    private static final float LANGUAGE_BUTTON_GAP_PX = 8f;

    private enum State {
        ENTER_USERNAME,
        CONNECTING,
        AWAITING_CONNECT_RESPONSE,
        AWAITING_CHARACTER_LIST,
        FAILED
    }

    private final KageEngine engine;
    private final PlayContext context;
    private final Stage stage;
    private final KageSkin skin;
    private final LanguageStore languageStore;

    /** KHÔNG final: đổi ngôn ngữ thì nạp lại ({@link #switchLanguage}). */
    private I18NBundle i18n;

    private final Label titleLabel;
    private final TextField usernameField;
    private final TextButton connectButton;
    private final Label statusLabel;
    private final Label languageLabel;
    private final Map<ClientLanguage, TextButton> languageButtons = new EnumMap<>(ClientLanguage.class);

    /**
     * Dòng trạng thái được nhớ dưới dạng KHOÁ chứ không phải chữ đã dịch — đổi ngôn ngữ ngay sau một
     * lần đăng nhập thất bại thì câu lỗi cũng phải đổi theo, không kẹt lại thứ tiếng cũ.
     */
    private StatusLine statusLine = StatusLine.NONE;

    private State state = State.ENTER_USERNAME;
    private float stateElapsed;
    private String username = "";
    private GameConnection connection;

    /**
     * {@code sequence_id} của yêu cầu đang chờ — chỉ câu trả lời mang đúng số này mới được nhận, câu
     * trả lời muộn của lần bấm trước bị bỏ ({@link PendingReply}).
     */
    private int pendingSequenceId;

    /** Giữ một lần thay vì tạo method reference mới mỗi tick. */
    private final Supplier<PacketCodec.Frame> inbound = this::pollConnection;

    /** @param initialUsername điền sẵn ô username, tuỳ chọn — {@code null}/rỗng để trống. */
    public LoginScreen(KageEngine engine, PlayContext context, String initialUsername) {
        this.engine = engine;
        this.context = context;
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());
        this.skin = new KageSkin();
        // Đọc lựa chọn đã lưu TRƯỚC lần nạp bundle đầu tiên — màn này luôn là màn đầu của tiến trình,
        // và cũng là màn người chơi quay về khi phiên kết thúc.
        this.languageStore = new GdxPreferencesLanguageStore();
        KageI18n.useLanguage(LanguageSettings.load(languageStore));
        this.i18n = KageI18n.load();

        this.titleLabel = new Label("", skin.labelStyle);
        this.usernameField = new TextField(initialUsername == null ? "" : initialUsername, skin.textFieldStyle);
        this.statusLabel = new Label("", skin.labelStyle);
        this.languageLabel = new Label("", skin.labelStyle);

        this.connectButton = new TextButton("", skin.buttonStyle);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onConnectClicked();
            }
        });

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        root.add(titleLabel).padBottom(24f).row();
        root.add(usernameField)
                .width(USERNAME_FIELD_WIDTH_PX)
                .height(CONTROL_HEIGHT_PX)
                .padBottom(12f)
                .row();
        root.add(connectButton)
                .size(CONNECT_BUTTON_WIDTH_PX, CONTROL_HEIGHT_PX)
                .padBottom(12f)
                .row();
        root.add(statusLabel).padBottom(24f).row();
        root.add(languageLabel).padBottom(8f).row();
        root.add(buildLanguageRow());

        stage.addActor(root);
        refreshTexts();
        refreshLanguageButtons(true);
    }

    /** Một nút cho mỗi {@link ClientLanguage}, nhãn là tên ngôn ngữ viết bằng chính ngôn ngữ đó. */
    private Table buildLanguageRow() {
        Table row = new Table();
        for (ClientLanguage language : ClientLanguage.values()) {
            TextButton button = new TextButton("", skin.buttonStyle);
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // ClickListener vẫn báo click trên nút đã khoá — phải tự chặn (khác ChangeListener).
                    if (!button.isDisabled()) {
                        switchLanguage(language);
                    }
                }
            });
            languageButtons.put(language, button);
            row.add(button)
                    .size(LANGUAGE_BUTTON_WIDTH_PX, CONTROL_HEIGHT_PX)
                    .padLeft(LANGUAGE_BUTTON_GAP_PX / 2f)
                    .padRight(LANGUAGE_BUTTON_GAP_PX / 2f);
        }
        return row;
    }

    /**
     * Đổi ngôn ngữ: lưu xuống máy, nạp lại bundle, viết lại mọi chữ đang hiện. Nút bị khoá lúc đang
     * kết nối ({@link #setFormEnabled}), nên không bao giờ đổi giữa chừng một lần đăng nhập.
     */
    private void switchLanguage(ClientLanguage language) {
        if (language == KageI18n.currentLanguage()) {
            return;
        }
        KageI18n.useLanguage(language);
        LanguageSettings.save(languageStore, language);
        this.i18n = KageI18n.load();
        refreshTexts();
        refreshLanguageButtons(true);
    }

    /** Viết lại mọi chữ của màn này từ bundle hiện tại — gọi lúc dựng và mỗi lần đổi ngôn ngữ. */
    private void refreshTexts() {
        titleLabel.setText(i18n.get("login.title"));
        usernameField.setMessageText(i18n.get("login.username.hint"));
        connectButton.setText(i18n.get("login.button.connect"));
        languageLabel.setText(i18n.format(
                "login.language.current", i18n.get(KageI18n.currentLanguage().nameKey())));
        for (Map.Entry<ClientLanguage, TextButton> entry : languageButtons.entrySet()) {
            entry.getValue().setText(i18n.get(entry.getKey().nameKey()));
        }
        statusLabel.setText(statusLine.render(i18n));
    }

    /**
     * Nút của ngôn ngữ ĐANG dùng luôn khoá (bấm lại vô nghĩa); các nút khác mở/khoá theo form. Ngôn
     * ngữ đang dùng còn được nói bằng chữ ở {@code languageLabel} — màu xám của nút khoá không phải
     * thông tin duy nhất (UI.md: không dùng màu làm thông tin duy nhất).
     */
    private void refreshLanguageButtons(boolean formEnabled) {
        ClientLanguage current = KageI18n.currentLanguage();
        for (Map.Entry<ClientLanguage, TextButton> entry : languageButtons.entrySet()) {
            entry.getValue().setDisabled(!formEnabled || entry.getKey() == current);
        }
    }

    private void showStatus(StatusLine line) {
        this.statusLine = line;
        statusLabel.setText(line.render(i18n));
    }

    /**
     * Hiện lý do người chơi vừa bị đưa về đây từ màn world — ví dụ {@code error.logged_in_elsewhere}
     * (bị đá vì nhân vật vừa vào thế giới từ kết nối khác, quyết định chủ dự án 2026-09-25).
     *
     * @param errorKey khoá i18n server gửi; khoá lạ rơi về câu lỗi kết nối chung
     */
    public void showSessionEndedReason(String errorKey) {
        showStatus(StatusLine.error(errorKey, "login.error.connect_failed"));
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    private void onConnectClicked() {
        if (state != State.ENTER_USERNAME && state != State.FAILED) {
            return; // Nút đã bị disable lúc đang xử lý — chặn kép cho chắc, không dựa mỗi UI state.
        }
        String enteredUsername = usernameField.getText().trim();
        if (enteredUsername.isEmpty()) {
            return;
        }
        this.username = enteredUsername;
        this.connection = new GameConnection(GATEWAY_HOST, GATEWAY_PORT);
        connection.start();
        setFormEnabled(false);
        showStatus(StatusLine.of("login.status.connecting"));
        transitionTo(State.CONNECTING);
    }

    private void transitionTo(State next) {
        state = next;
        stateElapsed = 0f;
    }

    @Override
    public void fixedUpdate(float fixedDeltaTime) {
        stateElapsed += fixedDeltaTime;
        switch (state) {
            case CONNECTING -> tickConnecting();
            case AWAITING_CONNECT_RESPONSE -> pollConnectResponse();
            case AWAITING_CHARACTER_LIST -> pollCharacterList();
            // ENTER_USERNAME/FAILED — chờ người chơi bấm nút, không có gì để làm mỗi tick. `default`
            // thay vì liệt kê 2 case (MissingSwitchDefault checkstyle bắt switch STATEMENT dù
            // enum switch đã exhaustive — khác switch EXPRESSION như CharacterModelKey#factionSegment).
            default -> {}
        }
    }

    private void tickConnecting() {
        if (connection.isConnected()) {
            sendConnectRequest();
            return;
        }
        if (stateElapsed > CONNECT_TIMEOUT_SECONDS) {
            failWithGenericError();
        }
    }

    private void sendConnectRequest() {
        try {
            ConnectRequest request =
                    ConnectRequest.newBuilder().setUsername(username).build();
            pendingSequenceId = connection.nextSequenceId();
            connection.send(Opcode.OPCODE_CONNECT_REQUEST_VALUE, pendingSequenceId, request.toByteArray());
            showStatus(StatusLine.of("login.status.handshaking"));
            transitionTo(State.AWAITING_CONNECT_RESPONSE);
        } catch (IOException e) {
            LOG.debug("Gửi ConnectRequest thất bại (mất kết nối tạm thời)", e);
            failWithGenericError();
        }
    }

    private void pollConnectResponse() {
        Optional<PacketCodec.Frame> reply =
                PendingReply.poll(inbound, pendingSequenceId, Opcode.OPCODE_CONNECT_RESPONSE_VALUE);
        if (reply.isPresent()) {
            PacketCodec.Frame frame = reply.get();
            if (frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE) {
                String errorKey = errorKeyOf(frame.payload());
                LOG.warn("Server từ chối ConnectRequest: error_key={}", errorKey);
                failWith(errorKey);
            } else {
                handleConnectResponse(frame.payload());
            }
            return;
        }
        if (stateElapsed > STEP_TIMEOUT_SECONDS) {
            failWithGenericError();
        }
    }

    private void handleConnectResponse(byte[] payload) {
        try {
            ConnectResponse response = ConnectResponse.parseFrom(payload);
            if (!response.getSuccess()) {
                LOG.warn("Đăng nhập bị server từ chối: errorKey={}", response.getErrorKey());
                failWith(response.getErrorKey());
                return;
            }
            sendListCharactersRequest();
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("ConnectResponse không hợp lệ", e);
            failWithGenericError();
        }
    }

    private void sendListCharactersRequest() {
        try {
            pendingSequenceId = connection.nextSequenceId();
            connection.send(
                    Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE,
                    pendingSequenceId,
                    ListCharactersRequest.newBuilder().build().toByteArray());
            showStatus(StatusLine.of("login.status.loading_characters"));
            transitionTo(State.AWAITING_CHARACTER_LIST);
        } catch (IOException e) {
            LOG.debug("Gửi ListCharactersRequest thất bại (mất kết nối tạm thời)", e);
            failWithGenericError();
        }
    }

    private void pollCharacterList() {
        Optional<PacketCodec.Frame> reply =
                PendingReply.poll(inbound, pendingSequenceId, Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE);
        if (reply.isPresent()) {
            PacketCodec.Frame frame = reply.get();
            if (frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE) {
                String errorKey = errorKeyOf(frame.payload());
                LOG.warn("Server từ chối ListCharactersRequest: error_key={}", errorKey);
                failWith(errorKey);
            } else {
                handleCharacterList(frame.payload());
            }
            return;
        }
        if (stateElapsed > STEP_TIMEOUT_SECONDS) {
            failWithGenericError();
        }
    }

    private void handleCharacterList(byte[] payload) {
        try {
            ListCharactersResponse response = ListCharactersResponse.parseFrom(payload);
            engine.setScreen(new CharacterSelectScreen(
                    engine, context, new AccountSession(connection, username), response.getCharactersList()));
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("ListCharactersResponse không hợp lệ", e);
            failWithGenericError();
        }
    }

    /** {@code null} khi chưa có kết nối — {@link PendingReply#poll} coi như hàng đợi rỗng. */
    private PacketCodec.Frame pollConnection() {
        return connection == null ? null : connection.pollInbound();
    }

    private void failWithGenericError() {
        failWith(null);
    }

    /**
     * Nói LÝ DO server đưa ra, không phải một câu chung cho mọi thứ — cùng mẫu
     * {@code CharacterSelectScreen#failWith}.
     *
     * <p>Trước 2026-09-19, mọi nhánh hỏng ở màn này (kể cả {@code error.invalid_username} — người
     * chơi CÓ THỂ sửa — và {@code error.login_failed}/{@code error.list_characters_failed} — lỗi
     * MÁY CHỦ, không phải người chơi làm sai) đều rơi về đúng MỘT câu {@code login.error.connect_failed}.
     * Ba khoá đó (cộng {@code error.not_authenticated}) đã có bản dịch từ trước (cổng build
     * {@code verifyServerErrorKeys} đòi mọi {@code error.*} literal ở server phải có mặt trong cả
     * hai bundle) — chỉ là màn hình này chưa từng đọc {@code error_key} để dùng chúng.
     *
     * @param errorKey khoá server gửi, hoặc {@code null} khi hỏng vì lý do không có khoá (hết giờ
     *     chờ, mất kết nối giữa chừng gửi gói, payload không parse được) — lúc đó câu chung mới
     *     đúng là câu đúng
     */
    private void failWith(String errorKey) {
        if (connection != null) {
            connection.stop();
        }
        setFormEnabled(true);
        showStatus(StatusLine.error(errorKey, "login.error.connect_failed"));
        transitionTo(State.FAILED);
    }

    /** {@code error_key} trong một khung {@code ErrorResponse}, hoặc {@code null} nếu không đọc được. */
    private String errorKeyOf(byte[] payload) {
        try {
            return ErrorResponse.parseFrom(payload).getErrorKey();
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("ErrorResponse không hợp lệ", e);
            return null;
        }
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setDisabled(!enabled);
        connectButton.setDisabled(!enabled);
        refreshLanguageButtons(enabled);
    }

    @Override
    public void render(float alpha) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }

    /**
     * Một dòng trạng thái dưới dạng khoá — dịch lại được bất cứ lúc nào.
     *
     * @param key khoá i18n của câu; {@code null} = dòng trống
     * @param errorKey {@code error_key} server gửi (dịch qua {@link KageI18n#errorText}, khoá lạ rơi
     *     về {@code key}), hoặc {@code null} khi đây là câu thường
     */
    private record StatusLine(String key, String errorKey) {

        static final StatusLine NONE = new StatusLine(null, null);

        static StatusLine of(String key) {
            return new StatusLine(key, null);
        }

        static StatusLine error(String errorKey, String fallbackKey) {
            return new StatusLine(fallbackKey, errorKey);
        }

        String render(I18NBundle bundle) {
            if (key == null) {
                return "";
            }
            return errorKey == null ? bundle.get(key) : KageI18n.errorText(bundle, errorKey, key);
        }
    }
}
