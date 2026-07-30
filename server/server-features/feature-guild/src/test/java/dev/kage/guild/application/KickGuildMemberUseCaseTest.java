package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.Guild;

class KickGuildMemberUseCaseTest {

    @Test
    void shouldKick_whenCallerIsLeaderAndTargetIsMember() {
        FakeGuildPort port = new FakeGuildPort();
        Guild guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        KickGuildMemberUseCase useCase = new KickGuildMemberUseCase(port);

        KickGuildMemberUseCase.Result result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(KickGuildMemberUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldRejectSelfKick() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        KickGuildMemberUseCase useCase = new KickGuildMemberUseCase(port);

        KickGuildMemberUseCase.Result result = useCase.execute(1, 1);

        assertThat(result).isEqualTo(KickGuildMemberUseCase.Result.CANNOT_KICK_SELF);
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        FakeGuildPort port = new FakeGuildPort();
        Guild guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 7).id(), 1);
        KickGuildMemberUseCase useCase = new KickGuildMemberUseCase(port);

        KickGuildMemberUseCase.Result result = useCase.execute(5, 7); // 5 là MEMBER thường, không phải leader

        assertThat(result).isEqualTo(KickGuildMemberUseCase.Result.NOT_FOUND);
        assertThat(port.findMembershipOf(7)).isPresent();
    }
}
