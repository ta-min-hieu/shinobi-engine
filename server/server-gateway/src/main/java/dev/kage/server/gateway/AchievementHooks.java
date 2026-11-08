package dev.kage.server.gateway;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.achievement.api.AchievementFacade;
import dev.kage.achievement.domain.Achievement;
import dev.kage.achievement.domain.AchievementKind;
import dev.kage.protocol.AchievementUnlockedEvent;
import dev.kage.protocol.Opcode;

/**
 * Nơi DUY NHẤT nối sự kiện của gateway vào thành tựu (ADR-062 D4) — hạ quái, lên cấp, lĩnh thưởng quest,
 * thắng luận kiếm, điểm danh, vào/rời world — và giữ danh hiệu đang đeo trong bộ nhớ cho dòng chat (D3).
 *
 * <p>Mọi hàm chạm DB: chỉ gọi trên Virtual Thread. Ghi tiến độ là việc PHỤ: lỗi dừng ở đây (log WARN),
 * không bao giờ làm hỏng việc chính đã gọi nó (phần thưởng, lên cấp, chốt trận).
 */
final class AchievementHooks {

    /** Gửi một gói cho một nhân vật nếu đang online — {@code ConnectedCharacterRegistry#sendToCharacterIfOnline}. */
    @FunctionalInterface
    interface Sink {
        void send(long characterId, int opcode, byte[] payload);
    }

    private static final Logger LOG = LoggerFactory.getLogger(AchievementHooks.class);

    // volatile, không final: registry dựng TRƯỚC DB (nó là WorldOutboundSink của mọi World) — bind() sau.
    private volatile AchievementFacade facade;
    private volatile Sink sink;

    /** ADR-230 — báo ra ngoài các thành tựu vừa mở (khoe vào bang); mặc định không làm gì. */
    private volatile java.util.function.BiConsumer<Long, List<Achievement>> onUnlocked = (id, opened) -> {};

    /** Khoá i18n danh hiệu đang đeo của người ĐANG trong world — vắng mặt = không đeo. */
    private final Map<Long, String> activeTitleKeys = new ConcurrentHashMap<>();

    AchievementHooks(AchievementFacade facade, Sink sink) {
        this.facade = facade;
        this.sink = sink;
    }

    /** Không thành tựu nào, không gửi gì — trạng thái trước khi bootstrap {@link #bind}, và của test. */
    static AchievementHooks unbound() {
        return new AchievementHooks(AchievementFacade.DISABLED, (id, opcode, payload) -> {});
    }

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(AchievementFacade realFacade, Sink realSink) {
        this.facade = realFacade;
        this.sink = realSink;
    }

    /** ADR-230 — nối nơi nhận thành tựu vừa mở (một lần, lúc khởi động). */
    void bindUnlockListener(java.util.function.BiConsumer<Long, List<Achievement>> listener) {
        this.onUnlocked = listener;
    }

    void onMonsterKilled(long characterId) {
        count(characterId, AchievementKind.KILL_MONSTERS);
    }

    void onQuestRewardClaimed(long characterId) {
        count(characterId, AchievementKind.CLAIM_QUESTS);
    }

    void onDuelWon(long characterId) {
        count(characterId, AchievementKind.WIN_DUELS);
    }

    void onTournamentWon(long characterId) {
        count(characterId, AchievementKind.WIN_TOURNAMENTS);
    }

    void onCheckedIn(long characterId) {
        count(characterId, AchievementKind.CHECK_IN);
    }

    // PMD AvoidCatchingGenericException: việc phụ — mọi lỗi dừng ở đây (D4).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onLevelReached(long characterId, int level) {
        try {
            announce(characterId, facade.levelReached(characterId, level));
        } catch (RuntimeException e) {
            LOG.warn("Ghi thành tựu cấp lỗi: characterId={}, level={}", characterId, level, e);
        }
    }

    /** Vào world: mở bù mốc đã đạt (D4) và nạp danh hiệu đang đeo cho chat (D3). */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onEnteredWorld(long characterId, int level) {
        try {
            facade.activeTitleKey(characterId)
                    .ifPresentOrElse(
                            key -> activeTitleKeys.put(characterId, key), () -> activeTitleKeys.remove(characterId));
            announce(characterId, facade.reconcile(characterId, level));
        } catch (RuntimeException e) {
            LOG.warn("Nạp thành tựu lúc vào world lỗi: characterId={}", characterId, e);
        }
    }

    /** Rời world — bỏ danh hiệu khỏi bộ nhớ (không chạm DB, gọi được trên EventLoop). */
    void onLeftWorld(long characterId) {
        activeTitleKeys.remove(characterId);
    }

    /** Khoá i18n danh hiệu đang đeo, chuỗi rỗng nếu không — cho {@code ChatMessageEvent.sender_title_key}. */
    String titleKeyOf(long characterId) {
        return activeTitleKeys.getOrDefault(characterId, "");
    }

    /** Đeo/bỏ danh hiệu; cập nhật bộ nhớ chat ngay. */
    boolean wearTitle(long characterId, String achievementCode) {
        if (!facade.wearTitle(characterId, achievementCode)) {
            return false;
        }
        facade.activeTitleKey(characterId)
                .ifPresentOrElse(
                        key -> activeTitleKeys.put(characterId, key), () -> activeTitleKeys.remove(characterId));
        return true;
    }

    AchievementFacade facade() {
        return facade;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void count(long characterId, AchievementKind kind) {
        try {
            announce(characterId, facade.count(characterId, kind, 1L));
        } catch (RuntimeException e) {
            LOG.warn("Ghi tiến độ thành tựu lỗi: characterId={}, kind={}", characterId, kind, e);
        }
    }

    private void announce(long characterId, List<Achievement> opened) {
        if (!opened.isEmpty()) {
            onUnlocked.accept(characterId, opened); // ADR-230
        }
        for (Achievement achievement : opened) {
            sink.send(
                    characterId,
                    Opcode.OPCODE_ACHIEVEMENT_UNLOCKED_EVENT_VALUE,
                    AchievementUnlockedEvent.newBuilder()
                            .setCode(achievement.code())
                            .setNameKey(achievement.nameKey())
                            .setTitleKey(achievement.titleKey())
                            .build()
                            .toByteArray());
        }
    }
}
