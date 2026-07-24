package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateGuildUseCaseTest {

    @Test
    void shouldCreateGuild_andMakeCreatorLeader() {
        var port = new FakeGuildPort();
        var useCase = new CreateGuildUseCase(port);

        var result = useCase.execute(5, "Konoha");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.OK);
        assertThat(result.guild().name()).isEqualTo("Konoha");
        assertThat(port.findMembershipOf(5).orElseThrow().role().name()).isEqualTo("LEADER");
    }

    @Test
    void shouldReject_whenCharacterAlreadyInGuild() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 5);
        var useCase = new CreateGuildUseCase(port);

        var result = useCase.execute(5, "Suna");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.ALREADY_IN_GUILD);
    }

    @Test
    void shouldReject_whenNameAlreadyTaken() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 5);
        var useCase = new CreateGuildUseCase(port);

        var result = useCase.execute(7, "Konoha");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.NAME_TAKEN);
    }
}
