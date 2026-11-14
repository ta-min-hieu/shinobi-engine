package dev.kage.character.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.kage.character.domain.ProfileVisitPort;

/**
 * API "ai ghé hồ sơ mình" (lô 54): người đã thích (ADR-360), lịch sử bị soi (ADR-361), chế độ chỉ bạn bè được soi
 * (ADR-362). Chạm DB — Virtual Thread.
 */
public final class ProfileVisitFacade {

    /** Số dòng trả cho người chơi. */
    public static final int SHOWN = 10;

    /** Số lần bị soi giữ lại mỗi người. */
    public static final int KEPT = 20;

    /** Không lưu gì (composition test). */
    public static final ProfileVisitFacade DISABLED = new ProfileVisitFacade(new ProfileVisitPort() {
        @Override
        public List<Liker> recentLikers(long targetId, int limit) {
            return List.of();
        }

        @Override
        public void recordInspect(long targetId, long viewerId, Instant at, int keep) {
            // không lưu
        }

        @Override
        public List<Visit> inspectHistory(long targetId, int limit) {
            return List.of();
        }

        @Override
        public boolean friendsOnly(long characterId) {
            return false;
        }

        @Override
        public void setFriendsOnly(long characterId, boolean enabled) {
            // không lưu
        }
    });

    private final ProfileVisitPort port;

    /** Dựng ở composition root. */
    public ProfileVisitFacade(ProfileVisitPort port) {
        this.port = port;
    }

    /** ADR-360 — người đã thích mình gần nhất. */
    public List<ProfileVisitPort.Liker> recentLikers(long targetId) {
        return port.recentLikers(targetId, SHOWN);
    }

    /** ADR-361 — ghi một lần bị soi. */
    public void recordInspect(long targetId, long viewerId, Instant at) {
        port.recordInspect(targetId, viewerId, at, KEPT);
    }

    /** ADR-361 — các lần bị soi gần nhất. */
    public List<ProfileVisitPort.Visit> inspectHistory(long targetId) {
        return port.inspectHistory(targetId, SHOWN);
    }

    /** ADR-362 — chỉ bạn bè được soi. */
    public boolean friendsOnly(long characterId) {
        return port.friendsOnly(characterId);
    }

    /** ADR-362 — bật / tắt. */
    public void setFriendsOnly(long characterId, boolean enabled) {
        port.setFriendsOnly(characterId, enabled);
    }

    /** Ngày (UTC) của một lượt thích, đổi sang số ngày từ 1970-01-01 cho giao thức. Tĩnh để test. */
    public static long epochDay(LocalDate day) {
        return day.toEpochDay();
    }
}
