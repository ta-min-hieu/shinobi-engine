package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.Faction;
import dev.kage.protocol.HealthChangedEvent;
import dev.kage.protocol.WorldKind;

/**
 * Dòng 32 bảng chờ (2026-10-03): vào Arena bằng client thật thì KHÔNG đánh được đối thủ, vì client
 * chỉ biết đặt mục tiêu là quái. Phương án (a): tự nhắm đối thủ mà server đã báo ở
 * {@code DuelStartedEvent}, gửi {@code SetTargetCommand} — server mới là nơi quyết có đánh được không.
 *
 * <p>Thứ tự gói giữa {@code DuelStartedEvent}, {@code WorldChangedEvent} (308) ARENA và spawn của đối
 * thủ KHÔNG cố định phía server ({@code DuelStartedEvent} gửi ngay sau {@code acceptDuel}, còn 308 +
 * attach chạy trên executor riêng sau khi world chính xả hàng đợi) — nên test cả hai thứ tự.
 */
class DuelTargetingTest {

    private static final long LOCAL_CHARACTER_ID = 7L;
    private static final long OPPONENT_ID = 42L;
    private static final String OPPONENT_NAME = "Han Lap";
    private static final float FIXED_DT = 1f / 60f;

    /** Nhân vật khác đang thấy trên màn hình — vai {@code RemoteCharacterPresenter.characters()}. */
    private final Map<Long, PickablePoint> visible = new HashMap<>();

    /** Mọi {@code SetTargetCommand} lẽ ra đã gửi lên server, theo thứ tự. */
    private final List<Long> sentTargets = new ArrayList<>();

    private CombatHudController combat;
    private DuelTargeting targeting;

    @BeforeEach
    void setUp() {
        combat = new CombatHudController(LOCAL_CHARACTER_ID, Faction.FACTION_HOA);
        combat.bindCharacterPositions(visible);
        targeting = new DuelTargeting(combat, visible::containsKey, sentTargets::add, LOCAL_CHARACTER_ID);
    }

    /** Vai {@code WorldScreen}: 308 dọn sạch thế giới cũ rồi mới báo cho luồng đấu. */
    private void worldChanged(WorldKind kind) {
        visible.clear();
        combat.onWorldChanged(100, 100);
        targeting.onWorldChanged(kind);
    }

    private void opponentSpawned(float x) {
        visible.put(OPPONENT_ID, new Point(x, 0f));
        targeting.onCharacterSpawned(OPPONENT_ID);
    }

    private record Point(float x, float y) implements PickablePoint {}

    @Test
    @DisplayName("Dòng 32(b): trong trận, click đối thủ là nhắm lại họ; ở world chính click không bị nuốt")
    void clickingTheOpponentInsideTheArena_retargetsThem() {
        assertThat(targeting.onCharacterClicked(OPPONENT_ID))
                .as("world chính: để luồng thách đấu xử lý")
                .isFalse();
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        opponentSpawned(4f);
        combat.clearCharacterTarget(OPPONENT_ID);

        boolean handled = targeting.onCharacterClicked(OPPONENT_ID);

        assertThat(handled).isTrue();
        assertThat(sentTargets).containsExactly(OPPONENT_ID, OPPONENT_ID);
        assertThat(targeting.onCharacterClicked(OPPONENT_ID))
                .as("đang nhắm sẵn: không gửi gói thừa nhưng vẫn không mở thách đấu")
                .isTrue();
        assertThat(sentTargets).hasSize(2);
        assertThat(targeting.onCharacterClicked(0L)).as("click chỗ trống").isFalse();
    }

    @Test
    @DisplayName("Dòng 67(b): DuelStartedEvent báo đối thủ là chính mình ⇒ bỏ qua, không nhắm ai")
    void duelStartedAgainstOneself_isIgnored() {
        targeting.onDuelStarted(LOCAL_CHARACTER_ID, "Me");
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        visible.put(LOCAL_CHARACTER_ID, new Point(1f, 0f));
        targeting.onCharacterSpawned(LOCAL_CHARACTER_ID);

        assertThat(sentTargets).isEmpty();

        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        opponentSpawned(4f);
        assertThat(sentTargets).as("gói đúng tới sau vẫn được nhắm").containsExactly(OPPONENT_ID);
    }

    @Test
    @DisplayName("Vào Arena, bấm Đánh ⇒ lệnh Attack đi kèm mục tiêu server-side là ĐỐI THỦ")
    void enteringArena_thenPressingAttack_attacksTheOpponent() {
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        opponentSpawned(4f);

        // Server lấy mục tiêu của AttackCommand từ TargetComponent (AttackCommand không có field):
        // "Attack với id đối thủ" = SetTargetCommand(đối thủ) đã đi, và lệnh Attack thật sự được gửi.
        assertThat(sentTargets).containsExactly(OPPONENT_ID);
        assertThat(combat.currentTargetId()).isEqualTo(OPPONENT_ID);

        AttackInputController attack = new AttackInputController(LOCAL_CHARACTER_ID);
        attack.onButtonClicked();
        assertThat(attack.update(false, FIXED_DT, combat.currentTargetId()))
                .isEqualTo(AttackInputController.Send.INTENT);
        // LocalIntentSender báo "không có mục tiêu" và KHÔNG gửi khi targetPoint() rỗng — đúng bug cũ.
        assertThat(combat.targetPoint()).isPresent();
        assertThat(combat.targetPoint().orElseThrow().x()).isEqualTo(4f);
        // Nút skill: điều kiện cục bộ duy nhất còn thiếu trước đây là mục tiêu.
        assertThat(combat.canUseSkill()).isTrue();
    }

    @Test
    @DisplayName("308 + spawn đối thủ tới TRƯỚC DuelStartedEvent ⇒ vẫn nhắm đúng một lần")
    void duelStartedArrivingLast_stillTargetsOnce() {
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        opponentSpawned(4f);
        assertThat(sentTargets).isEmpty();

        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);

        assertThat(sentTargets).containsExactly(OPPONENT_ID);
        assertThat(combat.hasCharacterTarget()).isTrue();
        assertThat(combat.characterTargetName()).isEqualTo(OPPONENT_NAME);
    }

    @Test
    @DisplayName("Thấy đối thủ ở world CHÍNH lúc vừa nhận trận ⇒ chưa nhắm (entity ở đấu trường chưa có)")
    void opponentSeenInMainWorld_isNotTargetedYet() {
        visible.put(OPPONENT_ID, new Point(1f, 0f));
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        targeting.onCharacterSpawned(OPPONENT_ID);

        assertThat(sentTargets).isEmpty();
        assertThat(combat.currentTargetId()).isZero();
    }

    @Test
    @DisplayName("Người lạ spawn trong đấu trường không bị nhắm — chỉ đúng đối thủ server đã báo")
    void strangerIsNeverTargeted() {
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        visible.put(99L, new Point(2f, 0f));
        targeting.onCharacterSpawned(99L);

        assertThat(sentTargets).isEmpty();
    }

    @Test
    @DisplayName("Hết trận ⇒ bỏ mục tiêu; spawn lại sau đó không nhắm nữa")
    void duelEnded_dropsTarget() {
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        opponentSpawned(4f);

        targeting.onDuelEnded();

        assertThat(combat.currentTargetId()).isZero();
        assertThat(combat.hasCharacterTarget()).isFalse();
        opponentSpawned(4f);
        assertThat(sentTargets).containsExactly(OPPONENT_ID);
    }

    @Test
    @DisplayName("Rời Arena (308 MAIN) ⇒ mục tiêu mất; đối thủ hiện ở world chính không bị nhắm lại")
    void leavingArena_dropsTarget() {
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        opponentSpawned(4f);

        worldChanged(WorldKind.WORLD_KIND_MAIN);
        opponentSpawned(4f);

        assertThat(combat.currentTargetId()).isZero();
        assertThat(sentTargets).containsExactly(OPPONENT_ID);
    }

    @Test
    @DisplayName("Đối thủ ra khỏi tầm nhìn ⇒ bỏ mục tiêu; quay lại ⇒ nhắm và báo server lần nữa")
    void opponentDespawnAndRespawn_retargets() {
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        opponentSpawned(4f);

        visible.remove(OPPONENT_ID);
        combat.onCharacterDespawned(OPPONENT_ID);
        assertThat(combat.currentTargetId()).isZero();

        opponentSpawned(5f);
        assertThat(sentTargets).containsExactly(OPPONENT_ID, OPPONENT_ID);
        assertThat(combat.currentTargetId()).isEqualTo(OPPONENT_ID);
    }

    @Test
    @DisplayName("Máu đối thủ: chưa biết ⇒ UNKNOWN; đòn đánh cho máu hiện tại; HealthChanged cho máu tối đa")
    void opponentHealth_comesOnlyFromServerPackets() {
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        opponentSpawned(4f);

        assertThat(combat.characterTargetHp()).isEqualTo(CombatHudController.UNKNOWN_HP);
        assertThat(combat.characterTargetMaxHp()).isEqualTo(CombatHudController.UNKNOWN_HP);

        combat.onAttackResult(AttackResultEvent.newBuilder()
                .setAttackerEntityId(LOCAL_CHARACTER_ID)
                .setTargetEntityId(OPPONENT_ID)
                .setDamage(12)
                .setTargetCurrentHp(88)
                .build());
        assertThat(combat.characterTargetHp()).isEqualTo(88);
        assertThat(combat.characterTargetMaxHp()).isEqualTo(CombatHudController.UNKNOWN_HP);

        combat.onHealthChanged(HealthChangedEvent.newBuilder()
                .setEntityId(OPPONENT_ID)
                .setCurrentHp(91)
                .setMaxHp(130)
                .build());
        assertThat(combat.characterTargetHp()).isEqualTo(91);
        assertThat(combat.characterTargetMaxHp()).isEqualTo(130);

        combat.onAttackResult(AttackResultEvent.newBuilder()
                .setAttackerEntityId(LOCAL_CHARACTER_ID)
                .setTargetEntityId(OPPONENT_ID)
                .setDamage(91)
                .setTargetCurrentHp(0)
                .setTargetDied(true)
                .build());
        assertThat(combat.characterTargetHp()).isZero();
        // Máu của MÌNH không bị đụng bởi gói nhắm vào người khác.
        assertThat(combat.playerHp()).isEqualTo(100);
    }

    @Test
    @DisplayName("Click quái ghi đè mục tiêu nhân vật — tên/máu đối thủ không còn hiện")
    void selectingMonster_replacesCharacterTarget() {
        targeting.onDuelStarted(OPPONENT_ID, OPPONENT_NAME);
        worldChanged(WorldKind.WORLD_KIND_ARENA);
        opponentSpawned(4f);
        combat.onMonsterSpawned(dev.kage.protocol.MonsterSpawnedEvent.newBuilder()
                .setMonsterId(-1L)
                .setMonsterCode("training_dummy")
                .setCurrentHp(50)
                .setMaxHp(50)
                .build());

        assertThat(combat.selectTarget(-1L)).isTrue();

        assertThat(combat.hasCharacterTarget()).isFalse();
        assertThat(combat.targetState()).isNotNull();
    }

    @Test
    @DisplayName("Nhắm lại đúng người đang nhắm ⇒ không gửi gói thừa")
    void sameTargetTwice_sendsOnce() {
        assertThat(combat.selectCharacterTarget(OPPONENT_ID, OPPONENT_NAME)).isFalse(); // chưa thấy
        visible.put(OPPONENT_ID, new Point(1f, 0f));
        assertThat(combat.selectCharacterTarget(OPPONENT_ID, OPPONENT_NAME)).isTrue();
        assertThat(combat.selectCharacterTarget(OPPONENT_ID, OPPONENT_NAME)).isFalse();
        assertThat(combat.selectCharacterTarget(LOCAL_CHARACTER_ID, "me")).isFalse();
    }
}
