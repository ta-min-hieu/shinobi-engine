package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListPendingGuildJoinRequestsUseCaseTest {

    @Test
    void shouldListPendingRequests_whenCallerIsLeader() {
        FakeGuildPort port = new FakeGuildPort();
        Guild guild = port.createGuild("Konoha", 1);
        port.sendJoinRequest(guild.id(), 5);
        port.sendJoinRequest(guild.id(), 7);
        ListPendingGuildJoinRequestsUseCase useCase = new ListPendingGuildJoinRequestsUseCase(port);

        List<GuildJoinRequest> pending = useCase.execute(1);

        assertThat(pending).extracting(r -> r.characterId()).containsExactlyInAnyOrder(5L, 7L);
    }

    @Test
    void shouldReturnEmpty_whenCallerIsNotLeader() {
        FakeGuildPort port = new FakeGuildPort();
        Guild guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        ListPendingGuildJoinRequestsUseCase useCase = new ListPendingGuildJoinRequestsUseCase(port);

        assertThat(useCase.execute(5)).isEmpty(); // 5 là MEMBER thường
    }

    @Test
    void shouldReturnEmpty_whenNotInAnyGuild() {
        ListPendingGuildJoinRequestsUseCase useCase = new ListPendingGuildJoinRequestsUseCase(new FakeGuildPort());

        assertThat(useCase.execute(1)).isEmpty();
    }
}
