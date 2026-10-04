package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Mesh;
import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterAppearanceChangedEvent;
import dev.kage.protocol.CharacterDespawnedEvent;
import dev.kage.protocol.CharacterMovedEvent;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.CharacterSpawnedEvent;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.ExitDuelResponse;
import dev.kage.protocol.ExitDungeonResponse;
import dev.kage.protocol.HealthChangedEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.NpcDespawnedEvent;
import dev.kage.protocol.NpcSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.WorldKind;

/**
 * Những ca "thứ tự lạ"/"giá trị độc" mà {@link InboundPacketFuzzTest} chỉ gặp theo xác suất —
 * viết ra tường minh để mỗi ca có tên, và để mỗi lỗi fuzz đã tìm ra có một ca hồi quy xác định.
 *
 * <p>Mọi ca chạy trên {@link WorldScreen} THẬT ({@link FuzzRig}); sau mỗi gói,
 * {@link FuzzRig#stateViolations()} phải rỗng.
 */
class InboundPacketScenarioTest {

    private static final long LOCAL = PoisonMessageFactory.LOCAL_ID;
    private static final long OTHER = 1_000L;
    private static final long MONSTER = -100L;
    private static final long NPC = -5_000L;
    private static final int MONSTER_MAX_HP = 50;
    private static final int GC_ATTEMPTS = 20;
    private static final Pattern MESH_COUNT = Pattern.compile("\\{\\s*(\\d+)\\s*\\}");

    private FuzzRig rig;

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install();
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @BeforeEach
    void setUp() {
        rig = new FuzzRig();
    }

    @AfterEach
    void tearDown() {
        if (rig != null) {
            rig.dispose();
        }
    }

    @Test
    void packetsAboutUnseenEntitiesCreateNothing() {
        send(
                Opcode.OPCODE_CHARACTER_MOVED_EVENT,
                CharacterMovedEvent.newBuilder().setCharacterId(OTHER).setX(1f).build());
        send(
                Opcode.OPCODE_CHARACTER_APPEARANCE_CHANGED_EVENT,
                CharacterAppearanceChangedEvent.newBuilder()
                        .setCharacterId(OTHER)
                        .build());
        send(
                Opcode.OPCODE_MONSTER_MOVED_EVENT,
                MonsterMovedEvent.newBuilder().setMonsterId(MONSTER).setX(1f).build());
        send(
                Opcode.OPCODE_ATTACK_RESULT_EVENT,
                AttackResultEvent.newBuilder()
                        .setTargetEntityId(MONSTER)
                        .setTargetCurrentHp(5)
                        .build());
        send(
                Opcode.OPCODE_HEALTH_CHANGED_EVENT,
                HealthChangedEvent.newBuilder()
                        .setEntityId(MONSTER)
                        .setCurrentHp(5)
                        .setMaxHp(9)
                        .build());
        send(
                Opcode.OPCODE_MONSTER_DESPAWNED_EVENT,
                MonsterDespawnedEvent.newBuilder().setMonsterId(MONSTER).build());
        send(
                Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT,
                CharacterDespawnedEvent.newBuilder().setCharacterId(OTHER).build());
        send(
                Opcode.OPCODE_NPC_DESPAWNED_EVENT,
                NpcDespawnedEvent.newBuilder().setNpcId(NPC).build());

        assertNothingVisible();
        assertThat(rig.link.sent).isEmpty();
    }

    @Test
    void despawningTwiceIsHarmless() {
        spawnOneOfEach();

        for (int i = 0; i < 2; i++) {
            send(
                    Opcode.OPCODE_CHARACTER_DESPAWNED_EVENT,
                    CharacterDespawnedEvent.newBuilder().setCharacterId(OTHER).build());
            send(
                    Opcode.OPCODE_MONSTER_DESPAWNED_EVENT,
                    MonsterDespawnedEvent.newBuilder().setMonsterId(MONSTER).build());
            send(
                    Opcode.OPCODE_NPC_DESPAWNED_EVENT,
                    NpcDespawnedEvent.newBuilder().setNpcId(NPC).build());
        }

        assertNothingVisible();
        assertThat(rig.hud.currentTargetId()).isZero();
    }

    @Test
    void worldChangeClearsTheOldWorldAndIgnoresItsLatePackets() {
        spawnOneOfEach();

        send(
                Opcode.OPCODE_WORLD_CHANGED_EVENT,
                WorldChangedEvent.newBuilder()
                        .setWorldKind(WorldKind.WORLD_KIND_DUNGEON)
                        .setX(5f)
                        .setCurrentHp(70)
                        .setMaxHp(FuzzRig.LOCAL_MAX_HP)
                        .build());
        // Gói của World CŨ còn trên đường bay tới — không được dựng lại thứ vừa dọn.
        send(
                Opcode.OPCODE_CHARACTER_MOVED_EVENT,
                CharacterMovedEvent.newBuilder().setCharacterId(OTHER).setX(2f).build());
        send(
                Opcode.OPCODE_MONSTER_MOVED_EVENT,
                MonsterMovedEvent.newBuilder().setMonsterId(MONSTER).setX(2f).build());
        send(
                Opcode.OPCODE_ATTACK_RESULT_EVENT,
                AttackResultEvent.newBuilder()
                        .setTargetEntityId(MONSTER)
                        .setTargetCurrentHp(1)
                        .build());

        assertNothingVisible();
        assertThat(rig.localPosition.x).isEqualTo(5f);
        assertThat(rig.hud.playerHp()).isEqualTo(70);
    }

    @Test
    void duelAndDungeonResultsWithoutAMatchAreHarmless() {
        send(
                Opcode.OPCODE_DUEL_RESULT_EVENT,
                DuelResultEvent.newBuilder()
                        .setWinnerCharacterId(OTHER)
                        .setLoserCharacterId(LOCAL)
                        .setLoserNewRating(Integer.MIN_VALUE)
                        .build());
        send(Opcode.OPCODE_EXIT_DUEL_RESPONSE, ExitDuelResponse.getDefaultInstance());
        send(Opcode.OPCODE_EXIT_DUNGEON_RESPONSE, ExitDungeonResponse.getDefaultInstance());
        // Trận "bắt đầu" với CHÍNH mình làm đối thủ — không được tự nhắm mình.
        send(
                Opcode.OPCODE_DUEL_STARTED_EVENT,
                DuelStartedEvent.newBuilder()
                        .setOpponentCharacterId(LOCAL)
                        .setOpponentName("\u0000")
                        .build());

        assertThat(rig.hud.currentTargetId()).isNotEqualTo(LOCAL);
        assertThat(writeCommands())
                .as("chỉ được hỏi lại server, không được ra lệnh")
                .isEmpty();
    }

    @Test
    void myOwnIdIsNeverAnotherEntity() {
        send(
                Opcode.OPCODE_CHARACTER_SPAWNED_EVENT,
                CharacterSpawnedEvent.newBuilder().setCharacterId(LOCAL).build());
        send(
                Opcode.OPCODE_MONSTER_SPAWNED_EVENT,
                MonsterSpawnedEvent.newBuilder()
                        .setMonsterId(LOCAL)
                        .setMonsterCode("x")
                        .setCurrentHp(1)
                        .setMaxHp(1)
                        .build());
        // Id DƯƠNG là nhân vật (combat.proto) — một "quái" mang id nhân vật là gói hỏng.
        send(
                Opcode.OPCODE_MONSTER_SPAWNED_EVENT,
                MonsterSpawnedEvent.newBuilder()
                        .setMonsterId(OTHER)
                        .setMonsterCode("x")
                        .build());
        send(
                Opcode.OPCODE_NPC_SPAWNED_EVENT,
                NpcSpawnedEvent.newBuilder().setNpcId(0L).setNpcCode("x").build());

        assertNothingVisible();
        assertThat(rig.link.sent)
                .as("không tự nhắm chính mình hay một id không phải quái")
                .isEmpty();
    }

    @Test
    void nonFiniteCoordinatesNeverReachTheScreen() {
        PositionComponent local = rig.localPosition;
        send(
                Opcode.OPCODE_MOVE_RESPONSE,
                MoveResponse.newBuilder().setX(Float.NaN).build());
        send(
                Opcode.OPCODE_MOVE_RESPONSE,
                MoveResponse.newBuilder().setX(Float.POSITIVE_INFINITY).build());
        send(
                Opcode.OPCODE_WORLD_CHANGED_EVENT,
                WorldChangedEvent.newBuilder().setY(Float.NaN).build());
        send(
                Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT,
                CharacterRespawnedEvent.newBuilder().setX(Float.NaN).build());
        send(
                Opcode.OPCODE_CHARACTER_SPAWNED_EVENT,
                CharacterSpawnedEvent.newBuilder()
                        .setCharacterId(OTHER)
                        .setY(Float.NaN)
                        .build());
        send(
                Opcode.OPCODE_MONSTER_SPAWNED_EVENT,
                MonsterSpawnedEvent.newBuilder()
                        .setMonsterId(MONSTER)
                        .setX(Float.NEGATIVE_INFINITY)
                        .build());
        send(
                Opcode.OPCODE_NPC_SPAWNED_EVENT,
                NpcSpawnedEvent.newBuilder().setNpcId(NPC).setX(Float.NaN).build());

        assertNothingVisible();
        assertThat(local.x).isEqualTo(FuzzRig.SPAWN_X);

        // Đã thấy rồi thì gói di chuyển hỏng giữ nguyên vị trí cũ.
        spawnOneOfEach();
        send(
                Opcode.OPCODE_CHARACTER_MOVED_EVENT,
                CharacterMovedEvent.newBuilder()
                        .setCharacterId(OTHER)
                        .setX(Float.NaN)
                        .build());
        send(
                Opcode.OPCODE_MONSTER_MOVED_EVENT,
                MonsterMovedEvent.newBuilder()
                        .setMonsterId(MONSTER)
                        .setY(Float.POSITIVE_INFINITY)
                        .build());
        assertThat(rig.characters.characters().get(OTHER).x()).isEqualTo(1f);
        assertThat(rig.hud.monsters().get(MONSTER).y()).isZero();
    }

    @Test
    void healthAndChiAreClampedToWhatTheHudCanShow() {
        CombatHudController hud = rig.hud;
        send(
                Opcode.OPCODE_ATTACK_RESULT_EVENT,
                AttackResultEvent.newBuilder()
                        .setTargetEntityId(LOCAL)
                        .setTargetCurrentHp(999)
                        .build());
        assertThat(hud.playerHp()).isEqualTo(FuzzRig.LOCAL_MAX_HP);
        send(
                Opcode.OPCODE_HEALTH_CHANGED_EVENT,
                HealthChangedEvent.newBuilder()
                        .setEntityId(LOCAL)
                        .setCurrentHp(-5)
                        .build());
        assertThat(hud.playerHp()).isZero();
        send(
                Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT,
                CharacterRespawnedEvent.newBuilder()
                        .setCurrentHp(Integer.MAX_VALUE)
                        .setMaxHp(FuzzRig.LOCAL_MAX_HP)
                        .setCurrentChi(-1)
                        .build());
        assertThat(hud.playerHp()).isEqualTo(FuzzRig.LOCAL_MAX_HP);
        assertThat(hud.playerChi()).isZero();

        send(
                Opcode.OPCODE_MONSTER_SPAWNED_EVENT,
                MonsterSpawnedEvent.newBuilder()
                        .setMonsterId(MONSTER)
                        .setMonsterCode("x")
                        .setCurrentHp(500)
                        .setMaxHp(MONSTER_MAX_HP)
                        .build());
        assertThat(hud.monsters().get(MONSTER).hp()).isEqualTo(MONSTER_MAX_HP);
    }

    /**
     * Màn world bị bỏ khi ô chat đang giữ bàn phím (bị đá/tự đăng nhập lại giữa lúc gõ) phải được
     * thu hồi HẲN — trước 2026-10-04 tác vụ nháy con trỏ trong {@code Timer} toàn cục giữ cả màn
     * lại, và {@code CombatHud}/{@code TradePanel} không trả {@code SpriteBatch} của mình.
     */
    @Test
    void aDisposedScreenIsReleasedEvenWithAFocusedTextField() {
        int meshesBefore = managedMeshes();
        FuzzRig doomed = rig;
        rig = null;
        doomed.input.keyDown(Input.Keys.T);
        doomed.input.keyUp(Input.Keys.T);
        doomed.frame();
        doomed.frame(); // TextField.draw() thấy focus và lên lịch nháy con trỏ.
        WeakReference<WorldScreen> screen = new WeakReference<>(doomed.screen);
        doomed.dispose();
        doomed = null;
        HeadlessGdx.forgetInputProcessor();

        for (int attempt = 0; attempt < GC_ATTEMPTS && screen.get() != null; attempt++) {
            System.gc();
        }

        assertThat(screen.get()).as("màn world đã dispose vẫn bị giữ lại").isNull();
        assertThat(managedMeshes()).as("mesh của màn đã dispose chưa được trả").isEqualTo(meshesBefore);
    }

    // -------------------------------------------------------------------------------------------

    private void spawnOneOfEach() {
        send(
                Opcode.OPCODE_CHARACTER_SPAWNED_EVENT,
                CharacterSpawnedEvent.newBuilder()
                        .setCharacterId(OTHER)
                        .setX(1f)
                        .build());
        send(
                Opcode.OPCODE_MONSTER_SPAWNED_EVENT,
                MonsterSpawnedEvent.newBuilder()
                        .setMonsterId(MONSTER)
                        .setMonsterCode("x")
                        .setCurrentHp(MONSTER_MAX_HP)
                        .setMaxHp(MONSTER_MAX_HP)
                        .build());
        send(
                Opcode.OPCODE_NPC_SPAWNED_EVENT,
                NpcSpawnedEvent.newBuilder().setNpcId(NPC).setNpcCode("x").build());
        assertThat(rig.characters.isPresent(OTHER)).isTrue();
        assertThat(rig.hud.monsters()).containsKey(MONSTER);
        assertThat(rig.npcs.npcs()).containsKey(NPC);
        rig.link.sent.clear(); // tự nhắm con quái vừa thấy — phản hồi chủ đích, không phải thứ đang kiểm.
    }

    /** Lệnh gửi lên mà KHÔNG phải tra cứu (List…, Get…). */
    private List<String> writeCommands() {
        List<String> writes = new ArrayList<>();
        for (FuzzRig.Sent command : rig.link.sent) {
            String name = Opcode.forNumber(command.opcode()).name();
            if (!name.startsWith("OPCODE_LIST_") && !name.startsWith("OPCODE_GET_")) {
                writes.add(name);
            }
        }
        return writes;
    }

    private void send(Opcode opcode, MessageLite message) {
        rig.deliver(opcode.getNumber(), 0, message.toByteArray());
        rig.frame();
        assertThat(rig.stateViolations()).as("sau %s", opcode).isEmpty();
    }

    private void assertNothingVisible() {
        assertThat(rig.characters.characters()).isEmpty();
        assertThat(rig.hud.monsters()).isEmpty();
        assertThat(rig.npcs.npcs()).isEmpty();
        assertThat(rig.world.entityCount()).isEqualTo(1);
    }

    /** Tổng mesh được libGDX quản lý — chỉ có dạng chuỗi ({@link Mesh#getManagedStatus()}). */
    private static int managedMeshes() {
        Matcher matcher = MESH_COUNT.matcher(Mesh.getManagedStatus());
        int total = 0;
        while (matcher.find()) {
            total += Integer.parseInt(matcher.group(1));
        }
        return total;
    }
}
