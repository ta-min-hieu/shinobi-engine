package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RejectGuildJoinRequestUseCaseTest {

    @Test
    void shouldReject_andDeleteRequest_whenCallerIsLeader() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var request = port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        var useCase = new RejectGuildJoinRequestUseCase(port);

        var result = useCase.execute(request.id(), 1);

        assertThat(result).isEqualTo(RejectGuildJoinRequestUseCase.Result.OK);
        assertThat(port.findPendingRequestOfCharacter(5)).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenCallerIsNotLeader() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var request = port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        var useCase = new RejectGuildJoinRequestUseCase(port);

        var result = useCase.execute(request.id(), 999);

        assertThat(result).isEqualTo(RejectGuildJoinRequestUseCase.Result.NOT_FOUND);
        assertThat(port.findPendingRequestOfCharacter(5)).isPresent();
    }
}
