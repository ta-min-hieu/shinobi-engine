package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateGuildUseCaseTest {

    @Test
    void shouldCreateGuild_andMakeCreatorLeader() {
        FakeGuildPort port = new FakeGuildPort();
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        CreateGuildUseCase.Result result = useCase.execute(5, "Konoha");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.OK);
        assertThat(result.guild().name()).isEqualTo("Konoha");
        assertThat(port.findMembershipOf(5).orElseThrow().role().name()).isEqualTo("LEADER");
    }

    @Test
    void shouldReject_whenCharacterAlreadyInGuild() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 5);
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        CreateGuildUseCase.Result result = useCase.execute(5, "Suna");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.ALREADY_IN_GUILD);
    }

    @Test
    void shouldReject_whenNameAlreadyTaken() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 5);
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        CreateGuildUseCase.Result result = useCase.execute(7, "Konoha");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.NAME_TAKEN);
    }
}
