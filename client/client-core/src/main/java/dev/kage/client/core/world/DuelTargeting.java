package dev.kage.client.core.world;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.WorldKind;

/**
 * Tự nhắm ĐỐI THỦ đấu trường làm mục tiêu — dòng 32 bảng chờ, phương án (a), thi hành 2026-10-04.
 *
 * <p><b>Vì sao cần.</b> Client chỉ từng đặt mục tiêu là QUÁI (tự chọn lúc quái spawn, hoặc click
 * quái); click vào người chơi khác là CHỌN để thách đấu ({@link DuelCoordinator#onCharacterPicked}).
 * Đấu trường không có quái ⇒ không bao giờ có mục tiêu ⇒ bấm Đánh báo "chưa chọn mục tiêu", nút
 * skill xám — vào được trận mà không đánh được ai. Phương án (b) — click đối thủ để nhắm lại khi đang
 * trong trận — thêm 2026-10-18 (lô 16, chủ dự án duyệt): {@link #onCharacterClicked}.
 *
 * <p><b>Không đổi protocol, không quyết gameplay.</b> Lớp này chỉ gửi {@code SetTargetCommand} với
 * đúng id mà server đã báo ở {@code DuelStartedEvent.opponent_character_id}; server tự kiểm lại
 * ({@code WorldFacade#handleSetTarget} bỏ qua id không tồn tại trong World của người gửi;
 * {@code CombatSystem} chỉ cho PvP ở World đấu trường).
 *
 * <p><b>Ba điều kiện, thứ tự tới KHÔNG cố định.</b> Chỉ nhắm khi đủ cả ba: đang có trận
 * ({@code DuelStartedEvent}), đang ở World đấu trường ({@code WorldChangedEvent} ARENA) và đối thủ
 * đang có trên màn hình (spawn của World đấu trường). Phía server {@code DuelStartedEvent} gửi ngay
 * sau {@code acceptDuel}, còn 308 + attach chạy trên executor riêng SAU khi world chính xả hàng đợi
 * ({@code ArenaInstanceRegistry#moveCharacterIntoDuel}) — gói nào tới trước tuỳ nhịp. Nhắm sớm hơn
 * (thấy đối thủ ở world CHÍNH lúc vừa nhận trận) là gửi một {@code SetTargetCommand} mà server bỏ
 * qua im lặng (entity ở đấu trường chưa tồn tại), rồi 308 xoá luôn mục tiêu phía client — nên chờ.
 * Spawn của đối thủ trong World đấu trường cũng là bằng chứng entity của họ ĐÃ attach phía server,
 * tức lệnh nhắm sẽ không rơi vào hư không.
 *
 * <p>Thuần, không GL, không mạng — gửi qua {@link LongConsumer} để test không cần kết nối thật.
 */
final class DuelTargeting implements DuelCoordinator.DuelListener {

    private static final Logger LOG = LoggerFactory.getLogger(DuelTargeting.class);

    private static final long NO_OPPONENT = 0L;

    /** Id thật của chính người chơi — để nhận ra một {@code DuelStartedEvent} báo đối thủ là mình. */
    private final long localCharacterId;

    private final CombatHudController combat;
    private final LongPredicate characterVisible;
    private final LongConsumer setTargetSender;

    /** Đối thủ server đã báo ở {@code DuelStartedEvent}; {@link #NO_OPPONENT} khi không có trận. */
    private long opponentId = NO_OPPONENT;

    private String opponentDisplayName = "";

    /** Đang ở World đấu trường theo gói 308 gần nhất. Vào world lần đầu luôn là world chính. */
    private boolean inArena;

    /** Bang mình đang trong giờ bang chiến (ADR-102) — mặc định không bao giờ. */
    private BooleanSupplier guildWarActive = () -> false;

    /** Nhãn mục tiêu khi nhắm người chơi trong bang chiến — server không gửi tên trong gói spawn. */
    private String guildWarTargetName = "";

    /**
     * @param combat nơi giữ mục tiêu hiện tại của HUD
     * @param characterVisible nhân vật đó có đang trên màn hình không ({@code RemoteCharacterPresenter#isPresent})
     * @param setTargetSender gửi {@code SetTargetCommand} ({@code WorldCommandSender#setTarget})
     * @param localCharacterId id thật của chính người chơi
     */
    DuelTargeting(
            CombatHudController combat,
            LongPredicate characterVisible,
            LongConsumer setTargetSender,
            long localCharacterId) {
        this.localCharacterId = localCharacterId;
        this.combat = combat;
        this.characterVisible = characterVisible;
        this.setTargetSender = setTargetSender;
    }

    /** {@code DuelStartedEvent} — server vừa nói đối thủ là ai. */
    @Override
    public void onDuelStarted(long opponentCharacterId, String opponentDisplayName) {
        if (opponentCharacterId == localCharacterId) {
            // Server không bao giờ được ghép một người với chính họ — gói như vậy là lỗi phía server.
            // Trước 2026-10-17 (dòng 67(b)) nó bị bỏ lặng lẽ: không nhắm ai, không dấu vết gì để lần ra.
            LOG.warn(
                    "DuelStartedEvent báo đối thủ là chính mình (characterId={}) — bỏ qua, không nhắm",
                    opponentCharacterId);
            return;
        }
        this.opponentId = opponentCharacterId;
        this.opponentDisplayName = opponentDisplayName;
        targetOpponentIfReady();
    }

    /**
     * {@code WorldChangedEvent} — gọi SAU khi {@code WorldScreen} đã dọn thực thể của World cũ (mục
     * tiêu cũ đã mất cùng {@link CombatHudController#onWorldChanged}).
     */
    void onWorldChanged(WorldKind kind) {
        inArena = kind == WorldKind.WORLD_KIND_ARENA;
        targetOpponentIfReady();
    }

    /** {@code CharacterSpawnedEvent} — nhân vật khác vừa vào tầm nhìn. */
    void onCharacterSpawned(long characterId) {
        if (characterId == opponentId) {
            targetOpponentIfReady();
        }
    }

    /**
     * Click vào một nhân vật (dòng 32(b)). Trong World đấu trường, click vào ĐỐI THỦ là nhắm lại họ —
     * không phải mở lời thách đấu như ở world chính (đang đấu thì thách ai cũng vô nghĩa).
     *
     * @return {@code true} nếu click đã được xử lý ở đây (đang ở đấu trường và trúng một nhân vật) —
     *     caller không chuyển tiếp sang luồng thách đấu
     */
    boolean onCharacterClicked(long characterId) {
        if (characterId == NO_OPPONENT) {
            return false;
        }
        if (!inArena) {
            return onCharacterClickedInGuildWar(characterId);
        }
        if (characterId == opponentId && combat.selectCharacterTarget(opponentId, opponentDisplayName)) {
            setTargetSender.accept(opponentId);
        }
        return true;
    }

    /**
     * Nối bang chiến (ADR-102 D6): trong giờ chiến, click một người chơi ở world chính là NHẮM họ — server mới là nơi
     * quyết họ có phải người bang đối địch và có đứng ngoài vùng an toàn không.
     */
    void useGuildWar(BooleanSupplier active, String targetName) {
        this.guildWarActive = active;
        this.guildWarTargetName = targetName;
    }

    private boolean onCharacterClickedInGuildWar(long characterId) {
        if (!guildWarActive.getAsBoolean()) {
            return false;
        }
        if (combat.selectCharacterTarget(characterId, guildWarTargetName)) {
            setTargetSender.accept(characterId);
        }
        return true;
    }

    /** {@code DuelResultEvent} — trận đã chốt, thôi nhắm đối thủ cũ. */
    @Override
    public void onDuelEnded() {
        combat.clearCharacterTarget(opponentId);
        opponentId = NO_OPPONENT;
        opponentDisplayName = "";
    }

    /** Rời màn hình/mất kết nối — trạng thái trận không sống sang phiên sau. */
    @Override
    public void onReset() {
        onDuelEnded();
        inArena = false;
    }

    private void targetOpponentIfReady() {
        if (opponentId == NO_OPPONENT || !inArena || !characterVisible.test(opponentId)) {
            return;
        }
        if (combat.selectCharacterTarget(opponentId, opponentDisplayName)) {
            setTargetSender.accept(opponentId);
        }
    }
}
