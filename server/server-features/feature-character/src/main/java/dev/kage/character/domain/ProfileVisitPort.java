package dev.kage.character.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Ai đã thích mình (ADR-360), lịch sử bị soi (ADR-361), chỉ bạn bè được soi (ADR-362). */
public interface ProfileVisitPort {

    /** Một người đã thích mình và ngày thích gần nhất. */
    record Liker(String name, LocalDate lastLikedOn) {}

    /** Một lần bị soi. */
    record Visit(String viewerName, Instant at) {}

    /** Những người đã thích {@code targetId}, ngày gần nhất trước. */
    List<Liker> recentLikers(long targetId, int limit);

    /** Ghi một lần {@code viewerId} soi {@code targetId}, chỉ giữ {@code keep} lần mới nhất của người bị soi. */
    void recordInspect(long targetId, long viewerId, Instant at, int keep);

    /** Các lần bị soi gần nhất, mới trước. */
    List<Visit> inspectHistory(long targetId, int limit);

    /** {@code true} nếu nhân vật chỉ cho bạn bè soi. */
    boolean friendsOnly(long characterId);

    /** Bật / tắt "chỉ bạn bè được soi". */
    void setFriendsOnly(long characterId, boolean enabled);
}
