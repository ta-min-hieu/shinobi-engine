package dev.kage.server.database.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.guild.application.LeaveGuildUseCase;
import dev.kage.guild.domain.DuplicateGuildJoinRequestException;
import dev.kage.guild.domain.DuplicateGuildMembershipException;
import dev.kage.guild.domain.DuplicateGuildNameException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V14 ({@code guild}/{@code
 * guild_member}/{@code guild_join_request}) + tính NGUYÊN TỬ của {@code createGuild}/
 * {@code approveJoinRequest} (feature-guild/CLAUDE.md).
 */
@Testcontainers
class GuildIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static long accountId;

    @BeforeAll
    static void migrate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);

        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] accountHolder = new Account[1];
        accountTx.inTransaction(tx -> accountHolder[0] = accountAdapter.findOrCreate(tx, "guildtester"));
        accountId = accountHolder[0].id();
    }

    private static long createFreshCharacter(String name) {
        PostgresCharacterAdapter characterAdapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner characterTx = new JdbcCharacterTransactionRunner(dataSource);
        NewCharacter newCharacter =
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString());
        Character[] holder = new Character[1];
        characterTx.inTransaction(tx -> holder[0] = characterAdapter.create(tx, newCharacter));
        return holder[0].id();
    }

    private static PostgresGuildAdapter newAdapter() {
        return new PostgresGuildAdapter(dataSource);
    }

    @Test
    void shouldCreateGuild_andMakeCreatorLeaderMember() {
        long leader = createFreshCharacter("GuildLeader1");
        PostgresGuildAdapter adapter = newAdapter();

        Guild guild = adapter.createGuild("Konoha1", leader);

        assertThat(adapter.findByName("Konoha1")).isPresent();
        GuildMember membership = adapter.findMembershipOf(leader).orElseThrow();
        assertThat(membership.guildId()).isEqualTo(guild.id());
        assertThat(membership.role()).isEqualTo(GuildRole.LEADER);
    }

    @Test
    void shouldRejectDuplicateGuildName() {
        long leaderA = createFreshCharacter("GuildLeader2");
        long leaderB = createFreshCharacter("GuildLeader3");
        PostgresGuildAdapter adapter = newAdapter();
        adapter.createGuild("Konoha2", leaderA);

        assertThatThrownBy(() -> adapter.createGuild("Konoha2", leaderB))
                .isInstanceOf(DuplicateGuildNameException.class);
        // 2 bảng phải nguyên tử — thất bại ở guild KHÔNG được để lại rác guild_member cho leaderB.
        assertThat(adapter.findMembershipOf(leaderB)).isEmpty();
    }

    /** V54 (2026-10-14, README dòng 42): tên bang chỉ khác hoa-thường cũng là trùng. */
    @Test
    void shouldRejectGuildName_thatDiffersOnlyInCase() {
        long leaderA = createFreshCharacter("GuildLeaderCa");
        long leaderB = createFreshCharacter("GuildLeaderCb");
        PostgresGuildAdapter adapter = newAdapter();
        adapter.createGuild("KonohaCase", leaderA);

        assertThatThrownBy(() -> adapter.createGuild("konohacase", leaderB))
                .isInstanceOf(DuplicateGuildNameException.class);
        assertThat(adapter.findMembershipOf(leaderB)).isEmpty();
    }

    @Test
    void shouldApproveJoinRequest_andAddMemberAtomically() {
        long leader = createFreshCharacter("GuildLeader4");
        long applicant = createFreshCharacter("GuildApplicant1");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("Konoha3", leader);
        GuildJoinRequest request = adapter.sendJoinRequest(guild.id(), applicant);

        GuildPort.ApproveResult result = adapter.approveJoinRequest(request.id(), leader);

        assertThat(result).isEqualTo(GuildPort.ApproveResult.OK);
        assertThat(adapter.findMembershipOf(applicant)).isPresent();
        assertThat(adapter.findPendingRequestOfCharacter(applicant)).isEmpty();
    }

    @Test
    void shouldRejectApprove_whenCallerIsNotLeader() {
        long leader = createFreshCharacter("GuildLeader5");
        long notLeader = createFreshCharacter("GuildNotLeader1");
        long applicant = createFreshCharacter("GuildApplicant2");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("Konoha4", leader);
        GuildJoinRequest request = adapter.sendJoinRequest(guild.id(), applicant);

        GuildPort.ApproveResult result = adapter.approveJoinRequest(request.id(), notLeader);

        assertThat(result).isEqualTo(GuildPort.ApproveResult.NOT_FOUND);
        assertThat(adapter.findMembershipOf(applicant)).isEmpty();
    }

    @Test
    void shouldRejectDuplicateJoinRequest_sameGuildSameCharacter() {
        long leader = createFreshCharacter("GuildLeader6");
        long applicant = createFreshCharacter("GuildApplicant3");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("Konoha5", leader);
        adapter.sendJoinRequest(guild.id(), applicant);

        assertThatThrownBy(() -> adapter.sendJoinRequest(guild.id(), applicant))
                .isInstanceOf(DuplicateGuildJoinRequestException.class);
    }

    /**
     * Dòng 28 (V73): đơn chờ thứ hai của cùng một nhân vật — kể cả vào bang KHÁC — bị chính DB chặn, nên hai
     * lệnh xin vào hai bang gửi cùng lúc không còn cùng lọt qua bước kiểm "đã có đơn chờ" của application.
     */
    @Test
    void shouldRejectASecondPendingRequest_toAnotherGuild_atDatabaseLevel() {
        long leaderA = createFreshCharacter("GuildLeader28a");
        long leaderB = createFreshCharacter("GuildLeader28b");
        long applicant = createFreshCharacter("GuildApplicant28");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guildA = adapter.createGuild("Konoha28", leaderA);
        Guild guildB = adapter.createGuild("Suna28", leaderB);
        adapter.sendJoinRequest(guildA.id(), applicant);

        assertThatThrownBy(() -> adapter.sendJoinRequest(guildB.id(), applicant))
                .isInstanceOf(DuplicateGuildJoinRequestException.class);
        assertThat(adapter.findPendingRequestOfCharacter(applicant))
                .map(GuildJoinRequest::guildId)
                .contains(guildA.id());
    }

    @Test
    void shouldDropStaleRequest_whenApplicantAlreadyInAnotherGuildAtApprovalTime() {
        long leaderA = createFreshCharacter("GuildLeader7");
        long applicant = createFreshCharacter("GuildApplicant4");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guildA = adapter.createGuild("Konoha6", leaderA);
        GuildJoinRequest request = adapter.sendJoinRequest(guildA.id(), applicant);
        adapter.createGuild("Suna1", applicant); // applicant tự tạo bang khác trong lúc đơn PENDING

        GuildPort.ApproveResult result = adapter.approveJoinRequest(request.id(), leaderA);

        assertThat(result).isEqualTo(GuildPort.ApproveResult.TARGET_ALREADY_IN_GUILD);
        assertThat(adapter.findPendingRequestOfCharacter(applicant)).isEmpty();
    }

    @Test
    void shouldDisbandGuild_andCascadeDeleteMembersAndRequests() {
        long leader = createFreshCharacter("GuildLeader8");
        long member = createFreshCharacter("GuildMember1");
        long applicant = createFreshCharacter("GuildApplicant5");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("Konoha7", leader);
        adapter.approveJoinRequest(adapter.sendJoinRequest(guild.id(), member).id(), leader);
        adapter.sendJoinRequest(guild.id(), applicant);

        boolean disbanded = adapter.disbandGuild(leader);

        assertThat(disbanded).isTrue();
        assertThat(adapter.findById(guild.id())).isEmpty();
        assertThat(adapter.findMembershipOf(leader)).isEmpty();
        assertThat(adapter.findMembershipOf(member)).isEmpty();
        assertThat(adapter.findPendingRequestOfCharacter(applicant)).isEmpty();
    }

    @Test
    void shouldEnforce1GuildPerCharacter_atDatabaseLevel() {
        long leaderA = createFreshCharacter("GuildLeader9");
        long leaderB = createFreshCharacter("GuildLeader10");
        long applicant = createFreshCharacter("GuildApplicant6");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guildA = adapter.createGuild("Konoha8", leaderA);
        Guild guildB = adapter.createGuild("Suna2", leaderB);
        GuildJoinRequest requestToA = adapter.sendJoinRequest(guildA.id(), applicant);
        adapter.approveJoinRequest(requestToA.id(), leaderA);
        GuildJoinRequest requestToB = adapter.sendJoinRequest(guildB.id(), applicant);

        GuildPort.ApproveResult result = adapter.approveJoinRequest(requestToB.id(), leaderB);

        assertThat(result).isEqualTo(GuildPort.ApproveResult.TARGET_ALREADY_IN_GUILD);
    }

    /**
     * Race THẬT: 1 nhân vật gửi 2 lệnh "Tạo bang" (tên KHÁC nhau) gần như đồng thời — VD double
     * click với client bị lag, hoặc client tự thử gửi lại tên khác sau timeout tưởng lần đầu thất
     * bại. {@code CreateGuildUseCase} check-then-act ({@code findMembershipOf} rồi mới
     * {@code createGuild}) không atomic — cả 2 request đều thấy "chưa ở bang nào" trước khi 1 trong
     * 2 transaction commit. Trước bản vá, {@code insertMember} dùng {@code JdbcOperations.update}
     * (không có nhánh dịch unique_violation), nên bên thua ném {@code DatabaseAccessException}
     * CHUNG CHUNG thay vì {@link DuplicateGuildMembershipException} — {@code CreateGuildUseCase}
     * chỉ bắt {@code DuplicateGuildNameException} nên exception này XUYÊN THẲNG lên
     * {@code GatewayChannelHandler.handleCreateGuildBlocking} (không có try/catch), khiến virtual
     * thread chết mà KHÔNG gửi packet phản hồi nào — client của người thua cuộc treo vô thời hạn
     * thay vì nhận {@code CREATE_GUILD_OUTCOME_ALREADY_IN_GUILD}. Dùng {@link CyclicBarrier} để 2
     * luồng thật sự gọi {@code createGuild} CÙNG LÚC (không phải may rủi theo thời gian).
     */
    @Test
    @Timeout(15)
    void shouldTranslateRaceIntoDuplicateGuildMembershipException_whenSameCharacterRacesTwoCreateGuildCalls()
            throws Exception {
        long leader = createFreshCharacter("GuildRaceLeader1");
        PostgresGuildAdapter adapter = newAdapter();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Guild> createA = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return adapter.createGuild("ConcurrentA", leader);
        };
        Callable<Guild> createB = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return adapter.createGuild("ConcurrentB", leader);
        };

        Future<Guild> futureA = pool.submit(createA);
        Future<Guild> futureB = pool.submit(createB);
        Guild winner = null;
        RuntimeException loserException = null;
        try {
            winner = futureA.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            loserException = (RuntimeException) e.getCause();
        }
        try {
            Guild guildB = futureB.get(10, TimeUnit.SECONDS);
            assertThat(winner).as("cả 2 lời gọi không được cùng thành công").isNull();
            winner = guildB;
        } catch (ExecutionException e) {
            assertThat(loserException)
                    .as("phải có đúng 1 lời gọi thua cuộc, không phải cả 2")
                    .isNull();
            loserException = (RuntimeException) e.getCause();
        }
        pool.shutdown();

        assertThat(winner).as("đúng 1 trong 2 lời gọi phải tạo bang thành công").isNotNull();
        assertThat(loserException)
                .as("lời gọi thua cuộc phải ném DuplicateGuildMembershipException (dịch được ở"
                        + " application/), KHÔNG phải DatabaseAccessException chung chung xuyên thẳng lên gateway")
                .isInstanceOf(DuplicateGuildMembershipException.class);
        // Rollback phải TOÀN BỘ — tên bang của lời thua cuộc không được để lại rác.
        boolean createdA = adapter.findByName("ConcurrentA").isPresent();
        boolean createdB = adapter.findByName("ConcurrentB").isPresent();
        assertThat(createdA ^ createdB)
                .as("chỉ đúng 1 trong 2 tên bang được tạo thật")
                .isTrue();
        assertThat(adapter.findMembershipOf(leader).orElseThrow().guildId()).isEqualTo(winner.id());
    }

    /**
     * Race THẬT: leader KICK 1 thành viên đúng lúc thành viên đó tự bấm RỜI bang — cả hai chạy
     * trên virtual thread khác nhau, không tuần tự hoá theo guild ({@code authExecutor} =
     * {@code Executors.newVirtualThreadPerTaskExecutor()}, xem {@code GatewayServer}).
     * {@code LeaveGuildUseCase} cũ: {@code findMembershipOf} (thấy CÒN trong bang) rồi
     * {@code port.leaveGuild(characterId)} — bản CŨ vứt bỏ giá trị {@code boolean} trả về và LUÔN
     * trả {@code Result.OK}, dù DELETE thật sự ảnh hưởng 0 dòng vì kick đã xoá dòng đó trước. Người
     * chơi bị kick nhận thông báo "Đã rời bang" như thể tự nguyện, thay vì phải biết mình BỊ ĐUỔI.
     * DB vẫn đúng (chỉ 1 DELETE thắng) — cái sai là {@code Result} trả về client, nên test đo đúng
     * GIÁ TRỊ CUỐI đó, không phải "có bị chặn hay không". Dùng {@link GuildPort} bọc quanh adapter
     * THẬT để ép đúng lệnh kick THẬT (adapter thật, DB thật) chạy CHÍNH XÁC giữa 2 lời gọi port của
     * use case — tất định, không phải may rủi theo thời gian luồng.
     */
    @Test
    @Timeout(15)
    void shouldReportNotInGuild_whenLeaderKicksTheSameMemberBetweenItsTwoReadsAndWrite() {
        long leader = createFreshCharacter("KickRaceLeader1");
        long member = createFreshCharacter("KickRaceMember1");
        PostgresGuildAdapter adapter = newAdapter();
        Guild guild = adapter.createGuild("KickRaceGuild1", leader);
        adapter.approveJoinRequest(adapter.sendJoinRequest(guild.id(), member).id(), leader);

        GuildPort racingPort = new KickInjectingGuildPort(adapter, leader, member);
        LeaveGuildUseCase useCase = new LeaveGuildUseCase(racingPort);

        LeaveGuildUseCase.Result result = useCase.execute(member);

        assertThat(result)
                .as("thành viên vừa bị KICK ngay giữa 2 lời gọi của use case — không được báo"
                        + " Result.OK như thể tự rời")
                .isEqualTo(LeaveGuildUseCase.Result.NOT_IN_GUILD);
        assertThat(adapter.findMembershipOf(member)).isEmpty();
    }

    /**
     * Bọc {@link PostgresGuildAdapter} THẬT — chỉ chèn 1 lệnh {@code kickMember} THẬT (DB thật)
     * ngay sau {@link #findMembershipOf}, trước khi {@link LeaveGuildUseCase} kịp gọi
     * {@link #leaveGuild}, mô phỏng đúng cửa sổ race giữa 2 lời gọi port không atomic đó. Mọi
     * method khác uỷ quyền thẳng cho adapter thật — {@link LeaveGuildUseCase} chỉ gọi 2 trong số
     * này nên phần còn lại không cần đúng ngữ nghĩa, chỉ cần không bị gọi.
     */
    private record KickInjectingGuildPort(PostgresGuildAdapter delegate, long leaderCharacterId, long targetCharacterId)
            implements GuildPort {

        @Override
        public Optional<GuildMember> findMembershipOf(long characterId) {
            Optional<GuildMember> result = delegate.findMembershipOf(characterId);
            if (characterId == targetCharacterId) {
                assertThat(delegate.kickMember(leaderCharacterId, targetCharacterId))
                        .as("lệnh kick THẬT chèn giữa race phải thành công")
                        .isTrue();
            }
            return result;
        }

        @Override
        public boolean leaveGuild(long characterId) {
            return delegate.leaveGuild(characterId);
        }

        @Override
        public Optional<Guild> findByName(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Guild> findById(long guildId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GuildMember> listMembers(long guildId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Guild createGuild(String name, long leaderCharacterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean disbandGuild(long leaderCharacterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<GuildJoinRequest> findPendingRequestOfCharacter(long characterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GuildJoinRequest> listPendingRequests(long guildId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GuildJoinRequest sendJoinRequest(long guildId, long characterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApproveResult approveJoinRequest(long requestId, long leaderCharacterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean rejectJoinRequest(long requestId, long leaderCharacterId) {
            throw new UnsupportedOperationException();
        }
    }
}
