package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SendGuildJoinRequestUseCaseTest {

    @Test
    void shouldSendRequest_whenGuildExistsAndCharacterFree() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var useCase = new SendGuildJoinRequestUseCase(port);

        var result = useCase.execute(5, "Konoha");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.OK);
        assertThat(port.findPendingRequestOfCharacter(5)).isPresent();
    }

    @Test
    void shouldReject_whenGuildNotFound() {
        var useCase = new SendGuildJoinRequestUseCase(new FakeGuildPort());

        var result = useCase.execute(5, "KhongTonTai");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.GUILD_NOT_FOUND);
    }

    @Test
    void shouldReject_whenCharacterAlreadyInAGuild() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        port.createGuild("Suna", 5);
        var useCase = new SendGuildJoinRequestUseCase(port);

        var result = useCase.execute(5, "Konoha");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.ALREADY_IN_GUILD);
    }

    @Test
    void shouldReject_whenRequestAlreadyPending() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var useCase = new SendGuildJoinRequestUseCase(port);
        useCase.execute(5, "Konoha");

        var result = useCase.execute(5, "Konoha");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.REQUEST_ALREADY_PENDING);
    }
}
