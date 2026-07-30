package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.Guild;

class DisbandGuildUseCaseTest {

    @Test
    void shouldDisband_andRemoveAllMembers_whenCallerIsLeader() {
        FakeGuildPort port = new FakeGuildPort();
        Guild guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        DisbandGuildUseCase useCase = new DisbandGuildUseCase(port);

        DisbandGuildUseCase.Result result = useCase.execute(1);

        assertThat(result).isEqualTo(DisbandGuildUseCase.Result.OK);
        assertThat(port.findById(guild.id())).isEmpty();
        assertThat(port.findMembershipOf(1)).isEmpty();
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        FakeGuildPort port = new FakeGuildPort();
        Guild guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        DisbandGuildUseCase useCase = new DisbandGuildUseCase(port);

        DisbandGuildUseCase.Result result = useCase.execute(5);

        assertThat(result).isEqualTo(DisbandGuildUseCase.Result.NOT_LEADER);
        assertThat(port.findById(guild.id())).isPresent();
    }

    @Test
    void shouldReject_whenCharacterNotInAnyGuild() {
        DisbandGuildUseCase useCase = new DisbandGuildUseCase(new FakeGuildPort());

        assertThat(useCase.execute(1)).isEqualTo(DisbandGuildUseCase.Result.NOT_LEADER);
    }
}
