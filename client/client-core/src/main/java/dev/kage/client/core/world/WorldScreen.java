package dev.kage.client.core.world;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.input.GameAction;
import dev.kage.client.core.input.InputMapper;
import dev.kage.client.core.net.GameConnection;
import dev.kage.engine.core.GameScreen;
import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Màn hình gameplay thật đầu tiên (Phase 3 — movement) — server-authoritative, client chỉ gửi Ý
 * ĐỊNH (hướng di chuyển) và render state nhận từ server (CLIENT.md §1).
 *
 * <p>Dự đoán CỤC BỘ chỉ áp dụng cho chính nhân vật của mình (CLIENT.md §6), chạy cùng
 * {@link MovementSystem} với server nên thường khớp — sai lệch được sửa qua {@link #onMoveResponse}.
 * Nhân vật KHÁC không dự đoán, chỉ hiển thị vị trí mới nhất nhận từ {@link CharacterMovedEvent}
 * (CLIENT.md §7 muốn nội suy snapshot buffer ~100ms — CHƯA làm ở lát cắt này, follow-up).
 */
public final class WorldScreen implements GameScreen {

    private static final Logger LOG = LoggerFactory.getLogger(WorldScreen.class);

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);

    private final InputMapper inputMapper;
    private final GameConnection connection;
    private final EcsWorld world;
    private final KEntity localPlayer;
    private final Map<Long, KEntity> remoteCharacters = new HashMap<>();

    /**
     * Id dùng cho nhân vật của chính người chơi trong {@link CharacterModelRenderer}. Server đánh id
     * cho nhân vật khác; nhân vật của mình chưa bao giờ cần id nên không có. 0 an toàn vì id thật
     * sinh từ sequence của DB, luôn dương.
     */
    private static final long LOCAL_CHARACTER_ID = 0L;

    /**
     * ⚠️ Tạm thời: mọi nhân vật dùng chung một model. {@link CharacterSpawnedEvent} <b>không có</b>
     * trường nào mô tả ngoại hình (hệ phái / bậc trang bị), nên client không có cách nào biết phải
     * nạp model nào. Thêm trường đó là <b>đổi protocol</b> — phải dừng và hỏi chủ dự án
     * (/CLAUDE.md §4), không tự làm ở đây.
     */
    private static final String PLACEHOLDER_MODEL_KEY = "char_thuy-b1";

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport;
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final CharacterModelRenderer models = new CharacterModelRenderer();

    private float lastSentDirX;
    private float lastSentDirY;

    public WorldScreen(EcsWorld world, InputMapper inputMapper, GameConnection connection) {
        this.world = world;
        this.inputMapper = inputMapper;
        this.connection = connection;
        this.viewport =
                new FitViewport(WorldConstants.VIEWPORT_HEIGHT_UNITS, WorldConstants.VIEWPORT_HEIGHT_UNITS, camera);

        world.addSystem(new MovementSystem());

        // Spawn (0,0) khớp mặc định server-world.WorldConstants.SPAWN_X/Y — chưa có persistence
        // vị trí (server-world follow-up), MoveResponse đầu tiên sẽ tự sửa nếu lệch.
        this.localPlayer = world.createEntity();
        localPlayer.add(new PositionComponent());
        localPlayer.add(new VelocityComponent());
        models.addCharacter(LOCAL_CHARACTER_ID, PLACEHOLDER_MODEL_KEY);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        models.resize(camera.viewportWidth, camera.viewportHeight);
    }

    @Override
    public void fixedUpdate(float fixedDeltaTime) {
        processInboundPackets();
        updateLocalMovementIntent();
    }

    private void processInboundPackets() {
        PacketCodec.Frame frame;
        while ((frame = connection.pollInbound()) != null) {
            dispatch(frame);
        }
    }

    private void dispatch(PacketCodec.Frame frame) {
        try {
            int opcode = frame.header().opcode();
            byte[] payload = frame.payload();
            if (opcode == Opcode.OPCODE_MOVE_RESPONSE_VALUE) {
                onMoveResponse(MoveResponse.parseFrom(payload));
            } else if (opcode == Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE) {
                onCharacterSpawned(CharacterSpawnedEvent.parseFrom(payload));
            } else if (opcode == Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE) {
                onCharacterMoved(CharacterMovedEvent.parseFrom(payload));
            } else if (opcode == Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE) {
                onCharacterDespawned(CharacterDespawnedEvent.parseFrom(payload));
            }
            // Opcode khác (VD PongResponse của heartbeat) — không thuộc gameplay world, bỏ qua.
        } catch (InvalidProtocolBufferException e) {
            LOG.warn(
                    "Payload protobuf không hợp lệ từ server: opcode={}",
                    frame.header().opcode(),
                    e);
        }
    }

    private void onMoveResponse(MoveResponse response) {
        PositionComponent pos = POSITION.get(localPlayer);
        float dx = response.getX() - pos.x;
        float dy = response.getY() - pos.y;
        if (Math.sqrt((dx * dx) + (dy * dy)) > WorldConstants.RECONCILE_SNAP_THRESHOLD) {
            // Lệch lớn (mất gói MoveCommand trước đó, hoặc server điều chỉnh) — sửa NGAY theo
            // server, không tranh cãi (Điều 15, CLIENT.md §6).
            pos.x = response.getX();
            pos.y = response.getY();
            pos.prevX = pos.x;
            pos.prevY = pos.y;
        }
    }

    private void onCharacterSpawned(CharacterSpawnedEvent event) {
        if (remoteCharacters.containsKey(event.getCharacterId())) {
            return; // đã có — bỏ qua an toàn thay vì tạo entity trùng.
        }
        KEntity entity = world.createEntity();
        PositionComponent pos = new PositionComponent();
        pos.x = event.getX();
        pos.y = event.getY();
        pos.prevX = pos.x;
        pos.prevY = pos.y;
        entity.add(pos);
        remoteCharacters.put(event.getCharacterId(), entity);
        models.addCharacter(event.getCharacterId(), PLACEHOLDER_MODEL_KEY);
    }

    private void onCharacterMoved(CharacterMovedEvent event) {
        KEntity entity = remoteCharacters.get(event.getCharacterId());
        if (entity == null) {
            return; // Moved tới trước Spawned (hiếm qua TCP, nhưng an toàn bỏ qua thay vì crash).
        }
        PositionComponent pos = POSITION.get(entity);
        pos.x = event.getX();
        pos.y = event.getY();
        pos.prevX = pos.x;
        pos.prevY = pos.y;
    }

    private void onCharacterDespawned(CharacterDespawnedEvent event) {
        KEntity entity = remoteCharacters.remove(event.getCharacterId());
        if (entity != null) {
            world.removeEntity(entity);
        }
        models.removeCharacter(event.getCharacterId());
    }

    private void updateLocalMovementIntent() {
        float dirX = 0f;
        float dirY = 0f;
        if (inputMapper.isPressed(GameAction.MOVE_LEFT)) {
            dirX -= 1f;
        }
        if (inputMapper.isPressed(GameAction.MOVE_RIGHT)) {
            dirX += 1f;
        }
        if (inputMapper.isPressed(GameAction.MOVE_DOWN)) {
            dirY -= 1f;
        }
        if (inputMapper.isPressed(GameAction.MOVE_UP)) {
            dirY += 1f;
        }
        // Chuẩn hoá CỤC BỘ giống hệt server (WorldFacade.clampDirection) — dự đoán khớp server
        // ngay cả khi đi chéo, tránh dự đoán "nhanh hơn" rồi bị snap lại liên tục.
        float length = (float) Math.sqrt((dirX * dirX) + (dirY * dirY));
        float normalizedDirX = length > 1f ? dirX / length : dirX;
        float normalizedDirY = length > 1f ? dirY / length : dirY;

        VelocityComponent velocity = VELOCITY.get(localPlayer);
        velocity.x = normalizedDirX * WorldConstants.MOVE_SPEED_MPS;
        velocity.y = normalizedDirY * WorldConstants.MOVE_SPEED_MPS;

        if (normalizedDirX != lastSentDirX || normalizedDirY != lastSentDirY) {
            lastSentDirX = normalizedDirX;
            lastSentDirY = normalizedDirY;
            sendMoveCommand(normalizedDirX, normalizedDirY);
        }
    }

    private void sendMoveCommand(float dirX, float dirY) {
        try {
            MoveCommand command =
                    MoveCommand.newBuilder().setDirX(dirX).setDirY(dirY).build();
            connection.send(Opcode.OPCODE_MOVE_COMMAND_VALUE, connection.nextSequenceId(), command.toByteArray());
        } catch (IOException e) {
            // Mất kết nối tạm thời — bình thường (GameConnection tự reconnect), hướng mới nhất sẽ
            // được gửi lại lần input đổi tiếp theo, không cần retry thủ công ở đây.
            LOG.debug("Gửi MoveCommand thất bại (mất kết nối tạm thời)", e);
        }
    }

    @Override
    public void render(float alpha) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        // Depth buffer bắt buộc từ ADR-031: nhân vật là hình học 3D thật, thiếu depth thì thứ tự vẽ
        // quyết định cái nào che cái nào — đúng thứ 2.5D phải bỏ.
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (models.isEnabled()) {
            renderModels(alpha);
        } else {
            renderPlaceholderBoxes(alpha);
        }
    }

    /** Nhánh 3D (ADR-031) — model glTF qua camera orthographic khoá góc. */
    private void renderModels(float alpha) {
        float localX = interpolatedX(localPlayer, alpha);
        float localY = interpolatedY(localPlayer, alpha);
        models.setCharacterPosition(LOCAL_CHARACTER_ID, localX, localY);
        for (Map.Entry<Long, KEntity> entry : remoteCharacters.entrySet()) {
            models.setCharacterPosition(
                    entry.getKey(), interpolatedX(entry.getValue(), alpha), interpolatedY(entry.getValue(), alpha));
        }
        models.follow(localX, localY);
        models.render(Gdx.graphics.getDeltaTime());
    }

    /**
     * Hình thay thế thời trước ADR-031. Còn giữ vì model là tài sản sinh ra, không commit
     * ([ART-PIPELINE §7](docs/14-art/ART-PIPELINE.md)) — bản checkout chưa chạy pipeline art vẫn
     * phải chơi được, không phải màn hình đen.
     */
    private void renderPlaceholderBoxes(float alpha) {
        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(Color.CORAL);
        drawInterpolated(localPlayer, alpha);

        shapeRenderer.setColor(Color.SKY);
        for (KEntity entity : remoteCharacters.values()) {
            drawInterpolated(entity, alpha);
        }

        shapeRenderer.end();
    }

    private static float interpolatedX(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevX + ((pos.x - pos.prevX) * alpha);
    }

    private static float interpolatedY(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevY + ((pos.y - pos.prevY) * alpha);
    }

    private void drawInterpolated(KEntity entity, float alpha) {
        shapeRenderer.rect(
                interpolatedX(entity, alpha) - (WorldConstants.BOX_SIZE / 2f),
                interpolatedY(entity, alpha) - (WorldConstants.BOX_SIZE / 2f),
                WorldConstants.BOX_SIZE,
                WorldConstants.BOX_SIZE);
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        models.dispose();
    }
}
