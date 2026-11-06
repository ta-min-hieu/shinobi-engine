package dev.kage.guild.api;

import java.time.Instant;
import java.util.Optional;

import dev.kage.guild.domain.GuildNoticePort;

/**
 * Thông báo bang (ADR-179) — chuẩn hoá (một dòng, gộp khoảng trắng) và giới hạn {@link #MAX_LENGTH} ký tự; quyền bang
 * chủ do adapter kiểm trong DB. Bộ lọc từ cấm (thuộc feature-chat) do gateway áp. Chạm DB — chỉ gọi trên Virtual
 * Thread.
 */
public final class GuildNoticeFacade {

    /** Độ dài tối đa (code point). */
    public static final int MAX_LENGTH = 100;

    /** Composition không có thông báo bang (test). */
    public static final GuildNoticeFacade DISABLED = new GuildNoticeFacade(new GuildNoticePort() {
        @Override
        public Optional<String> noticeOf(long characterId) {
            return Optional.empty();
        }

        @Override
        public Outcome set(long characterId, String notice, Instant at) {
            return Outcome.NOT_IN_GUILD;
        }
    });

    private final GuildNoticePort port;

    /** Dựng ở composition root. */
    public GuildNoticeFacade(GuildNoticePort port) {
        this.port = port;
    }

    /** Chuẩn hoá: ký tự điều khiển thành khoảng trắng, gộp khoảng trắng; rỗng nếu dài quá {@link #MAX_LENGTH}. */
    public static Optional<String> normalize(String raw) {
        String cleaned = raw.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s+", " ");
        return cleaned.codePointCount(0, cleaned.length()) > MAX_LENGTH ? Optional.empty() : Optional.of(cleaned);
    }

    /** Thông báo bang của {@code characterId} (rỗng nếu không ở bang nào). */
    public Optional<String> noticeOf(long characterId) {
        return port.noticeOf(characterId);
    }

    /** Ghi thông báo ĐÃ chuẩn hoá. */
    public GuildNoticePort.Outcome set(long characterId, String normalizedNotice, Instant at) {
        return port.set(characterId, normalizedNotice, at);
    }
}
