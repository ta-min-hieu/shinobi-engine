package dev.kage.guild.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import dev.kage.guild.domain.GuildName;
import dev.kage.guild.domain.GuildRecruitmentPort;

/**
 * Bảng chiêu mộ bang (ADR-185) — chuẩn hoá lời chiêu mộ (một dòng, ≤ {@link #MAX_MESSAGE_LENGTH} ký tự), kẹp cấp tối
 * thiểu 1..100; quyền bang chủ / phó do adapter kiểm trong DB. Chạm DB — chỉ gọi trên Virtual Thread.
 */
public final class GuildRecruitmentFacade {

    /** Độ dài tối đa lời chiêu mộ (code point). */
    public static final int MAX_MESSAGE_LENGTH = 80;

    /** Số dòng tối đa trên bảng. */
    public static final int PAGE = 50;

    private static final int MAX_LEVEL = 100;

    /** Composition không có bảng chiêu mộ (test). */
    public static final GuildRecruitmentFacade DISABLED = new GuildRecruitmentFacade(new GuildRecruitmentPort() {
        @Override
        public Outcome set(long characterId, int minLevel, String message, Instant at) {
            return Outcome.NOT_IN_GUILD;
        }

        @Override
        public List<Listing> list(int limit) {
            return List.of();
        }

        @Override
        public OptionalInt minLevelOf(String guildName) {
            return OptionalInt.empty();
        }
    });

    private final GuildRecruitmentPort port;

    /** Dựng ở composition root. */
    public GuildRecruitmentFacade(GuildRecruitmentPort port) {
        this.port = port;
    }

    /** Lời chiêu mộ đã chuẩn hoá; rỗng nếu dài quá. */
    public static Optional<String> normalize(String raw) {
        String cleaned = raw.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s+", " ");
        return cleaned.codePointCount(0, cleaned.length()) > MAX_MESSAGE_LENGTH
                ? Optional.empty()
                : Optional.of(cleaned);
    }

    /** Treo chiêu mộ (lời ĐÃ chuẩn hoá). */
    public GuildRecruitmentPort.Outcome post(long characterId, int minLevel, String message, Instant at) {
        return port.set(characterId, Math.max(1, Math.min(MAX_LEVEL, minLevel)), message, at);
    }

    /** Gỡ chiêu mộ. */
    public GuildRecruitmentPort.Outcome remove(long characterId, Instant at) {
        return port.set(characterId, 1, null, at);
    }

    /** Bảng chiêu mộ. */
    public List<GuildRecruitmentPort.Listing> list() {
        return port.list(PAGE);
    }

    /** Cấp tối thiểu bang đang đòi (rỗng nếu không chiêu mộ). */
    public OptionalInt minLevelOf(String guildName) {
        // Tên đến thẳng từ packet — tên dị dạng (vd U+0000) không bao giờ chạm DB.
        return GuildName.isValid(guildName) ? port.minLevelOf(guildName) : OptionalInt.empty();
    }
}
