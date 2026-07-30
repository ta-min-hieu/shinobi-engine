package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.Guild;

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
}
