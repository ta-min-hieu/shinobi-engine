package dev.kage.character.api;

import java.time.Clock;
import java.time.LocalDate;

import dev.kage.character.domain.ProfileLikePort;

/** API thích hồ sơ (ADR-298): mỗi ngày (UTC) thích một người tối đa một lần; không tự thích mình. */
public final class ProfileLikeFacade {

    /** Kết quả thích. */
    public enum Outcome {
        OK,
        ALREADY_TODAY,
        SELF
    }

    /** Không lưu gì (composition test). */
    public static final ProfileLikeFacade DISABLED = new ProfileLikeFacade(
            new ProfileLikePort() {
                @Override
                public boolean like(long likerId, long targetId, LocalDate day) {
                    return false;
                }

                @Override
                public int likesOf(long targetId) {
                    return 0;
                }
            },
            Clock.systemUTC());

    private final ProfileLikePort port;
    private final Clock clock;

    /** Dựng ở composition root. */
    public ProfileLikeFacade(ProfileLikePort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    /** {@code likerId} thích hồ sơ {@code targetId}. */
    public Outcome like(long likerId, long targetId) {
        if (likerId == targetId) {
            return Outcome.SELF;
        }
        return port.like(likerId, targetId, LocalDate.now(clock)) ? Outcome.OK : Outcome.ALREADY_TODAY;
    }

    /** Tổng lượt thích. */
    public int likesOf(long targetId) {
        return port.likesOf(targetId);
    }
}
