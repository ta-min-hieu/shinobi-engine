package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListPendingGuildJoinRequestsUseCaseTest {

    @Test
    void shouldListPendingRequests_whenCallerIsLeader() {
        var port = new FakeGuildPort();
        var guild = port.createGuild("Konoha", 1);
        port.sendJoinRequest(guild.id(), 5);
        port.sendJoinRequest(guild.id(), 7);
        var useCase = new ListPendingGuildJoinRequestsUseCase(port);

        var pending = useCase.execute(1);

        assertThat(pending).extracting(r -> r.characterId()).containsExactlyInAnyOrder(5L, 7L);
    }

    @Test
    void shouldReturnEmpty_whenCallerIsNotLeader() {
        var port = new FakeGuildPort();
        var guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        var useCase = new ListPendingGuildJoinRequestsUseCase(port);

        assertThat(useCase.execute(5)).isEmpty(); // 5 là MEMBER thường
    }

    @Test
    void shouldReturnEmpty_whenNotInAnyGuild() {
        var useCase = new ListPendingGuildJoinRequestsUseCase(new FakeGuildPort());

        assertThat(useCase.execute(1)).isEmpty();
    }
}
