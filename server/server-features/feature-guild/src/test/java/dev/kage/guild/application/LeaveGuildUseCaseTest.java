package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LeaveGuildUseCaseTest {

    @Test
    void shouldLeave_whenMemberIsRegularMember() {
        var port = new FakeGuildPort();
        var guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        var useCase = new LeaveGuildUseCase(port);

        var result = useCase.execute(5);

        assertThat(result).isEqualTo(LeaveGuildUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldReject_whenCharacterNotInAnyGuild() {
        var useCase = new LeaveGuildUseCase(new FakeGuildPort());

        var result = useCase.execute(5);

        assertThat(result).isEqualTo(LeaveGuildUseCase.Result.NOT_IN_GUILD);
    }

    @Test
    void shouldReject_whenCharacterIsLeader() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var useCase = new LeaveGuildUseCase(port);

        var result = useCase.execute(1);

        assertThat(result).isEqualTo(LeaveGuildUseCase.Result.LEADER_CANNOT_LEAVE);
        assertThat(port.findMembershipOf(1)).isPresent();
    }
}
