package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.GuildJoinRequest;

class ApproveGuildJoinRequestUseCaseTest {

    @Test
    void shouldApprove_andAddMember_whenCallerIsLeader() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        GuildJoinRequest request =
                port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(port);

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(request.id(), 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isPresent();
        assertThat(port.findPendingRequestOfCharacter(5)).isEmpty();
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        GuildJoinRequest request =
                port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(port);

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(request.id(), 999);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.NOT_FOUND);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenRequestDoesNotExist() {
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(new FakeGuildPort());

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(999, 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldReturnTargetAlreadyInGuild_andDropStaleRequest_whenApplicantJoinedElsewhereMeanwhile() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        GuildJoinRequest request =
                port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        port.createGuild("Suna", 5); // nhân vật 5 tự tạo bang khác trong lúc đơn còn PENDING
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(port);

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(request.id(), 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.TARGET_ALREADY_IN_GUILD);
        assertThat(port.findPendingRequestOfCharacter(5)).isEmpty();
    }

    /** Trần thành viên bang (quyết định chủ dự án 2026-10-11): bang đủ người thì đơn không được duyệt. */
    @Test
    void shouldRefuse_andKeepTheRequest_whenTheGuildIsFull() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        long guildId = port.findByName("Konoha").orElseThrow().id();
        // Bang chủ là thành viên thứ nhất; nhận thêm cho tới đúng trần.
        for (long applicant = 100;
                port.listMembers(guildId).size() < ApproveGuildJoinRequestUseCase.MAX_MEMBERS;
                applicant++) {
            port.approveJoinRequest(port.sendJoinRequest(guildId, applicant).id(), 1);
        }
        GuildJoinRequest request = port.sendJoinRequest(guildId, 5);
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(port);

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(request.id(), 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.GUILD_FULL);
        assertThat(port.findMembershipOf(5)).isEmpty();
        assertThat(port.findPendingRequestOfCharacter(5))
                .as("đơn còn nguyên, duyệt lại được khi có chỗ")
                .isPresent();
    }

    @Test
    void shouldStillApprove_whenTheGuildIsOneBelowTheLimit() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        long guildId = port.findByName("Konoha").orElseThrow().id();
        for (long applicant = 100;
                port.listMembers(guildId).size() < ApproveGuildJoinRequestUseCase.MAX_MEMBERS - 1;
                applicant++) {
            port.approveJoinRequest(port.sendJoinRequest(guildId, applicant).id(), 1);
        }
        GuildJoinRequest request = port.sendJoinRequest(guildId, 5);
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(port);

        assertThat(useCase.execute(request.id(), 1)).isEqualTo(ApproveGuildJoinRequestUseCase.Result.OK);
        assertThat(port.listMembers(guildId)).hasSize(ApproveGuildJoinRequestUseCase.MAX_MEMBERS);
    }
}
