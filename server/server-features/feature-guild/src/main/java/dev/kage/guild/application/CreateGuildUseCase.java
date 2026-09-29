package dev.kage.guild.application;

import dev.kage.guild.domain.DuplicateGuildMembershipException;
import dev.kage.guild.domain.DuplicateGuildNameException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildName;
import dev.kage.guild.domain.GuildPort;

/** Tạo bang mới — người tạo tự động thành LEADER. Chỉ tạo được khi chưa ở bang nào khác. */
public final class CreateGuildUseCase {

    private final GuildPort port;

    public CreateGuildUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long characterId, String name) {
        // Quyết định chủ dự án 2026-09-29: cùng luật tên nhân vật (GuildName). Trước đó server không
        // kiểm gì — tên 33 ký tự nổ SQLException ở INSERT, tên có ký tự lạ vào thẳng DB.
        if (!GuildName.isValid(name)) {
            return new Result(Outcome.INVALID_NAME, null);
        }
        if (port.findMembershipOf(characterId).isPresent()) {
            return new Result(Outcome.ALREADY_IN_GUILD, null);
        }
        try {
            return new Result(Outcome.OK, port.createGuild(name, characterId));
        } catch (DuplicateGuildNameException e) {
            return new Result(Outcome.NAME_TAKEN, null);
        } catch (DuplicateGuildMembershipException e) {
            // findMembershipOf ở trên rồi createGuild không atomic — 2 lệnh "Tạo bang" (tên khác
            // nhau) của CÙNG 1 nhân vật gần như đồng thời có thể cả hai đều vượt qua check đó.
            // DB (UNIQUE guild_member.character_id) là tuyến chặn cuối, dịch lại thành outcome
            // nghiệp vụ bình thường thay vì để exception xuyên thẳng lên gateway.
            return new Result(Outcome.ALREADY_IN_GUILD, null);
        }
    }

    public enum Outcome {
        OK,
        ALREADY_IN_GUILD,
        NAME_TAKEN,
        /** Sai luật {@link GuildName} — không chạm Port, không tạo gì (2026-09-29). */
        INVALID_NAME
    }

    public record Result(Outcome outcome, Guild guild) {}
}
