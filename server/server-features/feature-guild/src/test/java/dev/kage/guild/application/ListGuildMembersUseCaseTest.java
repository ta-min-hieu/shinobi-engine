package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildMember;

class ListGuildMembersUseCaseTest {

    @Test
    void shouldListAllMembers_includingLeader() {
        FakeGuildPort port = new FakeGuildPort();
        Guild guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        ListGuildMembersUseCase useCase = new ListGuildMembersUseCase(port);

        List<GuildMember> members = useCase.execute(5);

        assertThat(members).extracting(m -> m.characterId()).containsExactlyInAnyOrder(1L, 5L);
    }

    @Test
    void shouldReturnEmpty_whenNotInAnyGuild() {
        ListGuildMembersUseCase useCase = new ListGuildMembersUseCase(new FakeGuildPort());

        assertThat(useCase.execute(5)).isEmpty();
    }
}
