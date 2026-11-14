package dev.kage.character.api;

import java.time.Clock;
import java.time.LocalDate;

import dev.kage.character.domain.ProfileLikePort;

/** API thích hồ sơ (ADR-298): mỗi ngày (UTC) thích một người tối đa một lần; không tự thích mình. */
public final class ProfileLikeFacade {

    /** ADR-305 — số dòng bảng xếp hạng. */
    public static final int TOP = 10;

    /** ADR-384 — số ngày của "tuần" xếp hạng (trượt, gồm hôm nay). */
    public static final int WEEK_DAYS = 7;

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

                @Override
                public java.util.List<Ranked> top(int limit) {
                    return java.util.List.of();
                }

                @Override
                public java.util.List<Ranked> topSince(LocalDate from, int limit) {
                    return java.util.List.of();
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

    /** ADR-305 — top {@value #TOP} người được thích nhiều nhất. */
    public java.util.List<ProfileLikePort.Ranked> top() {
        return port.top(TOP);
    }

    /** ADR-384 — top {@value #TOP} theo lượt thích {@value #WEEK_DAYS} ngày gần nhất (gồm hôm nay). */
    public java.util.List<ProfileLikePort.Ranked> topThisWeek() {
        return port.topSince(LocalDate.now(clock).minusDays(WEEK_DAYS - 1L), TOP);
    }

    /** Tổng lượt thích. */
    public int likesOf(long targetId) {
        return port.likesOf(targetId);
    }
}
