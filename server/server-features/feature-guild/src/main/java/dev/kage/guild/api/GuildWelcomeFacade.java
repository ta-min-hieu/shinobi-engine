package dev.kage.guild.api;

import dev.kage.guild.domain.GuildWelcomePort;

/** API lời chào thành viên mới (ADR-313). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GuildWelcomeFacade {

    /** Dài nhất (khớp {@code VARCHAR(200)}). */
    public static final int MAX_LENGTH = 200;

    /** Không có lời chào (composition test). */
    public static final GuildWelcomeFacade DISABLED = new GuildWelcomeFacade(new GuildWelcomePort() {
        @Override
        public String welcomeOf(long guildId) {
            return "";
        }

        @Override
        public void setWelcome(long guildId, String message) {
            // không lưu gì
        }
    });

    private final GuildWelcomePort port;

    /** Dựng ở composition root. */
    public GuildWelcomeFacade(GuildWelcomePort port) {
        this.port = port;
    }

    /** Lời chào hiện tại. */
    public String welcomeOf(long guildId) {
        return port.welcomeOf(guildId);
    }

    /** Đặt lời chào (người gọi đã kiểm quyền + nội dung). */
    public void setWelcome(long guildId, String message) {
        port.setWelcome(guildId, message);
    }
}
