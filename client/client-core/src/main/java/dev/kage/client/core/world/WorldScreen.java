package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
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
import dev.kage.client.core.ui.hud.ChatPanel;
import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.client.core.ui.hud.DialoguePanel;
import dev.kage.engine.core.GameScreen;
import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;
import dev.kage.engine.ecs.system.VerticalMovementSystem;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.NpcDespawnedEvent;
import dev.kage.protocol.NpcSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SkillResultEvent;
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
    private final WorldCommandSender sender;
    private final EcsWorld world;
    private final KEntity localPlayer;
    /** Người chơi KHÁC — kho + model + idle/run, xem {@link RemoteCharacterPresenter}. */
    private final RemoteCharacterPresenter remoteCharacters;

    /**
     * Id dùng cho nhân vật của chính người chơi trong {@link WorldModelRenderer}. Server đánh id
     * cho nhân vật khác; nhân vật của mình chưa bao giờ cần id nên không có. 0 an toàn vì id thật
     * sinh từ sequence của DB, luôn dương.
     */
    private static final long LOCAL_CHARACTER_ID = 0L;

    /**
     * Id THẬT của chính người chơi (từ DB, luôn dương) — dùng để nhận ra
     * {@link AttackResultEvent}/{@link SkillResultEvent} nào nhắm vào MÌNH (khớp
     * {@code attacker_entity_id}/{@code target_entity_id}) và quy đổi sang
     * {@link #LOCAL_CHARACTER_ID} khi gọi {@link WorldModelRenderer}.
     */
    private final long localCharacterId;

    /**
     * Cấp nhân vật — khởi tạo từ lúc chọn nhân vật, sau đó server cập nhật qua
     * {@link ExperienceChangedEvent} (từ 2026-08-09 đã lên cấp được thật giữa trận).
     */
    private int characterLevel;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport;
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    // MEDIUM tường minh — bản cài đóng gói đúng 1 tier mặc định theo ADR-028, không phải để trống
    // dựa vào default ngầm (không có default: WorldModelRenderer bắt buộc tham số tier).
    private final WorldModelRenderer models = new WorldModelRenderer(GraphicsTier.MEDIUM);

    /**
     * Chiếu điểm world sang màn hình bằng camera của nhánh render ĐANG hoạt động. Hai method
     * reference dựng SẴN 1 lần thay vì viết {@code models::project} tại chỗ mỗi khung — mỗi lần
     * đánh giá một method reference bắt biến (capture {@code models}) là một lần cấp phát, thứ bị
     * cấm trong game loop (Điều 31).
     */
    private final WorldToScreen modelProjector = models::project;

    private final WorldToScreen fallbackProjector = camera::project;

    private final WorldEntityPicker entityPicker = new WorldEntityPicker();

    /** Toàn bộ phần hiển thị quái (model 3D, hộp thay thế, dấu mục tiêu) — xem {@link MonsterPresenter}. */
    private final MonsterPresenter monsterPresenter = new MonsterPresenter(models);

    /** NPC — server phát {@code NpcSpawnedEvent} từ Phase 6, client mới đọc từ 2026-08-09. */
    private final NpcPresenter npcPresenter = new NpcPresenter(models);

    private final CombatHudController combatHudController;
    private final CombatAnimationTrigger combatAnimationTrigger;
    private final CombatHud combatHud = new CombatHud();

    /** Số damage + hiệu ứng skill neo tại vị trí mục tiêu — xem {@link CombatFeedbackPresenter}. */
    private final CombatFeedbackPresenter combatFeedback;

    /** Khung hội thoại NPC — mượn skin của {@link #combatHud}, xem {@link DialoguePanel}. */
    private final DialoguePanel dialoguePanel = new DialoguePanel(combatHud.skin());

    /** Luồng hội thoại (trạng thái + mạng + panel) — xem {@link DialogueCoordinator}. */
    private final DialogueCoordinator dialogue;

    /** Khung chat — mượn skin của {@link #combatHud} như {@link #dialoguePanel}. */
    private final ChatPanel chatPanel = new ChatPanel(combatHud.skin(), ChatController.MAX_LINES);

    /** Luồng chat (nhật ký + kênh + mạng) — xem {@link ChatCoordinator}. */
    private final ChatCoordinator chat;

    /** Vòng đời nhiệm vụ: nhận → hoàn thành → lĩnh thưởng — xem {@link QuestCoordinator}. */
    private final QuestCoordinator quests;

    /** Xem {@link #buildInputProcessor()} về thứ tự nuốt input. */
    private final InputMultiplexer inputProcessor;

    private float lastSentDirX;

    /**
     * Toàn bộ trạng thái thời gian của nút nhảy (mép nhấn + đồng hồ giữ nút) — {@code GameAction}
     * chỉ nói "đang giữ hay không", biến nó thành cú nhảy đơn/đôi là việc của lớp này.
     */
    private final JumpInputController jumpInput = new JumpInputController();

    /** Edge-detection cho phím Enter — giữ phím không spam skill (nhảy có bộ đếm riêng ở {@link #jumpInput}). */
    private boolean skillKeyWasPressed;

    /** Phím Z + nút Đánh trên HUD gộp về một nguồn — xem {@link AttackInputController}. */
    private final AttackInputController attackInput = new AttackInputController();

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
        this.sender = new WorldCommandSender(connection);
        this.localCharacterId = character.getId();
        this.characterLevel = character.getLevel();
        this.combatHudController = new CombatHudController(character.getId(), character.getFaction());
        this.combatAnimationTrigger = new CombatAnimationTrigger(models, localCharacterId, LOCAL_CHARACTER_ID);
        this.remoteCharacters = new RemoteCharacterPresenter(context.world(), models);
        this.viewport =
                new FitViewport(WorldConstants.VIEWPORT_HEIGHT_UNITS, WorldConstants.VIEWPORT_HEIGHT_UNITS, camera);

        world.addSystem(new MovementSystem());
        // ADR-033 — trục dọc chạy CÙNG công thức server (WorldConstants.GRAVITY_MPS2 phải khớp)
        // để dự đoán cục bộ (CLIENT.md §6) khớp server, cùng lý do MovementSystem đã có ở trên.
        VerticalMovementSystem verticalMovementSystem = new VerticalMovementSystem(WorldConstants.GRAVITY_MPS2);
        // Cùng bộ bệ với server (WorldFacade.DEMO_PLATFORMS) — BẮT BUỘC, xem javadoc
        // DemoPlatforms.physics() về hậu quả nếu thiếu (nhân vật rơi xuyên bệ ở phía client).
        verticalMovementSystem.setPlatforms(DemoPlatforms.physics());
        world.addSystem(verticalMovementSystem);

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

        // SAU khi localPlayer tồn tại: presenter giữ tham chiếu thẳng tới PositionComponent của
        // chính người chơi (xem javadoc constructor của nó).
        this.combatFeedback = new CombatFeedbackPresenter(
                localCharacterId, POSITION.get(localPlayer), combatHudController, combatHud.font());

        // SAU combatFeedback: coordinator mượn nó để hiện thông báo khi hội thoại kết thúc bằng
        // nhánh nhận quest/mở cửa hàng.
        this.quests = new QuestCoordinator(combatHud, sender, combatFeedback);
        this.dialogue = new DialogueCoordinator(dialoguePanel, sender, combatFeedback, quests);
        this.chat = new ChatCoordinator(chatPanel, sender);

        combatHud.setSkillLabel(combatHudController.skillNameKey());
        combatHud.setOnSkillClicked(this::onSkillButtonClicked);
        combatHud.setOnAttackClicked(attackInput::onButtonClicked);
        this.inputProcessor = buildInputProcessor();
    }

    /**
     * Thứ tự nuốt input: hội thoại → chat → HUD → thế giới.
     *
     * <p>Hội thoại đứng đầu vì đang nói chuyện mà bấm trúng nút Đánh phía sau, hoặc click xuyên qua
     * khung thoại để chọn mục tiêu, đều là hỏng. Chat đứng trên HUD vì click vào nhật ký chat không
     * được rơi xuống tầng thế giới thành lệnh chọn mục tiêu. HUD trên thế giới vì {@code Stage} chỉ
     * trả {@code true} ở {@code touchDown} khi có actor thật nhận cú click, nên bấm vào vùng trống
     * của HUD vẫn rơi xuống {@link WorldTargetPicker} đúng như mong đợi.
     */
    private InputMultiplexer buildInputProcessor() {
        return new InputMultiplexer(
                dialoguePanel.stage(), chatPanel.stage(), combatHud.stage(), new WorldTargetPicker());
    }

    @Override
    public void show() {
        // Input di chuyển/nhảy/skill vẫn poll qua InputMapper (không đổi) — chỉ CLICK chọn mục tiêu
        // mới cần InputProcessor, vì đó là sự kiện tại một điểm cụ thể chứ không phải trạng thái
        // giữ/nhả để poll được.
        Gdx.input.setInputProcessor(inputProcessor);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        // Khung hội thoại không được sống sót qua một lần rời màn hình — quay lại sẽ thấy nó treo ở
        // node cũ trong khi server đã quên sạch cuộc nói chuyện đó.
        dialogue.reset();
        chat.reset();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        models.resize(camera.viewportWidth, camera.viewportHeight);
        combatHud.resize(width, height);
        dialoguePanel.resize(width, height);
        chatPanel.resize(width, height);
        combatFeedback.resize(width, height);
    }

    @Override
    public void fixedUpdate(float fixedDeltaTime) {
        processInboundPackets();
        updateLocalMovementIntent();
        updateJumpIntent(fixedDeltaTime);
        updateAttackIntent(fixedDeltaTime);
        updateSkillIntent();
        combatHudController.tick(fixedDeltaTime);
        remoteCharacters.tick(fixedDeltaTime);
        monsterPresenter.tick(fixedDeltaTime);
    }

    private void processInboundPackets() {
        PacketCodec.Frame frame;
        while ((frame = sender.pollInbound()) != null) {
            dispatch(frame);
        }
    }

    private void dispatch(PacketCodec.Frame frame) {
        try {
            int opcode = frame.header().opcode();
            byte[] payload = frame.payload();
            // quests TRƯỚC dialogue, và KHÔNG dùng else-if giữa hai cái: AcceptQuestResponse có
            // nghĩa với CẢ HAI (cập nhật nhiệm vụ vs đóng khung thoại). Dừng ở cái đầu tiên trả true
            // sẽ làm mất đúng một trong hai việc.
            boolean handled = quests.dispatch(opcode, payload);
            handled |= dialogue.dispatch(opcode, payload);
            handled |= chat.dispatch(opcode, payload);
            if (!handled && !dispatchMovement(opcode, payload)) {
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
            remoteCharacters.onSpawned(CharacterSpawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_MOVED_EVENT_VALUE) {
            remoteCharacters.onMoved(CharacterMovedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT_VALUE) {
            remoteCharacters.onDespawned(CharacterDespawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE) {
            onCharacterRespawned(CharacterRespawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE) {
            onExperienceChanged(ExperienceChangedEvent.parseFrom(payload));
        } else {
            return false;
        }
        return true;
    }

    private void dispatchCombat(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
            onMonsterSpawned(MonsterSpawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE) {
            MonsterDespawnedEvent despawned = MonsterDespawnedEvent.parseFrom(payload);
            combatHudController.onMonsterDespawned(despawned);
            monsterPresenter.onDespawned(despawned.getMonsterId());
        } else if (opcode == Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE) {
            MonsterMovedEvent moved = MonsterMovedEvent.parseFrom(payload);
            combatHudController.onMonsterMoved(moved);
            monsterPresenter.onMoved(moved);
        } else if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            onAttackResult(AttackResultEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
            onSkillResult(SkillResultEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_NPC_SPAWNED_EVENT_VALUE) {
            npcPresenter.onSpawned(NpcSpawnedEvent.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_NPC_DESPAWNED_EVENT_VALUE) {
            npcPresenter.onDespawned(NpcDespawnedEvent.parseFrom(payload).getNpcId());
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
        combatFeedback.onAttackResult(event);
    }

    /** Kết quả 1 lần dùng skill THẬT — cùng hình dạng {@link AttackResultEvent}. */
    private void onSkillResult(SkillResultEvent event) {
        combatHudController.onSkillResult(event);
        combatAnimationTrigger.onSkillResult(event);
        combatFeedback.onSkillResult(event);
    }

    private void onMoveResponse(MoveResponse response) {
        PositionComponent pos = POSITION.get(localPlayer);
        float dx = response.getX() - pos.x;
        float dy = response.getY() - pos.y;
        float dz = response.getZ() - pos.z;
        if (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)) > WorldConstants.RECONCILE_SNAP_THRESHOLD) {
            // Lệch lớn (mất gói MoveCommand/JumpCommand trước đó, hoặc server điều chỉnh) — sửa
            // NGAY theo server, không tranh cãi (Điều 15, CLIENT.md §6).
            pos.x = response.getX();
            pos.y = response.getY();
            pos.prevX = pos.x;
            pos.prevY = pos.y;
            pos.z = response.getZ();
            pos.prevZ = pos.z;
            pos.grounded = response.getGrounded();
            if (pos.grounded) {
                // Server nói đang đứng đất ⇒ hạn mức nhảy đôi bên server CHẮC CHẮN đã reset
                // (VerticalMovementSystem xoá lúc đáp đất) — theo server luôn, tránh trường hợp
                // client tưởng còn nợ 1 lần nhảy giữa không trung dù đã đứng trên đất.
                pos.airJumpsUsed = 0;
            }
            // vz KHÔNG snap theo server — MoveResponse không mang vz (chỉ x/y/z/grounded, xem
            // movement.proto). Dự đoán cục bộ tiếp tục dùng vz hiện có, tự hội tụ đúng quỹ đạo ở
            // các tick sau vì VerticalMovementSystem chạy CÙNG công thức cả 2 phía (ADR-033) —
            // follow-up nếu số liệu thật cho thấy cần đồng bộ cả vz.
        }
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
        // ADR-033 — hồi sinh LUÔN ở mặt đất (WorldFacade.respawnCharacter), reset cả trục dọc: có
        // thể chết giữa lúc đang lơ lửng (z>0, grounded=false), không reset sẽ hồi sinh "bay".
        pos.z = 0f;
        pos.prevZ = 0f;
        pos.vz = 0f;
        pos.grounded = true;
        pos.airJumpsUsed = 0;
        combatHudController.onRespawn(event.getCurrentHp(), event.getCurrentChi());
        models.resetAfterRespawn(LOCAL_CHARACTER_ID);
    }

    /**
     * Vạch kinh nghiệm/cấp của CHÍNH mình vừa đổi (2026-08-09) — server gửi cả lúc vừa vào world
     * (để vạch có số ngay) lẫn mỗi lần nhận EXP. Client KHÔNG tự tính đường cong: mọi con số ở đây
     * là do server đưa (Điều 15).
     */
    private void onExperienceChanged(ExperienceChangedEvent event) {
        characterLevel = event.getLevel();
        combatHud.setExperience(event.getExpIntoLevel(), event.getExpForNextLevel());
        if (event.getLeveledUp()) {
            combatFeedback.showLevelUp();
        }
    }

    private void onMonsterSpawned(MonsterSpawnedEvent event) {
        long autoTargetId = combatHudController.onMonsterSpawned(event);
        monsterPresenter.onSpawned(event);
        if (autoTargetId != 0L) {
            sender.setTarget(autoTargetId);
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
        sender.useSkill(combatHudController.skillCode(), combatHudController.currentTargetId());
    }

    /**
     * Phím gameplay, đã trừ đi lúc người chơi đang gõ chat.
     *
     * <p>MỌI chỗ đọc input đi qua đây thay vì gọi thẳng {@link InputMapper} — và đó là điểm mấu
     * chốt, không phải chuyện gọn gàng. Phím gameplay được <b>poll</b> qua
     * {@code Gdx.input.isKeyPressed} chứ không nhận qua {@code InputProcessor}, tức là chúng đi vòng
     * qua lưng {@code InputMultiplexer}: {@code Stage} của khung chat có nuốt phím hay không cũng
     * không ngăn được chữ "a" vừa gõ đồng thời làm nhân vật chạy sang trái. Chặn tập trung ở một
     * hàm thì không thể sót một loại input nào; rải {@code if} ở từng chỗ đọc thì sót là chuyện sớm
     * muộn.
     *
     * <p>Trả {@code false} đều tay cho MỌI action cũng chính là thứ làm nhân vật <b>dừng lại</b>
     * trên server: {@link #updateLocalMovementIntent} thấy hướng đổi thành 0 và tự gửi
     * {@code MoveCommand} một lần — không cần đường dừng riêng.
     */
    private boolean isPressed(GameAction action) {
        return !chat.isComposing() && inputMapper.isPressed(action);
    }

    /**
     * ADR-033 — chỉ còn trái/phải (bỏ MOVE_UP/MOVE_DOWN, xem {@link GameAction}). {@code dirY} LUÔN
     * gửi {@code 0f}: field còn tồn tại trên dây (movement.proto không xoá field đang dùng,
     * shared/CLAUDE.md) nhưng server-world giờ bỏ qua nó (WorldFacade.handleMoveIntent) — giữ 0
     * cục bộ để dự đoán khớp đúng những gì server THẬT SỰ áp dụng.
     */
    private void updateLocalMovementIntent() {
        float dirX = 0f;
        if (isPressed(GameAction.MOVE_LEFT)) {
            dirX -= 1f;
        }
        if (isPressed(GameAction.MOVE_RIGHT)) {
            dirX += 1f;
        }
        // dirX chỉ có thể là -1/0/1 (2 flag loại trừ cùng lúc) — không cần chuẩn hoá độ dài như
        // trước ADR-033 (khi còn 2 trục, đi chéo cần chia cho sqrt(2)).
        float normalizedDirX = dirX;

        // Quay mặt theo chiều đi; dirX == 0 (đứng yên) GIỮ NGUYÊN hướng cũ, không quay về mặc định.
        if (normalizedDirX != 0f) {
            models.setFacingRight(LOCAL_CHARACTER_ID, normalizedDirX > 0f);
        }

        VelocityComponent velocity = VELOCITY.get(localPlayer);
        velocity.x = normalizedDirX * WorldConstants.MOVE_SPEED_MPS;
        velocity.y = 0f;

        if (normalizedDirX != lastSentDirX) {
            lastSentDirX = normalizedDirX;
            sender.moveDirection(normalizedDirX);
        }
    }

    /**
     * ADR-033 + nhảy đôi (chủ dự án yêu cầu 2026-08-09) — luật nhảy nằm trong
     * {@link JumpInputController}; ở đây chỉ nối input với mạng.
     */
    private void updateJumpIntent(float fixedDeltaTime) {
        boolean jumpPressed = isPressed(GameAction.JUMP);
        if (jumpInput.update(jumpPressed, POSITION.get(localPlayer), fixedDeltaTime)) {
            sender.jump();
        }
    }

    /**
     * Đánh thường BẰNG TAY (chủ dự án yêu cầu 2026-08-09) — phím Z hoặc nút Đánh trên HUD, gộp ở
     * {@link AttackInputController}. Animation vung đòn phát NGAY tại chỗ (dự đoán cục bộ hợp lệ
     * theo CLIENT.md §6 — animation không quyết định gameplay); kết quả thật tới sau qua
     * {@code AttackResultEvent} và có thể là trượt/ngoài tầm, đó là chuyện của server.
     */
    private void updateAttackIntent(float fixedDeltaTime) {
        // Đang nói chuyện thì phím Z/Enter thuộc về khung thoại, không phải về combat: người chơi
        // đang đọc chữ, không nhắm ai. Vẫn cho đi lại/nhảy — đứng hình khi mở thoại còn khó chịu hơn.
        if (dialogue.isOpen() || !attackInput.update(isPressed(GameAction.ATTACK), fixedDeltaTime)) {
            return;
        }
        CombatHudController.MonsterState target = combatHudController.targetState();
        if (target == null) {
            // Không có mục tiêu thì server cũng không làm gì — nói ra thay vì im lặng, và KHÔNG gửi
            // gói thừa (cùng lý do CombatHudController.useSkill không gửi khi chưa đủ điều kiện).
            combatFeedback.showSelfNotice("hud.combat.no_target", FloatingCombatText.MISS);
            return;
        }
        if (!withinAttackRange(target)) {
            // VẪN gửi: client chỉ ĐOÁN tầm (xem CombatConstants.ATTACK_RANGE_METERS), server mới
            // quyết. Nói cho người chơi biết lý do khả dĩ thay vì để họ bấm mãi không hiểu gì.
            combatFeedback.showSelfNotice("hud.combat.out_of_range", FloatingCombatText.MISS);
        }
        models.playAttack(LOCAL_CHARACTER_ID);
        sender.attack();
    }

    private boolean withinAttackRange(CombatHudController.MonsterState target) {
        PositionComponent pos = POSITION.get(localPlayer);
        float dx = target.x() - pos.x;
        float dy = target.y() - pos.y;
        // So bình phương — tránh Math.sqrt mỗi lần bấm đánh, và tránh cả sai số của phép căn.
        return (dx * dx) + (dy * dy) <= CombatConstants.ATTACK_RANGE_METERS * CombatConstants.ATTACK_RANGE_METERS;
    }

    /**
     * Phím Enter = bấm nút skill (chủ dự án yêu cầu 2026-08-09) — đi qua ĐÚNG đường của nút chuột
     * ({@link #onSkillButtonClicked}) chứ không nhân bản logic, nên hồi chiêu/Khí/mục tiêu được
     * kiểm y hệt. Rising edge: giữ Enter không bắn skill liên tục.
     */
    private void updateSkillIntent() {
        boolean skillPressed = isPressed(GameAction.USE_SKILL);
        if (skillPressed && !skillKeyWasPressed && !dialogue.isOpen()) {
            onSkillButtonClicked();
        }
        skillKeyWasPressed = skillPressed;
    }

    /**
     * Click chuột trái vào thế giới — trúng quái thì chọn làm mục tiêu (2026-08-09), trúng NPC thì
     * bắt chuyện.
     *
     * <p>Thử QUÁI TRƯỚC: hai loại chỉ chồng nhau khi chúng đứng gần nhau trên màn hình, và trong
     * tình huống đó việc gấp là đánh chứ không phải nói chuyện.
     *
     * @param screenX toạ độ chuột thô từ {@code InputProcessor}.
     * @param screenY toạ độ chuột thô ({@code InputProcessor} lấy gốc TRÊN-trái).
     * @return {@code true} nếu click trúng thứ gì đó — nuốt sự kiện, không để rơi tiếp xuống.
     */
    private boolean selectTargetAt(int screenX, int screenY) {
        // Lật y sang quy ước GL của WorldToScreen (gốc DƯỚI-trái).
        float glScreenY = Gdx.graphics.getHeight() - screenY;
        WorldToScreen projector = models.isEnabled() ? modelProjector : fallbackProjector;
        long monsterId = entityPicker.pick(combatHudController.monsters(), screenX, glScreenY, projector);
        if (monsterId != 0L) {
            if (combatHudController.selectTarget(monsterId)) {
                sender.setTarget(monsterId);
            }
            return true;
        }
        return talkToNpcAt(screenX, glScreenY, projector);
    }

    private boolean talkToNpcAt(float screenX, float glScreenY, WorldToScreen projector) {
        long npcId = entityPicker.pick(npcPresenter.npcs(), screenX, glScreenY, projector);
        if (npcId == 0L) {
            return false; // click vào chỗ trống — GIỮ NGUYÊN mục tiêu cũ, không bỏ chọn.
        }
        NpcPresenter.NpcState npc = npcPresenter.npcs().get(npcId);
        // Hai khung nhập liệu cùng mở là một cái bẫy phím: ESC sẽ đóng khung thoại (nó đứng trước
        // trong InputMultiplexer) chứ không đóng ô chat đang giữ keyboard focus.
        chat.closeComposer();
        dialogue.talkTo(npc.code());
        return true;
    }

    @Override
    public void render(float alpha) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        // Depth buffer bắt buộc từ ADR-031: nhân vật là hình học 3D thật, thiếu depth thì thứ tự vẽ
        // quyết định cái nào che cái nào — đúng thứ 2.5D phải bỏ.
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (models.isEnabled()) {
            renderModels(alpha);
            renderMonsters(models.combined());
            DemoPlatforms.render(shapeRenderer, models.combined());
            // VFX/số damage vẽ SAU cùng trong nhánh 3D — luôn hiện đè lên nhân vật/địa hình, đúng
            // thứ tự "nhân vật vẽ trước, VFX vẽ đè sau" của RENDERING.md §2.
            combatFeedback.render(Gdx.graphics.getDeltaTime(), models.combined(), modelProjector);
        } else {
            renderPlaceholderBoxes(alpha);
            renderMonsters(camera.combined);
            DemoPlatforms.render(shapeRenderer, camera.combined);
            combatFeedback.render(Gdx.graphics.getDeltaTime(), camera.combined, fallbackProjector);
        }

        syncCombatHud();
        combatHud.render(Gdx.graphics.getDeltaTime());
        chatPanel.render(Gdx.graphics.getDeltaTime());
        dialoguePanel.render(Gdx.graphics.getDeltaTime());
    }

    /**
     * Phần vẽ quái mà {@link WorldModelRenderer} không lo được — hộp thay thế cho mã quái chưa có
     * art, và dấu mục tiêu. Dùng ĐÚNG ma trận chiếu của nhánh render đang hoạt động để không lệch
     * không gian chiếu so với model.
     */
    private void renderMonsters(Matrix4 projection) {
        monsterPresenter.render(
                shapeRenderer, projection, combatHudController.monsters(), combatHudController.currentTargetId());
        npcPresenter.render(shapeRenderer, projection);
    }

    private void syncCombatHud() {
        combatHud.setPlayerHp(combatHudController.playerHp(), combatHudController.playerMaxHp());
        combatHud.setPlayerChi(combatHudController.playerChi(), combatHudController.playerMaxChi());
        combatHud.setLevel(characterLevel);
        CombatHudController.MonsterState target = combatHudController.targetState();
        if (target != null) {
            combatHud.setTarget(target.code(), target.hp(), target.maxHp());
        } else {
            combatHud.clearTarget();
        }
        combatHud.setSkillState(combatHudController.skillNameKey(), combatHudController.skillCooldownRemaining());
        combatHud.setSkillReady(combatHudController.canUseSkill());
    }

    /** Nhánh 3D (ADR-031) — model glTF qua camera orthographic khoá góc. */
    private void renderModels(float alpha) {
        float localX = interpolatedX(localPlayer, alpha);
        float localY = interpolatedY(localPlayer, alpha);
        float localZ = interpolatedZ(localPlayer, alpha);
        models.setPosition(LOCAL_CHARACTER_ID, localX, localY, localZ);
        models.setMoving(LOCAL_CHARACTER_ID, isLocalPlayerMoving());
        // ADR-033 — grounded là trạng thái dự đoán cục bộ/xác nhận qua MoveResponse, KHÔNG nội suy
        // (giống lý do isLocalPlayerMoving đọc VelocityComponent thẳng, không qua interpolation).
        models.setAirborne(LOCAL_CHARACTER_ID, !POSITION.get(localPlayer).grounded);
        remoteCharacters.sync(alpha);
        // Quái đi cùng lượt vẽ với nhân vật (cùng Scene list, cùng camera) — chỉ nguồn dữ liệu là
        // khác: chúng không có entity ECS phía client, xem MonsterPresenter.sync.
        monsterPresenter.sync(combatHudController.monsters(), localX);
        npcPresenter.sync(localX);
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
        remoteCharacters.renderPlaceholder(this::drawInterpolated, alpha);

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

    /** ADR-033 — nội suy trục dọc giống hệt {@link #interpolatedX}/{@link #interpolatedY}. */
    private static float interpolatedZ(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevZ + ((pos.z - pos.prevZ) * alpha);
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
        // TRƯỚC combatHud: combatFeedback mượn font của HUD, dispose HUD trước là kéo font ra khỏi
        // dưới chân nó (dù ở đây nó không dùng font lúc dispose, giữ đúng thứ tự sở hữu).
        combatFeedback.dispose();
        // TRƯỚC combatHud như combatFeedback, và cùng lý do: panel mượn skin của HUD.
        dialoguePanel.dispose();
        chatPanel.dispose();
        combatHud.dispose();
    }

    /**
     * Tầng input DƯỚI HUD trong {@link #inputProcessor} — chỉ quan tâm click chuột trái vào thế
     * giới. Không xử lý phím: mọi phím vẫn poll qua {@link InputMapper} như trước (CLIENT.md §5),
     * lớp này không được biết mã phím cụ thể.
     */
    private final class WorldTargetPicker extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT) {
                return false;
            }
            return selectTargetAt(screenX, screenY);
        }
    }
}
