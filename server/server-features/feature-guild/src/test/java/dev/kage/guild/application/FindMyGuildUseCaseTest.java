package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.guild.domain.GuildRole;
import org.junit.jupiter.api.Test;

class FindMyGuildUseCaseTest {

    @Test
    void shouldReturnGuildInfo_whenCharacterIsLeader() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        FindMyGuildUseCase useCase = new FindMyGuildUseCase(port);

        FindMyGuildUseCase.Result result = useCase.execute(1).orElseThrow();

        assertThat(result.guildName()).isEqualTo("Konoha");
        assertThat(result.myRole()).isEqualTo(GuildRole.LEADER);
    }

    @Test
    void shouldReturnEmpty_whenNotInAnyGuild() {
        FindMyGuildUseCase useCase = new FindMyGuildUseCase(new FakeGuildPort());

        assertThat(useCase.execute(1)).isEmpty();
    }
}
