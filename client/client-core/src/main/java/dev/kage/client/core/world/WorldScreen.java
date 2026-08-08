package dev.kage.client.core.world;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.input.GameAction;
import dev.kage.client.core.input.InputMapper;
import dev.kage.client.core.net.GameConnection;
import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.engine.core.GameScreen;
import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.UseSkillCommand;
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
    private final RemoteMotionTracker remoteMotionTracker = new RemoteMotionTracker();

    /**
     * Id dùng cho nhân vật của chính người chơi trong {@link CharacterModelRenderer}. Server đánh id
     * cho nhân vật khác; nhân vật của mình chưa bao giờ cần id nên không có. 0 an toàn vì id thật
     * sinh từ sequence của DB, luôn dương.
     */
    private static final long LOCAL_CHARACTER_ID = 0L;

    /**
     * Id THẬT của chính người chơi (từ DB, luôn dương) — dùng để nhận ra
     * {@link AttackResultEvent}/{@link SkillResultEvent} nào nhắm vào MÌNH (khớp
     * {@code attacker_entity_id}/{@code target_entity_id}) và quy đổi sang
     * {@link #LOCAL_CHARACTER_ID} khi gọi {@link CharacterModelRenderer}.
     */
    private final long localCharacterId;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport;
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final CharacterModelRenderer models = new CharacterModelRenderer();

    private final CombatHudController combatHudController;
    private final CombatAnimationTrigger combatAnimationTrigger;
    private final CombatHud combatHud = new CombatHud();

    private float lastSentDirX;
    private float lastSentDirY;

    /**
     * @param context {@link EcsWorld} + {@link InputMapper} gộp lại (CODING-STANDARDS
     *     ParameterNumber max=4) — xem {@link PlayContext}.
     * @param character {@code CharacterSummary} của CHÍNH người chơi, nhận từ
     *     {@code CharacterSelectScreen} lúc chọn/tạo xong. Mang theo {@code faction} vì server
     *     KHÔNG gửi {@link CharacterSpawnedEvent} cho chính mình (đã có {@code MoveResponse}), nên
     *     đây là nguồn duy nhất để biết phải vẽ mình bằng model nào; mang theo {@code id} để nhận ra
     *     {@link AttackResultEvent}/{@link SkillResultEvent} nào đang nhắm vào MÌNH thay vì quái
     *     ({@link #LOCAL_CHARACTER_ID} chỉ là sentinel nội bộ cho render, không phải id thật).
     */
    public WorldScreen(PlayContext context, GameConnection connection, CharacterSummary character) {
        this.world = context.world();
        this.inputMapper = context.inputMapper();
        this.connection = connection;
        this.localCharacterId = character.getId();
        this.combatHudController = new CombatHudController(character.getId(), character.getFaction());
        this.combatAnimationTrigger =
                new CombatAnimationTrigger(models, localCharacterId, LOCAL_CHARACTER_ID, remoteCharacters::containsKey);
        this.viewport =
                new FitViewport(WorldConstants.VIEWPORT_HEIGHT_UNITS, WorldConstants.VIEWPORT_HEIGHT_UNITS, camera);

        world.addSystem(new MovementSystem());

        // Spawn (0,0) khớp mặc định server-world.WorldConstants.SPAWN_X/Y — chưa có persistence
        // vị trí (server-world follow-up), MoveResponse đầu tiên sẽ tự sửa nếu lệch.
        this.localPlayer = world.createEntity();
        localPlayer.add(new PositionComponent());
        localPlayer.add(new VelocityComponent());
        // Bậc trang phục của chính mình cũng chưa suy được từ đâu (xem CharacterAppearance phía
        // server) — dùng đúng giá trị mặc định server đang phát cho người khác, để mình và người
        // khác nhìn thấy mình giống nhau.
        models.addCharacter(
                LOCAL_CHARACTER_ID, CharacterModelKey.of(character.getFaction(), CostumeTier.COSTUME_TIER_UNSPECIFIED));

        combatHud.setSkillLabel(combatHudController.skillNameKey());
        combatHud.setOnSkillClicked(this::onSkillButtonClicked);
    }

    @Override
    public void show() {
        // Input di chuyển vẫn poll qua InputMapper (không đổi) — HUD combat là actor Scene2D đầu
        // tiên của client, chưa ai khác đăng ký InputProcessor nên không tranh chấp.
        Gdx.input.setInputProcessor(combatHud.stage());
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        models.resize(camera.viewportWidth, camera.viewportHeight);
        combatHud.resize(width, height);
    }

    @Override
    public void fixedUpdate(float fixedDeltaTime) {
        processInboundPackets();
        updateLocalMovementIntent();
        combatHudController.tick(fixedDeltaTime);
        remoteMotionTracker.tick(fixedDeltaTime);
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
            if (!dispatchMovement(opcode, payload)) {
                dispatchCombat(opcode, payload);
            }
            // Opcode khác (VD PongResponse của heartbeat) — không thuộc gameplay world, bỏ qua.
        } catch (InvalidProtocolBufferException e) {
            LOG.warn(
                    "Payload protobuf không hợp lệ từ server: opcode={}",
                    frame.header().opcode(),
                    e);
        }
    }

    // Tách dispatch() thành 2 nhóm (di chuyển/spawn nhân vật vs quái/combat) THUẦN để hạ cyclomatic
    // complexity dưới ngưỡng PMD (methodReportLevel=10) — 1 chuỗi if-else 9 nhánh vượt ngưỡng, không
    // phải đổi hành vi.
    /** @return {@code true} nếu opcode thuộc nhóm di chuyển/spawn nhân vật, đã xử lý xong. */
    private boolean dispatchMovement(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_MOVE_RESPONSE_VALUE) {
            onMoveResponse(MoveResponse.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_SPAWNED_EVENT_VALUE) {
            onCharacterSpawned(CharacterSpawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE) {
            onCharacterMoved(CharacterMovedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE) {
            onCharacterDespawned(CharacterDespawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE) {
            onCharacterRespawned(CharacterRespawnedEvent.parseFrom(payload));
        } else {
            return false;
        }
        return true;
    }

    private void dispatchCombat(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
            onMonsterSpawned(MonsterSpawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE) {
            combatHudController.onMonsterDespawned(MonsterDespawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE) {
            combatHudController.onMonsterMoved(MonsterMovedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            onAttackResult(AttackResultEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
            onSkillResult(SkillResultEvent.parseFrom(payload));
        }
    }

    /**
     * Kết quả 1 đòn đánh thường THẬT — gửi cho MỌI nhân vật thấy mục tiêu trong AoI (combat.proto),
     * nên {@code attacker_entity_id}/{@code target_entity_id} có thể là CHÍNH mình, người khác, hoặc
     * quái. HP/hồi chiêu (HUD) và animation (model 3D) là 2 mối quan tâm tách biệt — xem
     * {@link CombatAnimationTrigger}.
     */
    private void onAttackResult(AttackResultEvent event) {
        combatHudController.onAttackResult(event);
        combatAnimationTrigger.onAttackResult(event);
    }

    /** Kết quả 1 lần dùng skill THẬT — cùng hình dạng {@link AttackResultEvent}. */
    private void onSkillResult(SkillResultEvent event) {
        combatHudController.onSkillResult(event);
        combatAnimationTrigger.onSkillResult(event);
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
        models.addCharacter(event.getCharacterId(), CharacterModelKey.of(event.getFaction(), event.getCostumeTier()));
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
        remoteMotionTracker.onPositionUpdate(event.getCharacterId(), event.getX(), event.getY());
        if (models.isDead(event.getCharacterId())) {
            // Giao thức KHÔNG phát CharacterRespawnedEvent cho ai khác ngoài chính người hồi sinh
            // (javadoc onCharacterRespawned) — vị trí đổi sau khi chết chỉ có thể là hồi sinh, nên
            // coi gói di chuyển đầu tiên sau "die" là tín hiệu hồi sinh cho nhân vật KHÁC.
            models.resetAfterRespawn(event.getCharacterId());
        }
    }

    private void onCharacterDespawned(CharacterDespawnedEvent event) {
        KEntity entity = remoteCharacters.remove(event.getCharacterId());
        if (entity != null) {
            world.removeEntity(entity);
        }
        models.removeCharacter(event.getCharacterId());
        remoteMotionTracker.remove(event.getCharacterId());
    }

    /**
     * CHÍNH nhân vật vừa hồi sinh (HP về 0 trước đó) — không có {@code character_id} trong packet
     * (cùng quy ước "ngầm định chính mình" như {@link #onMoveResponse}). Snap vị trí NGAY, không
     * nội suy — hồi sinh vốn là dịch chuyển tức thời, khác di chuyển thường.
     */
    private void onCharacterRespawned(CharacterRespawnedEvent event) {
        PositionComponent pos = POSITION.get(localPlayer);
        pos.x = event.getX();
        pos.y = event.getY();
        pos.prevX = pos.x;
        pos.prevY = pos.y;
        combatHudController.onRespawn(event.getCurrentHp(), event.getCurrentChi());
        models.resetAfterRespawn(LOCAL_CHARACTER_ID);
    }

    private void onMonsterSpawned(MonsterSpawnedEvent event) {
        long autoTargetId = combatHudController.onMonsterSpawned(event);
        if (autoTargetId != 0L) {
            sendSetTarget(autoTargetId);
        }
    }

    private void sendSetTarget(long targetEntityId) {
        try {
            SetTargetCommand command = SetTargetCommand.newBuilder()
                    .setTargetEntityId(targetEntityId)
                    .build();
            connection.send(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, connection.nextSequenceId(), command.toByteArray());
        } catch (IOException e) {
            LOG.debug("Gửi SetTargetCommand thất bại (mất kết nối tạm thời)", e);
        }
    }

    private void onSkillButtonClicked() {
        if (!combatHudController.useSkill()) {
            return; // Chưa đủ điều kiện cục bộ (hồi chiêu/Khí/mục tiêu) — không gửi gì thừa.
        }
        // Dự đoán CỤC BỘ animation (không phải kết quả gameplay) của chính mình — CLIENT.md §6 cho
        // phép dự đoán "di chuyển/animation của chính mình". SkillResultEvent xác nhận tới sau sẽ
        // gọi lại models.playSkillCast() lần nữa nhưng vô hại (playOneShotState bỏ qua nếu clip
        // giống hệt đang chạy) — không có xung đột gameplay vì animation không quyết định gì.
        models.playSkillCast(LOCAL_CHARACTER_ID);
        try {
            UseSkillCommand command = UseSkillCommand.newBuilder()
                    .setSkillCode(combatHudController.skillCode())
                    .setTargetEntityId(combatHudController.currentTargetId())
                    .build();
            connection.send(Opcode.OPCODE_USE_SKILL_COMMAND_VALUE, connection.nextSequenceId(), command.toByteArray());
        } catch (IOException e) {
            LOG.debug("Gửi UseSkillCommand thất bại (mất kết nối tạm thời)", e);
        }
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
            drawMonsters(models.combined());
        } else {
            renderPlaceholderBoxes(alpha);
            drawMonsters(camera.combined);
        }

        syncCombatHud();
        combatHud.render(Gdx.graphics.getDeltaTime());
    }

    /**
     * Quái CHƯA có model 3D (art pipeline chưa nhắm tới quái) — vẽ hộp phẳng bằng đúng ma trận
     * chiếu của nhánh render nhân vật đang hoạt động, để không lệch không gian chiếu (ADR-031 #7 —
     * bài học "đo bằng log thật" cho lệch khung hình áp dụng y hệt ở đây, chỉ khác nguyên nhân).
     */
    private void drawMonsters(Matrix4 projection) {
        Map<Long, CombatHudController.MonsterState> currentMonsters = combatHudController.monsters();
        if (currentMonsters.isEmpty()) {
            return;
        }
        shapeRenderer.setProjectionMatrix(projection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.SCARLET);
        for (CombatHudController.MonsterState monster : currentMonsters.values()) {
            shapeRenderer.rect(
                    monster.x() - (WorldConstants.BOX_SIZE / 2f),
                    monster.y() - (WorldConstants.BOX_SIZE / 2f),
                    WorldConstants.BOX_SIZE,
                    WorldConstants.BOX_SIZE);
        }
        shapeRenderer.end();
    }

    private void syncCombatHud() {
        combatHud.setPlayerHp(combatHudController.playerHp(), combatHudController.playerMaxHp());
        CombatHudController.MonsterState target = combatHudController.targetState();
        if (target != null) {
            combatHud.setTarget(target.code(), target.hp(), target.maxHp());
        } else {
            combatHud.clearTarget();
        }
        combatHud.setSkillReady(combatHudController.canUseSkill());
    }

    /** Nhánh 3D (ADR-031) — model glTF qua camera orthographic khoá góc. */
    private void renderModels(float alpha) {
        float localX = interpolatedX(localPlayer, alpha);
        float localY = interpolatedY(localPlayer, alpha);
        models.setCharacterPosition(LOCAL_CHARACTER_ID, localX, localY);
        models.setCharacterMoving(LOCAL_CHARACTER_ID, isLocalPlayerMoving());
        for (Map.Entry<Long, KEntity> entry : remoteCharacters.entrySet()) {
            long characterId = entry.getKey();
            models.setCharacterPosition(
                    characterId, interpolatedX(entry.getValue(), alpha), interpolatedY(entry.getValue(), alpha));
            // Nhân vật khác không có VelocityComponent (chỉ nội suy vị trí, CLIENT.md §7) — suy
            // "đang di chuyển" từ độ lệch vị trí giữa các CharacterMovedEvent thay vào đó.
            models.setCharacterMoving(characterId, remoteMotionTracker.isMoving(characterId));
        }
        models.follow(localX, localY);
        models.render(Gdx.graphics.getDeltaTime());
    }

    /** Vận tốc dự đoán cục bộ (CLIENT.md §6) của chính mình — đủ để quyết định idle vs run. */
    private boolean isLocalPlayerMoving() {
        VelocityComponent velocity = VELOCITY.get(localPlayer);
        return CharacterAnimationClips.isMoving(velocity.x, velocity.y);
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
        combatHud.dispose();
    }
}
