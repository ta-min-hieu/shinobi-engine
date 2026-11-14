package dev.kage.guild.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.guild.domain.GuildNameHistoryPort;

/** API lịch sử tên bang (ADR-423). Chạm DB — Virtual Thread. */
public final class GuildNameHistoryFacade {

    /** Không ghi gì (composition test). */
    public static final GuildNameHistoryFacade DISABLED = new GuildNameHistoryFacade(new GuildNameHistoryPort() {
        @Override
        public void record(long guildId, String oldName, String newName, Instant at) {
            // không ghi
        }

        @Override
        public List<Rename> history(long guildId) {
            return List.of();
        }

        @Override
        public Optional<Long> findGuildId(String name) {
            return Optional.empty();
        }
    });

    private final GuildNameHistoryPort port;

    /** Dựng ở composition root. */
    public GuildNameHistoryFacade(GuildNameHistoryPort port) {
        this.port = port;
    }

    /** Ghi một lần đổi tên (bỏ qua nếu tên không đổi). */
    public void record(long guildId, String oldName, String newName, Instant at) {
        if (!oldName.equals(newName)) {
            port.record(guildId, oldName, newName, at);
        }
    }

    /** Các lần đổi tên, cũ trước. */
    public List<GuildNameHistoryPort.Rename> history(long guildId) {
        return port.history(guildId);
    }

    /** Bang theo tên hiện tại hoặc tên cũ. */
    public Optional<Long> findGuildId(String name) {
        return port.findGuildId(name.strip());
    }
}
