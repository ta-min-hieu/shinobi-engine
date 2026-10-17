package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.ObjectMap;
import net.mgsx.gltf.scene3d.animation.NodeAnimationHack;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.scene.SceneModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.engine.ecs.EcsWorld;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.Faction;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.NpcSpawnedEvent;

/**
 * Đo số byte cấp phát MỖI KHUNG của các đường client chạy trong game loop — luật "không {@code new}
 * mỗi frame" (/CLAUDE.md §6) và "benchmark trước, refactor sau" (§9) gặp nhau ở đây.
 *
 * <p>Đo bằng {@link com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes()} — bộ đếm byte
 * cấp phát của CHÍNH luồng test, chính xác tới byte (gồm cả phần TLAB đang dùng dở), không phụ thuộc
 * GC chạy hay không. Đo SAU warm-up đủ dài để C2 biên dịch các hàm nóng: escape analysis của C2 có
 * thể khử được iterator/boxing tạm, nên "for-each trên HashMap cấp phát" là giả thuyết phải ĐO chứ
 * không phải suy ra từ đọc code.
 *
 * <p>Cảnh điển hình: 20 người chơi khác, 15 quái, 3 NPC, MỌI thực thể đều có model (asset giả dựng
 * thuần dữ liệu — {@link WorldModelRenderer#preloadAsset} — vì không có GL context để nạp glTF thật).
 * Giữa các khung, cứ 3 khung (20Hz, nhịp AoI sync của server ở 60fps) thì một nửa người chơi và quái
 * nhận gói di chuyển — để đường idle/run lật thật, không chỉ đứng yên. Phần nạp gói đó KHÔNG nằm
 * trong cửa sổ đo: nó là đường xử lý gói mạng (đã cấp phát protobuf), không phải đường mỗi khung.
 *
 * <p>Không đo được ở đây (cần GPU thật): {@code WorldModelRenderer.render}, các {@code render} vẽ hộp
 * của {@link MonsterPresenter}/{@link NpcPresenter}, {@code CombatHud} (Stage + font FreeType).
 */
class FrameAllocationTest {

    private static final Logger LOG = LoggerFactory.getLogger(FrameAllocationTest.class);

    private static final int REMOTE_PLAYERS = 20;
    private static final int MONSTERS = 15;
    private static final int NPCS = 3;

    /** Đủ dài để C2 (tier 4) biên dịch mọi hàm nóng — ngưỡng mặc định cỡ vài nghìn lượt gọi. */
    private static final int WARMUP_FRAMES = 30_000;

    private static final int MEASURED_FRAMES = 5_000;
    private static final int ROUNDS = 5;

    /** Nhịp AoI sync 20Hz ở 60 khung/giây. */
    private static final int FRAMES_PER_NETWORK_UPDATE = 3;

    private static final float FIXED_DT = 1f / 60f;
    private static final float ALPHA = 0.5f;
    private static final float PLAYER_X = 3f;

    private static final long FIRST_CHARACTER_ID = 1_000L;
    private static final long FIRST_MONSTER_ID = -1_000L;
    private static final long FIRST_NPC_ID = -5_000L;

    private static final String MONSTER_MODEL_KEY = "mob_test";

    /**
     * Trần cho đường còn phụ thuộc JIT — xem {@link #constantSmallAllocationPaths}. Đo thật
     * 2026-10-03: 0 (quái), 32 (NPC — iterator {@code HashMap}), 24 (HUD — một {@code Long} của
     * {@code targetState()}). NPC và HUD về 0 từ 2026-10-17 — xem {@link #npcSyncAndHudReads_areAllocationFree}.
     */
    private static final long JIT_DEPENDENT_LIMIT_BYTES = 64L;

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private static GL20 previousGl;
    private WorldModelRenderer models;
    private RemoteCharacterPresenter remoteCharacters;
    private MonsterPresenter monsterPresenter;
    private NpcPresenter npcPresenter;
    private CombatHudController combatHud;
    private CharacterMovedEvent[][] characterMoves;
    private MonsterMovedEvent[][] monsterMoves;

    /** Một đường mỗi khung cần đo. */
    @FunctionalInterface
    private interface FrameWork {
        void run();
    }

    @BeforeAll
    static void installHeadlessGl() {
        // follow() -> OrthographicCamera.update() gọi Matrix4.prj bản native.
        GdxNativesLoader.load();
        previousGl = Gdx.gl;
        // ModelBatch (trong constructor WorldModelRenderer) hỏi GL số texture unit — chỉ câu đó.
        Gdx.gl = headlessGl();
    }

    @AfterAll
    static void restoreGl() {
        Gdx.gl = previousGl;
    }

    @BeforeEach
    void setUp() {
        models = new WorldModelRenderer(GraphicsTier.MEDIUM);
        SceneAsset asset = fakeAsset();
        models.preloadAsset(CharacterModelKey.of(Faction.FACTION_HOA, CostumeTier.COSTUME_TIER_B1), asset);
        models.preloadAsset(MONSTER_MODEL_KEY, asset);
        models.addCharacter(
                WorldConstants.LOCAL_CHARACTER_ID,
                CharacterModelKey.of(Faction.FACTION_HOA, CostumeTier.COSTUME_TIER_B1));

        remoteCharacters = new RemoteCharacterPresenter(new EcsWorld(), models, 1L);
        monsterPresenter = new MonsterPresenter(models);
        npcPresenter = new NpcPresenter(models);
        combatHud = new CombatHudController(1L, Faction.FACTION_HOA);
        populateScene(asset);
    }

    @AfterEach
    void tearDown() {
        models.dispose();
    }

    /**
     * Đường đã chuyển sang {@link com.badlogic.gdx.utils.LongMap}: KHÔNG cấp phát theo CẤU TRÚC (tra
     * khoá {@code long} không boxing, iterator dựng sẵn) — không dựa vào JIT, nên ngưỡng là đúng 0.
     */
    @Test
    void structurallyAllocationFreePaths() {
        assertThat(measure("RemoteCharacterPresenter.sync", () -> remoteCharacters.sync(ALPHA)))
                .isZero();
        assertThat(measure("RemoteCharacterPresenter.tick", () -> remoteCharacters.tick(FIXED_DT)))
                .isZero();
        assertThat(measure("MonsterPresenter.tick", () -> monsterPresenter.tick(FIXED_DT)))
                .isZero();
        assertThat(measure("WorldModelRenderer local player", this::localPlayerFrame))
                .isZero();
        assertThat(models.hasModel(FIRST_MONSTER_ID)).isTrue();
    }

    /**
     * Đường còn lặp/tra {@code java.util.HashMap}: con số phụ thuộc escape analysis của C2, nên ngưỡng
     * có biên. Biên vẫn chặn được điều đáng chặn: một lần boxing cho MỖI thực thể (trước 2026-10-03:
     * ~120–170 byte mỗi thực thể mỗi khung) vượt ngưỡng này ngay từ thực thể thứ nhất của cảnh.
     */
    @Test
    void constantSmallAllocationPaths() {
        assertThat(measure("baseline (empty)", () -> {})).isZero();
        assertThat(measure("MonsterPresenter.sync", () -> monsterPresenter.sync(combatHud.monsters(), PLAYER_X)))
                .isLessThanOrEqualTo(JIT_DEPENDENT_LIMIT_BYTES);
    }

    /**
     * Dòng 57 (chủ dự án duyệt 2026-10-17): NPC duyệt danh sách bằng chỉ số thay vì iterator
     * {@code HashMap} (từng 32 B), HUD tra mục tiêu bằng khoá đã đóng hộp sẵn thay vì đóng hộp một
     * {@code Long} mỗi khung (từng 24 B) — cả hai nay không phụ thuộc JIT, nên ngưỡng là đúng 0.
     */
    @Test
    void npcSyncAndHudReads_areAllocationFree() {
        assertThat(measure("NpcPresenter.sync", () -> npcPresenter.sync(PLAYER_X)))
                .isZero();
        assertThat(measure("CombatHudController (tick + HUD reads)", this::combatHudFrame))
                .isZero();
    }

    /**
     * Mục tiêu là NHÂN VẬT (đối thủ đấu trường, 2026-10-04): đường HUD mỗi khung chỉ đọc số nguyên và
     * một chuỗi có sẵn — không tra {@code Map<Long, …>} nào, nên ngưỡng là đúng 0.
     */
    @Test
    void characterTargetHudPath_isAllocationFree() {
        CombatHudController duelHud = new CombatHudController(1L, Faction.FACTION_HOA);
        duelHud.bindCharacterPositions(remoteCharacters.characters());
        assertThat(duelHud.selectCharacterTarget(FIRST_CHARACTER_ID, "opponent"))
                .isTrue();

        assertThat(measure("CombatHudController character target reads", () -> {
                    duelHud.tick(FIXED_DT);
                    if (duelHud.hasCharacterTarget()) {
                        consume(duelHud.characterTargetName().length()
                                + duelHud.characterTargetHp()
                                + duelHud.characterTargetMaxHp());
                    }
                    consume(duelHud.canUseSkill() ? 1 : 0);
                }))
                .isZero();
    }

    private void combatHudFrame() {
        combatHud.tick(FIXED_DT);
        consume(combatHud.playerHp() + combatHud.playerMaxHp() + combatHud.playerChi() + combatHud.playerMaxChi());
        CombatHudController.MonsterState target = combatHud.targetState();
        if (target != null) {
            consume(target.hp() + target.maxHp());
        }
        consume(combatHud.skillNameKey().length());
        consume(combatHud.canUseSkill() ? 1 : 0);
        consume((int) combatHud.skillCooldownRemaining());
    }

    private void localPlayerFrame() {
        models.setPosition(WorldConstants.LOCAL_CHARACTER_ID, PLAYER_X, 0f, 0f);
        models.setMoving(WorldConstants.LOCAL_CHARACTER_ID, false);
        models.setAirborne(WorldConstants.LOCAL_CHARACTER_ID, false);
        models.follow(PLAYER_X, 0f);
    }

    private int sink;

    private void consume(int value) {
        sink += value;
    }

    private long measure(String name, FrameWork work) {
        int frame = 0;
        for (int i = 0; i < WARMUP_FRAMES; i++) {
            feedNetwork(frame++);
            work.run();
        }
        long best = Long.MAX_VALUE;
        for (int round = 0; round < ROUNDS; round++) {
            long total = 0L;
            for (int i = 0; i < MEASURED_FRAMES; i++) {
                feedNetwork(frame++);
                long before = THREADS.getCurrentThreadAllocatedBytes();
                work.run();
                total += THREADS.getCurrentThreadAllocatedBytes() - before;
            }
            best = Math.min(best, total);
        }
        long bytesPerFrame = best / MEASURED_FRAMES;
        LOG.info(
                "ALLOC {} = {} byte/khung (min of {} rounds x {} frames, sink {})",
                name,
                bytesPerFrame,
                ROUNDS,
                MEASURED_FRAMES,
                sink);
        return bytesPerFrame;
    }

    /** Ngoài cửa sổ đo — xem javadoc lớp. */
    private void feedNetwork(int frame) {
        if (frame % FRAMES_PER_NETWORK_UPDATE != 0) {
            return;
        }
        int phase = (frame / FRAMES_PER_NETWORK_UPDATE) % 2;
        for (int i = 0; i < REMOTE_PLAYERS; i += 2) {
            remoteCharacters.onMoved(characterMoves[phase][i]);
        }
        for (int i = 0; i < MONSTERS; i += 2) {
            combatHud.onMonsterMoved(monsterMoves[phase][i]);
            monsterPresenter.onMoved(monsterMoves[phase][i]);
        }
    }

    private void populateScene(SceneAsset asset) {
        characterMoves = new CharacterMovedEvent[2][REMOTE_PLAYERS];
        for (int i = 0; i < REMOTE_PLAYERS; i++) {
            long id = FIRST_CHARACTER_ID + i;
            remoteCharacters.onSpawned(CharacterSpawnedEvent.newBuilder()
                    .setCharacterId(id)
                    .setX(i)
                    .setFaction(Faction.FACTION_HOA)
                    .setCostumeTier(CostumeTier.COSTUME_TIER_B1)
                    .build());
            for (int phase = 0; phase < 2; phase++) {
                characterMoves[phase][i] = CharacterMovedEvent.newBuilder()
                        .setCharacterId(id)
                        .setX(i + phase)
                        .build();
            }
        }
        monsterMoves = new MonsterMovedEvent[2][MONSTERS];
        for (int i = 0; i < MONSTERS; i++) {
            long id = FIRST_MONSTER_ID - i;
            MonsterSpawnedEvent spawned = MonsterSpawnedEvent.newBuilder()
                    .setMonsterId(id)
                    .setMonsterCode("test_mob")
                    .setModelKey(MONSTER_MODEL_KEY)
                    .setX(i)
                    .setCurrentHp(50)
                    .setMaxHp(50)
                    .build();
            combatHud.onMonsterSpawned(spawned);
            monsterPresenter.onSpawned(spawned);
            for (int phase = 0; phase < 2; phase++) {
                monsterMoves[phase][i] = MonsterMovedEvent.newBuilder()
                        .setMonsterId(id)
                        .setX(i + phase)
                        .build();
            }
        }
        for (int i = 0; i < NPCS; i++) {
            String code = "test_npc_" + i;
            models.preloadAsset(NpcModelKey.of(code), asset);
            npcPresenter.onSpawned(NpcSpawnedEvent.newBuilder()
                    .setNpcId(FIRST_NPC_ID - i)
                    .setNpcCode(code)
                    .setX(i)
                    .build());
        }
    }

    /**
     * Asset thuần dữ liệu: một {@link Model} rỗng mang đủ hai clip loop {@code idle}/{@code run} để
     * {@code setMoving} đi đúng nhánh {@code animate} thật khi trạng thái lật.
     */
    private static SceneAsset fakeAsset() {
        Model model = new Model();
        Node root = new Node();
        root.id = "root";
        model.nodes.add(root);
        model.animations.add(animation(CharacterAnimationClips.IDLE, root));
        model.animations.add(animation(CharacterAnimationClips.RUN, root));
        SceneModel sceneModel = new SceneModel();
        sceneModel.model = model;
        sceneModel.cameras = new ObjectMap<>();
        sceneModel.lights = new ObjectMap<>();
        SceneAsset asset = new SceneAsset();
        asset.scene = sceneModel;
        return asset;
    }

    private static Animation animation(String id, Node node) {
        Animation animation = new Animation();
        animation.id = id;
        animation.duration = 1f;
        NodeAnimationHack track = new NodeAnimationHack();
        track.node = node;
        // ModelInstanceHack bỏ qua track không có keyframe nào — cần ít nhất một để clip tồn tại.
        track.translation = new Array<>();
        track.translation.add(new NodeKeyframe<>(0f, new Vector3()));
        animation.nodeAnimations.add(track);
        return animation;
    }

    /** GL giả trả 0/false/null cho mọi lệnh, trừ số texture unit mà {@code DefaultTextureBinder} cần. */
    private static GL20 headlessGl() {
        return (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(), new Class<?>[] {GL20.class}, FrameAllocationTest::invokeGl);
    }

    private static Object invokeGl(Object proxy, Method method, Object[] args) {
        if ("glGetIntegerv".equals(method.getName())) {
            ((IntBuffer) args[1]).put(0, 16);
        }
        Class<?> type = method.getReturnType();
        if (type == int.class) {
            return 0;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == float.class) {
            return 0f;
        }
        return null;
    }
}
