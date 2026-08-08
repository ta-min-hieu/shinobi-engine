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
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.client.core.world.PlayContext;
import dev.kage.client.core.world.WorldScreen;
import dev.kage.engine.core.GameScreen;
import dev.kage.engine.core.KageEngine;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
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
        root.add(characterListTable).width(360f).padBottom(24f).row();
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
                    character.getLevel());
            TextButton row = new TextButton(rowText, skin.buttonStyle);
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onCharacterRowClicked(character);
                }
            });
            characterListTable.add(row).size(340f, 44f).padBottom(8f).row();
        }
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
                LOG.warn("Server từ chối CreateCharacterRequest (ErrorResponse)");
                failWithGenericError();
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
                failWithGenericError();
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
                LOG.warn("Server từ chối SelectCharacterRequest (ErrorResponse)");
                failWithGenericError();
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
            engine.setScreen(new WorldScreen(context, connection, pendingSelection));
        } catch (InvalidProtocolBufferException e) {
            LOG.warn("SelectCharacterResponse không hợp lệ", e);
            failWithGenericError();
        }
    }

    private void failWithGenericError() {
        setFormEnabled(true);
        statusLabel.setText(i18n.get("charselect.error.generic"));
        transitionTo(State.FAILED);
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
