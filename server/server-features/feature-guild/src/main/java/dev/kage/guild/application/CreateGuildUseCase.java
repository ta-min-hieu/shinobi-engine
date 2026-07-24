package dev.kage.guild.application;

import dev.kage.guild.domain.DuplicateGuildNameException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildPort;

/** Tạo bang mới — người tạo tự động thành LEADER. Chỉ tạo được khi chưa ở bang nào khác. */
public final class CreateGuildUseCase {

    private final GuildPort port;

    public CreateGuildUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long characterId, String name) {
        if (port.findMembershipOf(characterId).isPresent()) {
            return new Result(Outcome.ALREADY_IN_GUILD, null);
        }
        try {
            return new Result(Outcome.OK, port.createGuild(name, characterId));
        } catch (DuplicateGuildNameException e) {
            return new Result(Outcome.NAME_TAKEN, null);
        }
    }

    public enum Outcome { OK, ALREADY_IN_GUILD, NAME_TAKEN }

    public record Result(Outcome outcome, Guild guild) {
    }
}
