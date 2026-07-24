package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListGuildMembersUseCaseTest {

    @Test
    void shouldListAllMembers_includingLeader() {
        var port = new FakeGuildPort();
        var guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        var useCase = new ListGuildMembersUseCase(port);

        var members = useCase.execute(5);

        assertThat(members).extracting(m -> m.characterId()).containsExactlyInAnyOrder(1L, 5L);
    }

    @Test
    void shouldReturnEmpty_whenNotInAnyGuild() {
        var useCase = new ListGuildMembersUseCase(new FakeGuildPort());

        assertThat(useCase.execute(5)).isEmpty();
    }
}
