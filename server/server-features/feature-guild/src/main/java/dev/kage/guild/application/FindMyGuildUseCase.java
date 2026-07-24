package dev.kage.guild.application;

import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;
import java.util.Optional;

/** Thông tin bang hiện tại của {@code characterId} — rỗng nếu chưa vào bang nào. */
public final class FindMyGuildUseCase {

    private final GuildPort port;

    public FindMyGuildUseCase(GuildPort port) {
        this.port = port;
    }

    public Optional<Result> execute(long characterId) {
        return port.findMembershipOf(characterId)
                .flatMap(member -> port.findById(member.guildId())
                        .map(guild -> new Result(guild.id(), guild.name(), member.role())));
    }

    public record Result(long guildId, String guildName, GuildRole myRole) {
    }
}
