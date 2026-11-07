package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;
import java.util.OptionalInt;

import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.guild.api.GuildRecruitmentFacade;
import dev.kage.guild.domain.GuildLevelTable;
import dev.kage.guild.domain.GuildRecruitmentPort;
import dev.kage.protocol.GuildRecruitmentView;
import dev.kage.protocol.ListGuildRecruitmentResponse;
import dev.kage.protocol.PostGuildRecruitmentCommand;
import dev.kage.protocol.PostGuildRecruitmentOutcome;
import dev.kage.protocol.PostGuildRecruitmentResponse;

/**
 * Bảng chiêu mộ bang (ADR-185) — bang chủ / phó treo lời chiêu mộ (qua bộ lọc từ cấm của chat); ai cũng xem được bảng.
 * Đơn xin vào vẫn là {@code SendGuildJoinRequestCommand}; cấp tối thiểu được kiểm ở đó ({@link #minLevelOf}).
 */
final class GuildRecruitmentCommands {

    private final GuildRecruitmentFacade recruitment;
    private final Clock clock;

    GuildRecruitmentCommands(GuildRecruitmentFacade recruitment, Clock clock) {
        this.recruitment = recruitment;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    PostGuildRecruitmentResponse post(long characterId, PostGuildRecruitmentCommand command) {
        PostGuildRecruitmentResponse.Builder response =
                PostGuildRecruitmentResponse.newBuilder().setOpen(command.getOpen());
        if (!command.getOpen()) {
            return response.setOutcome(wire(recruitment.remove(characterId, clock.instant())))
                    .build();
        }
        Optional<String> message = GuildRecruitmentFacade.normalize(command.getMessage());
        if (message.isEmpty()) {
            return response.setOutcome(PostGuildRecruitmentOutcome.POST_GUILD_RECRUITMENT_OUTCOME_TOO_LONG)
                    .build();
        }
        if (ProfanityFilter.containsBannedWord(message.get())) {
            return response.setOutcome(PostGuildRecruitmentOutcome.POST_GUILD_RECRUITMENT_OUTCOME_BANNED_WORD)
                    .build();
        }
        return response.setOutcome(
                        wire(recruitment.post(characterId, command.getMinLevel(), message.get(), clock.instant())))
                .build();
    }

    /** Virtual Thread; bảng chiêu mộ (ai cũng xem được). */
    ListGuildRecruitmentResponse list() {
        ListGuildRecruitmentResponse.Builder response = ListGuildRecruitmentResponse.newBuilder();
        for (GuildRecruitmentPort.Listing listing : recruitment.list()) {
            response.addEntries(GuildRecruitmentView.newBuilder()
                    .setGuildName(listing.guildName())
                    .setGuildLevel(listing.guildLevel())
                    .setMembers(listing.members())
                    .setMaxMembers(GuildLevelTable.maxMembers(listing.guildLevel()))
                    .setMinLevel(listing.minLevel())
                    .setMessage(listing.message()));
        }
        return response.build();
    }

    /** Cấp tối thiểu bang {@code guildName} đang chiêu mộ (rỗng = không đòi). */
    OptionalInt minLevelOf(String guildName) {
        return recruitment.minLevelOf(guildName);
    }

    private static PostGuildRecruitmentOutcome wire(GuildRecruitmentPort.Outcome outcome) {
        return PostGuildRecruitmentOutcome.valueOf("POST_GUILD_RECRUITMENT_OUTCOME_" + outcome.name());
    }
}
