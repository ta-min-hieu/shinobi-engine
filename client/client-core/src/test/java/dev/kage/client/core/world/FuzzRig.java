package dev.kage.client.core.world;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.google.protobuf.ByteString;

import dev.kage.client.core.input.GameAction;
import dev.kage.client.core.input.InputMapper;
import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.EcsWorld;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.Faction;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Một {@link WorldScreen} THẬT dựng trên {@link HeadlessGdx} và một đường truyền giả: test đẩy gói
 * vào hàng đợi đến, chạy một khung ({@code fixedUpdate} + {@code render}) và đọc lại mọi lệnh màn
 * hình đã gửi lên "server".
 */
final class FuzzRig {

    static final float FIXED_DT = 1f / 60f;
    static final int LOCAL_MAX_HP = 100;
    static final int LOCAL_HP = 80;
    static final float SPAWN_X = 3f;
    static final int LOCAL_LEVEL = 12;
    private static final float RENDER_ALPHA = 0.5f;
    private static final int RECENT_SEQUENCE_IDS = 16;
    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    final FakeLink link = new FakeLink();
    final EcsWorld world = new EcsWorld();
    final WorldScreen screen;
    final InputProcessor input;

    // Bên trong màn hình, đọc bằng reflection MỘT lần lúc dựng: WorldScreen đã chạm trần độ phức tạp
    // PMD cho cả lớp, không còn chỗ cho các hàm get chỉ-để-test. Đổi tên field ⇒ test đỏ ngay ở đây.
    final CombatHudController hud;
    final RemoteCharacterPresenter characters;
    final NpcPresenter npcs;
    final PositionComponent localPosition;
    final OrthographicCamera camera;
    String sessionEndedKey;

    FuzzRig() {
        CharacterSummary character = CharacterSummary.newBuilder()
                .setId(PoisonMessageFactory.LOCAL_ID)
                .setName("fuzz")
                .setFaction(Faction.FACTION_HOA)
                .setLevel(LOCAL_LEVEL)
                .build();
        SelectCharacterResponse enterWorld = SelectCharacterResponse.newBuilder()
                .setSuccess(true)
                .setCurrentHp(LOCAL_HP)
                .setMaxHp(LOCAL_MAX_HP)
                .setSpawnX(SPAWN_X)
                .build();
        screen = new WorldScreen(
                new PlayContext(world, new IdleInput()), new WorldCommandSender(link), character, enterWorld);
        screen.setOnSessionEnded(key -> sessionEndedKey = key);
        HeadlessGdx.forgetInputProcessor();
        screen.resize(HeadlessGdx.SCREEN_WIDTH, HeadlessGdx.SCREEN_HEIGHT);
        screen.show();
        input = HeadlessGdx.capturedInputProcessor;
        hud = field(screen, "combatHudController", CombatHudController.class);
        characters = field(screen, "remoteCharacters", RemoteCharacterPresenter.class);
        npcs = field(screen, "npcPresenter", NpcPresenter.class);
        camera = field(screen, "camera", OrthographicCamera.class);
        localPosition = POSITION.get(field(screen, "localPlayer", KEntity.class));
        // Hai lời hỏi của show() (catalog, nhật ký nhiệm vụ) là do VÀO MÀN chứ không do gói nào.
        link.sent.clear();
    }

    private static <T> T field(WorldScreen screen, String name, Class<T> type) {
        try {
            Field field = WorldScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(screen));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("WorldScreen không còn field '" + name + "' — sửa FuzzRig", e);
        }
    }

    /** Một khung game: gói đến được xử lý ở đầu {@code fixedUpdate}, rồi mô phỏng, rồi vẽ. */
    void frame() {
        screen.fixedUpdate(FIXED_DT);
        if (sessionEndedKey == null) {
            world.update(FIXED_DT);
            screen.render(RENDER_ALPHA);
        }
    }

    void deliver(int opcode, int sequenceId, byte[] payload) {
        link.inbound.add(new PacketCodec.Frame(new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId), payload));
    }

    void dispose() {
        screen.dispose();
    }

    /** Xem {@link DisplayInvariants} — rỗng nghĩa là sạch. */
    List<String> stateViolations() {
        return DisplayInvariants.check(this);
    }

    /**
     * {@code sequenceId} của lệnh đang chờ mà gói {@code reply} trả lời — quy ước đánh số của giao
     * thức là lệnh N, trả lời N+1. Chưa gửi lệnh nào như thế thì dùng {@code fallback}.
     */
    int matchingSequenceId(int reply, int fallback) {
        Integer sequenceId = link.lastSequenceIdByOpcode.get(reply - 1);
        return sequenceId != null ? sequenceId : fallback;
    }

    void press(int key) {
        input.keyDown(key);
        input.keyUp(key);
    }

    /** Đường truyền giả — xem {@link CommandLink}. */
    static final class FakeLink implements CommandLink {
        final Deque<PacketCodec.Frame> inbound = new ArrayDeque<>();
        final List<Sent> sent = new ArrayList<>();
        final Deque<Integer> recentSequenceIds = new ArrayDeque<>();
        /** sequenceId của lần GẦN NHẤT gửi mỗi opcode — để gói trả lời "khớp" được lệnh đang chờ. */
        final Map<Integer, Integer> lastSequenceIdByOpcode = new HashMap<>();

        private int nextSequenceId = 1;
        boolean stopped;

        @Override
        public void send(int opcode, int sequenceId, byte[] payload) {
            sent.add(new Sent(opcode, ByteString.copyFrom(payload)));
            lastSequenceIdByOpcode.put(opcode, sequenceId);
            recentSequenceIds.addFirst(sequenceId);
            if (recentSequenceIds.size() > RECENT_SEQUENCE_IDS) {
                recentSequenceIds.removeLast();
            }
        }

        @Override
        public PacketCodec.Frame pollInbound() {
            return inbound.poll();
        }

        @Override
        public boolean isConnected() {
            return !stopped;
        }

        @Override
        public int connectionGeneration() {
            return 1;
        }

        @Override
        public int nextSequenceId() {
            return nextSequenceId++;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    /** Một lệnh màn hình đã gửi lên "server". */
    record Sent(int opcode, ByteString payload) {}

    /** Không phím nào đang giữ — mọi lệnh gửi đi trong lúc nhận gói đều là do GÓI gây ra. */
    private static final class IdleInput implements InputMapper {
        @Override
        public boolean isPressed(GameAction action) {
            return false;
        }
    }
}
