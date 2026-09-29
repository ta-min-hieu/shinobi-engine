package dev.kage.client.core.world;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.SelectCharacterResponse;

/**
 * Vòng đời "tự đăng nhập lại" của MỘT màn world (quyết định chủ dự án 2026-09-29): khi nào bắt đầu
 * một {@link SessionRecovery}, đưa nhịp cho nó, và báo kết quả ra hai lối — vào lại được, hoặc bỏ
 * cuộc kèm lý do.
 *
 * <p>Tách khỏi {@code WorldScreen} vì hai lẽ: lớp kia đã chạm trần độ phức tạp PMD tính cho cả lớp,
 * và phần này không cần GL nên đứng riêng thì đọc/nghĩ về nó dễ hơn. Cỗ máy bắt tay thật nằm ở
 * {@link SessionRecovery}; lớp này chỉ là phần nối.
 */
final class AutoRelog {

    private static final Logger LOG = LoggerFactory.getLogger(AutoRelog.class);

    private final SessionRecovery.Link link;
    private final long characterId;

    /** {@link SessionRecovery.Link#connectionGeneration()} lúc phiên này vào world. */
    private final int sessionGeneration;

    /** {@code null} = chưa bật, tức hành vi cũ: chỉ báo đứt/nối lại, không tự vào lại. */
    private String username;

    private BiConsumer<CharacterSummary, SelectCharacterResponse> onResumed = (character, enterWorld) -> {};
    private Consumer<String> onFailed = errorKey -> {};

    /** {@code null} khi phiên còn sống. */
    private SessionRecovery recovery;

    /**
     * @param link đường truyền của màn world — đọc thế hệ kết nối NGAY lúc dựng, tức lúc phiên vào world
     * @param characterId nhân vật phải chọn lại
     */
    AutoRelog(SessionRecovery.Link link, long characterId) {
        this.link = link;
        this.characterId = characterId;
        this.sessionGeneration = link.connectionGeneration();
    }

    /**
     * Bật tự vào lại.
     *
     * @param onResumed vào lại được — nhận nhân vật bản mới nhất và gói vào world
     * @param onFailed bỏ cuộc — nhận khoá i18n nói lý do
     */
    void enable(
            String username,
            BiConsumer<CharacterSummary, SelectCharacterResponse> onResumed,
            Consumer<String> onFailed) {
        this.username = username;
        this.onResumed = onResumed;
        this.onFailed = onFailed;
    }

    /** Đang tự vào lại — thế giới đứng im, gói là của cỗ máy đăng nhập lại. */
    boolean isRecovering() {
        return recovery != null;
    }

    /**
     * Gọi SAU khi đã xử lý hết gói của nhịp này và phiên KHÔNG bị server kết thúc (xem
     * {@link SessionRecovery#shouldStart} — lời đá luôn nằm trong hàng đợi trước khi socket đứt).
     *
     * @return {@code true} nếu vừa bắt đầu tự vào lại
     */
    boolean startIfSessionLost() {
        if (username == null
                || !SessionRecovery.shouldStart(
                        null, sessionGeneration, link.connectionGeneration(), link.isConnected())) {
            return false;
        }
        LOG.info("Mất phiên do đứt mạng — tự đăng nhập lại nhân vật {}", characterId);
        recovery = new SessionRecovery(link, username, characterId);
        return true;
    }

    /** Một nhịp tự vào lại; ra kết quả thì gọi đúng một trong hai lối đã cài ở {@link #enable}. */
    void tick(float deltaSeconds) {
        SessionRecovery.Phase phase = recovery.tick(deltaSeconds);
        if (phase == SessionRecovery.Phase.RESUMED) {
            recovery.resumed().ifPresent(resumed -> onResumed.accept(resumed.character(), resumed.enterWorld()));
        } else if (phase == SessionRecovery.Phase.FAILED) {
            onFailed.accept(recovery.failureKey());
        }
    }
}
