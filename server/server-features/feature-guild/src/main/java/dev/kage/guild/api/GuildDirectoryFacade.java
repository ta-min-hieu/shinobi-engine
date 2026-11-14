package dev.kage.guild.api;

import java.util.List;

import dev.kage.guild.domain.GuildDirectoryPort;

/** API tra cứu bang (ADR-393 thâm niên). Chạm DB — Virtual Thread. */
public final class GuildDirectoryFacade {

    /** Không có dữ liệu (composition test). */
    public static final GuildDirectoryFacade DISABLED = new GuildDirectoryFacade(new GuildDirectoryPort() {
        @Override
        public List<Senior> seniority(long guildId) {
            return List.of();
        }

        @Override
        public List<Found> search(String fragment, int limit) {
            return List.of();
        }
    });

    /** ADR-399 — số bang tối đa mỗi lần tìm. */
    public static final int SEARCH_LIMIT = 10;

    /** ADR-399 — chuỗi tìm dài nhất (bằng tên bang dài nhất). */
    public static final int MAX_QUERY = 32;

    private final GuildDirectoryPort port;

    /** Dựng ở composition root. */
    public GuildDirectoryFacade(GuildDirectoryPort port) {
        this.port = port;
    }

    /** ADR-399 — tìm bang theo tên; chuỗi rỗng hoặc dài quá {@value #MAX_QUERY} ký tự ⇒ rỗng. */
    public List<GuildDirectoryPort.Found> search(String query) {
        String trimmed = query.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_QUERY) {
            return List.of();
        }
        return port.search(trimmed, SEARCH_LIMIT);
    }

    /** ADR-393 — thành viên theo thâm niên, lâu nhất trước. */
    public List<GuildDirectoryPort.Senior> seniority(long guildId) {
        return port.seniority(guildId);
    }
}
