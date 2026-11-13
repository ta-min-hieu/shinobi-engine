package dev.kage.chat.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.chat.domain.BulletinPort;

/**
 * API bảng rao vặt (ADR-336): mỗi nhân vật một tin ≤ {@value #MAX_LENGTH} ký tự, sống {@link #LIFETIME}; đăng lại phải
 * cách {@link #COOLDOWN}. Người gọi đã kiểm nội dung. Chạm DB — Virtual Thread.
 */
public final class BulletinFacade {

    /** Độ dài tối đa của tin. */
    public static final int MAX_LENGTH = 100;

    /** Số tin trả mỗi lần xem. */
    public static final int SHOWN = 20;

    /** Tin sống bao lâu. */
    public static final Duration LIFETIME = Duration.ofHours(24);

    /** Khoảng cách tối thiểu giữa hai lần đăng của một người. */
    public static final Duration COOLDOWN = Duration.ofMinutes(1);

    /** Không lưu gì (composition test). */
    public static final BulletinFacade DISABLED = new BulletinFacade(new BulletinPort() {
        @Override
        public Optional<Instant> postedAt(long characterId) {
            return Optional.empty();
        }

        @Override
        public void post(long characterId, String body, Instant at) {
            // không lưu
        }

        @Override
        public boolean remove(long characterId) {
            return false;
        }

        @Override
        public List<Post> since(Instant since, int limit) {
            return List.of();
        }
    });

    /** Kết quả đăng. */
    public enum PostResult {
        OK,
        TOO_SOON
    }

    private final BulletinPort port;

    /** Dựng ở composition root. */
    public BulletinFacade(BulletinPort port) {
        this.port = port;
    }

    /** Đăng tin (đã kiểm nội dung); quá nhanh sau lần trước ⇒ {@link PostResult#TOO_SOON}. */
    public PostResult post(long characterId, String body, Instant now) {
        Optional<Instant> last = port.postedAt(characterId);
        if (last.isPresent() && last.get().plus(COOLDOWN).isAfter(now)) {
            return PostResult.TOO_SOON;
        }
        port.post(characterId, body, now);
        return PostResult.OK;
    }

    /** Gỡ tin của mình. */
    public boolean remove(long characterId) {
        return port.remove(characterId);
    }

    /** Tin còn hạn, mới trước. */
    public List<BulletinPort.Post> active(Instant now) {
        return port.since(now.minus(LIFETIME), SHOWN);
    }
}
