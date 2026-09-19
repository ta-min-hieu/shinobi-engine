package dev.kage.client.core.screen;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

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
import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.client.core.world.PlayContext;
import dev.kage.client.core.world.WorldScreen;
import dev.kage.engine.core.GameScreen;
import dev.kage.engine.core.KageEngine;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Faction;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Danh sách nhân vật của account (nhận từ {@link LoginScreen}) — chọn 1 hàng để vào game, hoặc tạo
 * nhân vật mới nếu danh sách trống/muốn thêm. Chọn xong ({@code SelectCharacterRequest} thành công)
 * chuyển sang {@link WorldScreen} — điểm cuối của toàn bộ chuỗi Login → CharacterSelect → World.
 */
public final class CharacterSelectScreen implements GameScreen {

    private static final Logger LOG = LoggerFactory.getLogger(CharacterSelectScreen.class);

    private static final float STEP_TIMEOUT_SECONDS = 5f;

    private enum State {
        LISTING,
        AWAITING_CREATE_RESPONSE,
        AWAITING_SELECT_RESPONSE,
        FAILED
    }

    /**
     * Bề rộng một dòng nhân vật.
     *
     * <p>Nới từ 340px lên 440px khi cấp độ chuyển sang hiển thị bằng CẢNH GIỚI (ADR-038 D2). Dòng
     * này là một {@code TextButton} bề rộng CỐ ĐỊNH, chữ không xuống dòng, nên nó phải chứa được
     * trường hợp DÀI NHẤT chứ không phải trường hợp thường gặp: tên nhân vật tối đa 16 ký tự +
     * linh căn dài nhất ("Phong") + cảnh giới dài nhất ("Nguyên Anh 9") ≈ 38 ký tự. Ở cỡ chữ HUD
     * (~9,7px/ký tự, đo trên ảnh chụp {@code runHudPreview}) là ~369px — vượt 340px cũ.
     *
     * <p>⚠️ Bề rộng cũ vốn ĐÃ sát trần trước đợt này: "(Cấp 100)" cho ra ~330px trên cùng cái tên
     * 16 ký tự đó. Nới thêm là trả lại khoảng thở cho cả hai, không riêng cảnh giới.
     */
    private static final float CHARACTER_ROW_WIDTH_PX = 440f;

    /** Khung chứa rộng hơn dòng đúng 20px — giữ nguyên tỉ lệ lề của bố cục cũ (360 so với 340). */
    private static final float CHARACTER_LIST_WIDTH_PX = 460f;

    private final KageEngine engine;
    private final PlayContext context;
    private final GameConnection connection;
    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;

    private final Table characterListTable;
    private final TextField newCharacterNameField;
    private final TextButton createButton;
    private final Label statusLabel;

    private State state = State.LISTING;
    private float stateElapsed;
    private CharacterSummary pendingSelection;

    public CharacterSelectScreen(
            KageEngine engine, PlayContext context, GameConnection connection, List<CharacterSummary> characters) {
        this.engine = engine;
        this.context = context;
        this.connection = connection;
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());
        this.skin = new KageSkin();
        this.i18n = KageI18n.load();

        Label titleLabel = new Label(i18n.get("charselect.title"), skin.labelStyle);

        this.characterListTable = new Table();
        rebuildCharacterRows(characters);

        this.newCharacterNameField = new TextField("", skin.textFieldStyle);
        newCharacterNameField.setMessageText(i18n.get("charselect.newname.hint"));

        this.createButton = new TextButton(i18n.get("charselect.button.create"), skin.buttonStyle);
        createButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onCreateClicked();
            }
        });

        this.statusLabel = new Label("", skin.labelStyle);

        Table root = new Table();
        root.setFillParent(true);
        root.top();
        root.add(titleLabel).padTop(32f).padBottom(16f).row();
        root.add(characterListTable)
                .width(CHARACTER_LIST_WIDTH_PX)
                .padBottom(24f)
                .row();
        root.add(newCharacterNameField).width(280f).height(48f).padBottom(12f).row();
        root.add(createButton).size(200f, 48f).padBottom(12f).row();
        root.add(statusLabel);

        stage.addActor(root);
    }

    private void rebuildCharacterRows(List<CharacterSummary> characters) {
        characterListTable.clear();
        if (characters.isEmpty()) {
            characterListTable.add(new Label(i18n.get("charselect.empty"), skin.labelStyle));
            return;
        }
        for (CharacterSummary character : characters) {
            String rowText = i18n.format(
                    "charselect.character.summary",
                    character.getName(),
                    factionLabel(character.getFaction()),
                    realmLabel(character.getLevel()));
            TextButton row = new TextButton(rowText, skin.buttonStyle);
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onCharacterRowClicked(character);
                }
            });
            characterListTable
                    .add(row)
                    .size(CHARACTER_ROW_WIDTH_PX, 44f)
                    .padBottom(8f)
                    .row();
        }
    }

    /**
     * Cấp độ hiện ra dưới dạng CẢNH GIỚI — ADR-038 D2
     * ({@code docs/01-architecture/adr/ADR-038-Theme-Pivot-Tu-Tien.md}).
     *
     * <p>Dùng bản NGẮN ({@code realm.tier_short} → "Luyện Khí 6") chứ không phải bản đầy đủ: dòng
     * này nằm trong một {@code TextButton} bề rộng CỐ ĐỊNH (xem {@link #CHARACTER_ROW_WIDTH_PX})
     * cùng với tên nhân vật (tối đa 16 ký tự) và tên linh căn. Bản đầy đủ ("Nguyên Anh tầng 9")
     * dài thêm 5 ký tự nữa, đủ để đẩy trường hợp tên dài nhất tràn khỏi nút ngay cả sau khi đã
     * nới — đo ở trạng thái tệ nhất, không phải ở tên nhân vật ngắn của mình.
     */
    private String realmLabel(int level) {
        CultivationRealm realm = CultivationRealm.of(level);
        String realmName = i18n.get(realm.nameKey());
        return realm.perfected()
                ? i18n.format("realm.perfect", realmName)
                : i18n.format("realm.tier_short", realmName, realm.tier());
    }

    private String factionLabel(Faction faction) {
        String key =
                switch (faction) {
                    case FACTION_HOA -> "faction.hoa";
                    case FACTION_THUY -> "faction.thuy";
                    case FACTION_PHONG -> "faction.phong";
                    case FACTION_LOI -> "faction.loi";
                    case FACTION_THO -> "faction.tho";
                    case FACTION_UNSPECIFIED, UNRECOGNIZED -> "faction.unspecified";
                };
        return i18n.get(key);
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

    private void onCharacterRowClicked(CharacterSummary character) {
        if (state != State.LISTING && state != State.FAILED) {
            return; // Đang chờ response cho hành động khác — bấm thêm không làm gì.
        }
        selectCharacter(character);
    }

    private void onCreateClicked() {
        if (state != State.LISTING && state != State.FAILED) {
            return;
        }
        String name = newCharacterNameField.getText().trim();
        if (name.isEmpty()) {
            return;
        }
        try {
            CreateCharacterRequest request = CreateCharacterRequest.newBuilder()
                    .setName(name)
                    .setIdempotencyKey(UUID.randomUUID().toString())
                    .build();
            connection.send(
                    Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, connection.nextSequenceId(), request.toByteArray());
            setFormEnabled(false);
            statusLabel.setText(i18n.get("charselect.status.creating"));
            transitionTo(State.AWAITING_CREATE_RESPONSE);
        } catch (IOException e) {
            LOG.debug("Gửi CreateCharacterRequest thất bại (mất kết nối tạm thời)", e);
            failWithGenericError();
        }
    }

    private void selectCharacter(CharacterSummary character) {
        try {
            this.pendingSelection = character;
            SelectCharacterRequest request = SelectCharacterRequest.newBuilder()
                    .setCharacterId(character.getId())
                    .build();
            connection.send(
                    Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, connection.nextSequenceId(), request.toByteArray());
            setFormEnabled(false);
            statusLabel.setText(i18n.get("charselect.status.selecting"));
            transitionTo(State.AWAITING_SELECT_RESPONSE);
        } catch (IOException e) {
            LOG.debug("Gửi SelectCharacterRequest thất bại (mất kết nối tạm thời)", e);
            failWithGenericError();
        }
    }

    private void transitionTo(State next) {
        state = next;
        stateElapsed = 0f;
    }

    @Override
    public void fixedUpdate(float fixedDeltaTime) {
        stateElapsed += fixedDeltaTime;
        switch (state) {
            case AWAITING_CREATE_RESPONSE -> pollCreateResponse();
            case AWAITING_SELECT_RESPONSE -> pollSelectResponse();
            // LISTING/FAILED — chờ người chơi chọn/tạo nhân vật, không có gì để làm mỗi tick.
            // `default` thay vì liệt kê 2 case (MissingSwitchDefault, xem LoginScreen#fixedUpdate).
            default -> {}
        }
    }

    private void pollCreateResponse() {
        PacketCodec.Frame frame;
        while ((frame = connection.pollInbound()) != null) {
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE) {
                handleCreateResponse(frame.payload());
                return;
            }
            if (opcode == Opcode.OPCODE_ERROR_VALUE) {
                String errorKey = errorKeyOf(frame.payload());
                LOG.warn("Server từ chối CreateCharacterRequest: error_key={}", errorKey);
                failWith(errorKey);
                return;
            }
        }
        if (stateElapsed > STEP_TIMEOUT_SECONDS) {
            failWithGenericError();
        }
    }

    private void handleCreateResponse(byte[] payload) {
        try {
            CreateCharacterResponse response = CreateCharacterResponse.parseFrom(payload);
            if (!response.getSuccess()) {
                LOG.warn("Tạo nhân vật thất bại: errorKey={}", response.getErrorKey());
                failWith(response.getErrorKey());
                return;
            }
            newCharacterNameField.setText("");
            selectCharacter(response.getCharacter());
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("CreateCharacterResponse không hợp lệ", e);
            failWithGenericError();
        }
    }

    private void pollSelectResponse() {
        PacketCodec.Frame frame;
        while ((frame = connection.pollInbound()) != null) {
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE) {
                handleSelectResponse(frame.payload());
                return;
            }
            if (opcode == Opcode.OPCODE_ERROR_VALUE) {
                String errorKey = errorKeyOf(frame.payload());
                LOG.warn("Server từ chối SelectCharacterRequest: error_key={}", errorKey);
                failWith(errorKey);
                return;
            }
        }
        if (stateElapsed > STEP_TIMEOUT_SECONDS) {
            failWithGenericError();
        }
    }

    private void handleSelectResponse(byte[] payload) {
        try {
            SelectCharacterResponse response = SelectCharacterResponse.parseFrom(payload);
            if (!response.getSuccess()) {
                LOG.warn("Chọn nhân vật thất bại: errorKey={}", response.getErrorKey());
                failWithGenericError();
                return;
            }
            engine.setScreen(new WorldScreen(context, connection, pendingSelection, response));
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("SelectCharacterResponse không hợp lệ", e);
            failWithGenericError();
        }
    }

    private void failWithGenericError() {
        failWith(null);
    }

    /**
     * Nói LÝ DO server đưa ra, không phải một câu chung cho mọi thứ.
     *
     * <p>Trước 2026-09-19 mọi nhánh hỏng ở màn này đều rơi về {@code charselect.error.generic} và
     * {@code error_key} thật chỉ đi vào một dòng log. Người chơi tạo nhân vật trùng tên vì thế thấy
     * đúng câu mà họ thấy khi máy chủ sập — và chuyện trùng tên thì chắc chắn xảy ra ngay ngày đầu
     * Closed Beta.
     *
     * @param errorKey khoá server gửi, hoặc {@code null} khi hỏng vì lý do không có khoá (hết giờ
     *     chờ, payload không parse được) — lúc đó câu chung mới đúng là câu đúng
     */
    private void failWith(String errorKey) {
        setFormEnabled(true);
        statusLabel.setText(KageI18n.errorText(i18n, errorKey, "charselect.error.generic"));
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
        newCharacterNameField.setDisabled(!enabled);
        createButton.setDisabled(!enabled);
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
}
