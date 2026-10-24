package dev.kage.party.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bảng tìm nhóm CHỈ trong RAM (ADR-082 D1): mỗi trưởng nhóm tối đa một tin, tin sống {@link #LISTING_LIFETIME}.
 * Khởi động lại server là mất hết — tin tìm nhóm không phải tài sản. An toàn đa luồng (gọi từ Virtual Thread).
 */
public final class PartyFinderBoard {

    /** Tin tự hết hạn sau chừng này. */
    public static final Duration LISTING_LIFETIME = Duration.ofMinutes(10);

    /** Cấp tối thiểu hợp lệ của một tin (cấp nhân vật 1–100). */
    public static final int MAX_LEVEL = 100;

    private final Map<Long, PartyFinderListing> byLeader = new ConcurrentHashMap<>();
    private final Clock clock;

    public PartyFinderBoard(Clock clock) {
        this.clock = clock;
    }

    /** Đăng (hoặc thay) tin của {@code leaderId}; hạn tính lại từ bây giờ. */
    public PartyFinderListing post(long leaderId, String leaderName, PartyGoal goal, int minLevel) {
        PartyFinderListing listing = new PartyFinderListing(
                leaderId,
                leaderName,
                goal,
                Math.clamp(minLevel, 1, MAX_LEVEL),
                clock.instant().plus(LISTING_LIFETIME));
        byLeader.put(leaderId, listing);
        return listing;
    }

    /** Gỡ tin của {@code leaderId}; {@code false} nếu không có tin nào. */
    public boolean remove(long leaderId) {
        return byLeader.remove(leaderId) != null;
    }

    /** Tin còn hạn của {@code leaderId}, dọn luôn nếu đã hết hạn. */
    public Optional<PartyFinderListing> find(long leaderId) {
        PartyFinderListing listing = byLeader.get(leaderId);
        if (listing == null) {
            return Optional.empty();
        }
        if (!listing.expiresAt().isAfter(clock.instant())) {
            byLeader.remove(leaderId, listing);
            return Optional.empty();
        }
        return Optional.of(listing);
    }

    /** Mọi tin còn hạn, mới nhất trước; tin hết hạn bị dọn trong lúc đọc. */
    public List<PartyFinderListing> active() {
        Instant now = clock.instant();
        byLeader.values().removeIf(listing -> !listing.expiresAt().isAfter(now));
        List<PartyFinderListing> listings = new ArrayList<>(byLeader.values());
        listings.sort(Comparator.comparing(PartyFinderListing::expiresAt).reversed());
        return listings;
    }
}
