package dev.kage.guild.api;

import java.time.Instant;

import dev.kage.guild.domain.GuildName;
import dev.kage.guild.domain.GuildRenamePort;

/** Đổi tên bang hội (ADR-172). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GuildRenameFacade {

    /** Composition không có đổi tên bang (test). */
    public static final GuildRenameFacade DISABLED = new GuildRenameFacade(
            (characterId, newName, at) -> GuildRenamePort.Renamed.refused(GuildRenamePort.Outcome.NOT_IN_GUILD));

    private final GuildRenamePort port;

    /** Dựng ở composition root. */
    public GuildRenameFacade(GuildRenamePort port) {
        this.port = port;
    }

    /** Kiểm luật tên (đúng luật tạo bang — {@link GuildName}) rồi mới chạm DB. */
    public GuildRenamePort.Renamed rename(long characterId, String rawName, Instant at) {
        String name = GuildName.normalize(rawName);
        if (!GuildName.isValid(name)) {
            return GuildRenamePort.Renamed.refused(GuildRenamePort.Outcome.INVALID_NAME);
        }
        return port.rename(characterId, name, at);
    }
}
