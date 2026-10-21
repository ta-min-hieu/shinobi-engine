package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildPort;

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

    /** ADR-068 — bang cấp 2 nhận được người thứ 51 (trần 60). */
    @Test
    void shouldApproveBeyondFifty_whenTheGuildLevelRaisedTheCap() {
        FakeGuildPort base = new FakeGuildPort();
        GuildPort port = new LevelTwo(base);
        base.createGuild("Konoha", 1);
        long guildId = base.findByName("Konoha").orElseThrow().id();
        for (long applicant = 100;
                base.listMembers(guildId).size() < ApproveGuildJoinRequestUseCase.MAX_MEMBERS;
                applicant++) {
            base.approveJoinRequest(base.sendJoinRequest(guildId, applicant).id(), 1);
        }
        GuildJoinRequest request = base.sendJoinRequest(guildId, 5);

        assertThat(new ApproveGuildJoinRequestUseCase(port).execute(request.id(), 1))
                .isEqualTo(ApproveGuildJoinRequestUseCase.Result.OK);
    }

    /** Bang luôn cấp 2 — mọi thứ khác chuyển cho fake gốc. */
    private static final class LevelTwo implements GuildPort {
        private final FakeGuildPort delegate;

        LevelTwo(FakeGuildPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public int levelOf(long guildId) {
            return 2;
        }

        @Override
        public java.util.Optional<dev.kage.guild.domain.Guild> findByName(String name) {
            return delegate.findByName(name);
        }

        @Override
        public java.util.Optional<dev.kage.guild.domain.Guild> findById(long guildId) {
            return delegate.findById(guildId);
        }

        @Override
        public java.util.Optional<dev.kage.guild.domain.GuildMember> findMembershipOf(long characterId) {
            return delegate.findMembershipOf(characterId);
        }

        @Override
        public java.util.List<dev.kage.guild.domain.GuildMember> listMembers(long guildId) {
            return delegate.listMembers(guildId);
        }

        @Override
        public dev.kage.guild.domain.Guild createGuild(String name, long leaderCharacterId) {
            return delegate.createGuild(name, leaderCharacterId);
        }

        @Override
        public boolean disbandGuild(long leaderCharacterId) {
            return delegate.disbandGuild(leaderCharacterId);
        }

        @Override
        public boolean leaveGuild(long characterId) {
            return delegate.leaveGuild(characterId);
        }

        @Override
        public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
            return delegate.kickMember(leaderCharacterId, targetCharacterId);
        }

        @Override
        public java.util.Optional<GuildJoinRequest> findPendingRequestOfCharacter(long characterId) {
            return delegate.findPendingRequestOfCharacter(characterId);
        }

        @Override
        public java.util.List<GuildJoinRequest> listPendingRequests(long guildId) {
            return delegate.listPendingRequests(guildId);
        }

        @Override
        public GuildJoinRequest sendJoinRequest(long guildId, long characterId) {
            return delegate.sendJoinRequest(guildId, characterId);
        }

        @Override
        public ApproveResult approveJoinRequest(long requestId, long leaderCharacterId) {
            return delegate.approveJoinRequest(requestId, leaderCharacterId);
        }

        @Override
        public boolean rejectJoinRequest(long requestId, long leaderCharacterId) {
            return delegate.rejectJoinRequest(requestId, leaderCharacterId);
        }
    }
}
