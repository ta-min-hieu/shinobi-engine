package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildRole;

class LeaveGuildUseCaseTest {

    @Test
    void shouldLeave_whenMemberIsRegularMember() {
        FakeGuildPort port = new FakeGuildPort();
        Guild guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        LeaveGuildUseCase useCase = new LeaveGuildUseCase(port);

        LeaveGuildUseCase.Result result = useCase.execute(5);

        assertThat(result).isEqualTo(LeaveGuildUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldReject_whenCharacterNotInAnyGuild() {
        LeaveGuildUseCase useCase = new LeaveGuildUseCase(new FakeGuildPort());

        LeaveGuildUseCase.Result result = useCase.execute(5);

        assertThat(result).isEqualTo(LeaveGuildUseCase.Result.NOT_IN_GUILD);
    }

    @Test
    void shouldReject_whenCharacterIsLeader() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        LeaveGuildUseCase useCase = new LeaveGuildUseCase(port);

        LeaveGuildUseCase.Result result = useCase.execute(1);

        assertThat(result).isEqualTo(LeaveGuildUseCase.Result.LEADER_CANNOT_LEAVE);
        assertThat(port.findMembershipOf(1)).isPresent();
    }

    /**
     * {@code findMembershipOf} rồi mới {@code leaveGuild} không atomic — race THẬT (xác nhận bằng
     * Testcontainers ở {@code GuildIntegrationTest
     * #shouldReportNotInGuild_whenLeaderKicksTheSameMemberBetweenItsTwoReadsAndWrite}) là leader
     * KICK đúng nhân vật này ngay giữa 2 lời gọi đó, nên {@code leaveGuild} xoá 0 dòng dù
     * {@code findMembershipOf} vừa thấy CÒN trong bang. Test đơn vị này chỉ verify khúc DỊCH kết
     * quả DELETE thật (không cần Postgres) — bản thân race đã verify bằng Postgres thật ở test kia.
     */
    @Test
    void shouldReportNotInGuild_whenPortLeaveGuildReturnsFalse_despiteMembershipCheckPassing() {
        LeaveGuildUseCase useCase = new LeaveGuildUseCase(new AlreadyKickedGuildPort());

        LeaveGuildUseCase.Result result = useCase.execute(5);

        assertThat(result).isEqualTo(LeaveGuildUseCase.Result.NOT_IN_GUILD);
    }

    /** Mô phỏng đúng 1 khoảnh khắc: membership CÒN thấy được, nhưng DELETE thật đã trễ (đã bị kick). */
    private static final class AlreadyKickedGuildPort extends UnsupportedGuildPort {

        @Override
        public Optional<GuildMember> findMembershipOf(long characterId) {
            return Optional.of(new GuildMember(1, characterId, GuildRole.MEMBER, Instant.now()));
        }

        @Override
        public boolean leaveGuild(long characterId) {
            return false;
        }
    }
}
