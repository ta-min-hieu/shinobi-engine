package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.guild.api.GuildProgressionFacade;
import dev.kage.guild.application.GuildProgressionUseCase;
import dev.kage.guild.domain.DonationOutcome;
import dev.kage.guild.domain.GuildProgressionPort;
import dev.kage.guild.domain.GuildRank;
import dev.kage.guild.domain.GuildStanding;
import dev.kage.guild.domain.WeeklyCompletion;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.Opcode;

/** docs/README.md dòng 81 (b): một thư thưởng bang tuần lỗi không được bỏ dở thư của người khác. */
class GuildProgressHooksTest {

    private static final long GUILD_ID = 7L;
    private static final long BROKEN_MEMBER = 2L;

    @Test
    void shouldMailEveryOtherMember_whenOneRewardMailThrows() {
        List<Long> mailed = new ArrayList<>();
        List<Long> told = new ArrayList<>();
        GuildProgressHooks hooks = GuildProgressHooks.unbound();
        hooks.bind(
                completingFacade(List.of(1L, BROKEN_MEMBER, 3L)),
                request -> {
                    if (request.recipientId() == BROKEN_MEMBER) {
                        throw new IllegalStateException("DB lỗi giữa vòng gửi");
                    }
                    mailed.add(request.recipientId());
                    return new MailFacade.SendOutcome(MailFacade.SendResult.OK, 100L + request.recipientId());
                },
                (id, opcode, payload) -> {
                    if (opcode == Opcode.OPCODE_NEW_MAIL_EVENT_VALUE) {
                        told.add(id);
                    }
                });

        hooks.onMonsterKilled(1L);

        assertThat(mailed).containsExactly(1L, 3L);
        assertThat(told).containsExactly(1L, 3L);
    }

    @Test
    void shouldStillAnnounceCompletion_whenOneRewardMailThrows() {
        List<Long> announced = new ArrayList<>();
        GuildProgressHooks hooks = GuildProgressHooks.unbound();
        hooks.bind(
                completingFacade(List.of(1L, BROKEN_MEMBER)),
                request -> {
                    throw new IllegalStateException("DB lỗi");
                },
                (id, opcode, payload) -> {
                    if (opcode == Opcode.OPCODE_GUILD_CHANGED_EVENT_VALUE) {
                        announced.add(id);
                    }
                });

        hooks.onMonsterKilled(1L);

        assertThat(announced).containsExactly(1L, BROKEN_MEMBER);
    }

    /** Lượt hạ nào cũng chốt tuần với {@code members}. */
    private static GuildProgressionFacade completingFacade(List<Long> members) {
        GuildProgressionPort port = new GuildProgressionPort() {
            @Override
            public Optional<GuildStanding> standingOf(long characterId, LocalDate weekStart) {
                return Optional.empty();
            }

            @Override
            public Donation donate(long characterId, long amount, String idempotencyKey) {
                return new Donation(DonationOutcome.NOT_IN_GUILD, false, 0L);
            }

            @Override
            public Optional<WeeklyCompletion> recordKill(long characterId, LocalDate weekStart) {
                return Optional.of(new WeeklyCompletion(GUILD_ID, members, false));
            }

            @Override
            public Map<Long, Long> contributionsOf(long guildId) {
                return Map.of();
            }

            @Override
            public List<GuildRank> rankGuilds() {
                return List.of();
            }
        };
        return new GuildProgressionFacade(new GuildProgressionUseCase(port, Clock.systemUTC()));
    }
}
