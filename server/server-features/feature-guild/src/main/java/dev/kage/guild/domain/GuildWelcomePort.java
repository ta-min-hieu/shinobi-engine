package dev.kage.guild.domain;

/** Lời chào thành viên mới (ADR-313) — cột {@code guild.welcome_message}. */
public interface GuildWelcomePort {

    /** Lời chào của bang ({@code ""} = không chào). */
    String welcomeOf(long guildId);

    /** Đặt lời chào ({@code ""} = xoá). */
    void setWelcome(long guildId, String message);
}
