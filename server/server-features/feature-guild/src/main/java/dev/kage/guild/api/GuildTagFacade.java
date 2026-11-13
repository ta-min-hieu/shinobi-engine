package dev.kage.guild.api;

import java.util.Optional;

import dev.kage.guild.domain.GuildTagPort;

/**
 * API ký hiệu bang (ADR-344): {@value #MIN_LENGTH}–{@value #MAX_LENGTH} chữ cái/chữ số, không trùng bang khác; hiện
 * trước tên thành viên trong chat thế giới và khi soi trang bị. Chạm DB — Virtual Thread.
 */
public final class GuildTagFacade {

    /** Độ dài ngắn nhất. */
    public static final int MIN_LENGTH = 2;

    /** Độ dài dài nhất. */
    public static final int MAX_LENGTH = 4;

    /** Không lưu gì (composition test). */
    public static final GuildTagFacade DISABLED = new GuildTagFacade(new GuildTagPort() {
        @Override
        public Optional<String> tagOf(long guildId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> tagOfCharacter(long characterId) {
            return Optional.empty();
        }

        @Override
        public boolean setTag(long guildId, String tag) {
            return false;
        }
    });

    private final GuildTagPort port;

    /** Dựng ở composition root. */
    public GuildTagFacade(GuildTagPort port) {
        this.port = port;
    }

    /** Ký hiệu hợp lệ: đúng độ dài, chỉ chữ cái/chữ số (kể cả có dấu). Tĩnh để test. */
    public static boolean valid(String tag) {
        return tag.length() >= MIN_LENGTH
                && tag.length() <= MAX_LENGTH
                && tag.codePoints().allMatch(Character::isLetterOrDigit);
    }

    /** Ký hiệu của bang ("" nếu chưa đặt). */
    public String tagOf(long guildId) {
        return port.tagOf(guildId).orElse("");
    }

    /** Ký hiệu bang của nhân vật ("" nếu không có). */
    public String tagOfCharacter(long characterId) {
        return port.tagOfCharacter(characterId).orElse("");
    }

    /** Đặt (người gọi đã kiểm quyền và {@link #valid}); rỗng = xoá; {@code false} nếu trùng. */
    public boolean setTag(long guildId, String tag) {
        return port.setTag(guildId, tag.isEmpty() ? null : tag);
    }
}
