package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KickGuildMemberUseCaseTest {

    @Test
    void shouldKick_whenCallerIsLeaderAndTargetIsMember() {
        var port = new FakeGuildPort();
        var guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        var useCase = new KickGuildMemberUseCase(port);

        var result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(KickGuildMemberUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldRejectSelfKick() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var useCase = new KickGuildMemberUseCase(port);

        var result = useCase.execute(1, 1);

        assertThat(result).isEqualTo(KickGuildMemberUseCase.Result.CANNOT_KICK_SELF);
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        var port = new FakeGuildPort();
        var guild = port.createGuild("Konoha", 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 5).id(), 1);
        port.approveJoinRequest(port.sendJoinRequest(guild.id(), 7).id(), 1);
        var useCase = new KickGuildMemberUseCase(port);

        var result = useCase.execute(5, 7); // 5 là MEMBER thường, không phải leader

        assertThat(result).isEqualTo(KickGuildMemberUseCase.Result.NOT_FOUND);
        assertThat(port.findMembershipOf(7)).isPresent();
    }
}
