package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.character.domain.Character;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;

/**
 * Lệnh GM (ADR-052) qua đúng composition root, trên PostgreSQL THẬT: im lặng với người không quyền và
 * khi chốt cấu hình tắt; cấp quyền (GM không tạo vàng); không tác động người cấp ≥ mình; mỗi lệnh có
 * dòng {@code gm_audit_log} được CHỐT (không còn PENDING); vàng ADMIN đi thư hệ thống; khoá/mở chat.
 */
@Testcontainers
@Timeout(240)
class GmCommandRouterDatabaseTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "gm-redis-password";
    private static final InetSocketAddress LOOPBACK = new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), 50_000);
    private static final InetSocketAddress OUTSIDE =
            new InetSocketAddress(InetAddress.ofLiteral("203.0.113.7"), 50_000);
    private static final String GRANT =
            "INSERT INTO gm_role (account_id, role, granted_at, note) VALUES (?, ?, now(), 'test')";
    private static final String COUNT_AUDIT = "SELECT COUNT(*) FROM gm_audit_log WHERE actor_account_id = ?";
    private static final String COUNT_AUDIT_OUTCOME =
            "SELECT COUNT(*) FROM gm_audit_log WHERE actor_account_id = ? AND outcome = ?";
    private static final String COUNT_PENDING = "SELECT COUNT(*) FROM gm_audit_log WHERE outcome = 'PENDING'";
    private static final String GOLD_MAILS =
            "SELECT COUNT(*) FROM mail WHERE recipient_id = ? AND sender_id IS NULL AND gold_amount = ?";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    private static final AtomicInteger NEXT = new AtomicInteger();
    private static final List<long[]> RELOCATIONS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static GatewayFacades facades;
    private static GmCommandRouter.Deps deps;

    @BeforeAll
    static void wire() {
        ServerDatabaseBootstrap databaseBootstrap = new ServerDatabaseBootstrap(
                new DatabaseConfig(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(POSTGRES_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()),
                new RedisConfig(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), REDIS_PASSWORD),
                DbMetricsPort.NO_OP);
        facades = GatewayBootstrap.wireFacades(databaseBootstrap);
        ConnectedCharacterRegistry registry = new ConnectedCharacterRegistry(
                facades.inventoryFacade(),
                facades.contentFacades().questFacade(),
                ExperienceGrantPort.disabled(),
                new GatewayNetworkMetrics(new SimpleMeterRegistry()));
        deps = new GmCommandRouter.Deps(
                facades.gmFacade(),
                facades.characterFacade(),
                facades.contentFacades().socialFacades().chatFacade(),
                facades.contentFacades().socialFacades().mailFacade(),
                facades.inventoryFacade(),
                registry,
                id -> Optional.empty(),
                id -> 100,
                (mover, anchor) -> {
                    RELOCATIONS.add(new long[] {mover, anchor});
                    return MainWorldRelocation.Outcome.OK;
                });
    }

    @Test
    void shouldStaySilent_forAPlayerWithoutRole_andWhenTheGatesAreClosed() {
        Character player = newCharacter();
        Character gm = newCharacter();
        grant(gm.accountId(), "GM");

        assertThat(router(true).execute(actor(player, LOOPBACK), "/gm help")).isEmpty();
        assertThat(router(false).execute(actor(gm, LOOPBACK), "/gm help"))
                .as("KAGE_GM_ENABLED=false")
                .isEmpty();
        assertThat(router(true).execute(actor(gm, OUTSIDE), "/gm help"))
                .as("ngoài CIDR")
                .isEmpty();
        assertThat(count(COUNT_AUDIT, player.accountId())).isZero();
    }

    @Test
    void gmShouldNotCreateGold_butAdminShould_andEveryCommandIsAuditedAndClosed() {
        Character gm = newCharacter();
        Character admin = newCharacter();
        Character player = newCharacter();
        grant(gm.accountId(), "GM");
        grant(admin.accountId(), "ADMIN");

        List<String> denied = router(true).execute(actor(gm, LOOPBACK), "/gm gold " + player.name() + " 500");
        List<String> paid = router(true).execute(actor(admin, LOOPBACK), "/gm gold " + player.name() + " 500");

        assertThat(denied).singleElement().asString().startsWith("Denied");
        assertThat(paid).singleElement().asString().startsWith("Sent mail");
        assertThat(count(COUNT_AUDIT_OUTCOME, gm.accountId(), "DENIED")).isEqualTo(1L);
        assertThat(count(COUNT_AUDIT_OUTCOME, admin.accountId(), "OK")).isEqualTo(1L);
        assertThat(count(GOLD_MAILS, player.id(), 500L))
                .as("vàng đi thư hệ thống")
                .isEqualTo(1L);
        assertThat(count(COUNT_PENDING)).as("mọi dòng audit đã được chốt").isZero();
    }

    @Test
    void muteShouldBlockChat_untilUnmuted_andNeverTouchAnEqualRank() {
        Character gm = newCharacter();
        Character otherGm = newCharacter();
        Character player = newCharacter();
        grant(gm.accountId(), "GM");
        grant(otherGm.accountId(), "GM");

        router(true).execute(actor(gm, LOOPBACK), "/gm mute " + player.name() + " 30");
        boolean mutedAfterMute = deps.chat().isMuted(player.accountId(), Instant.now());
        router(true).execute(actor(gm, LOOPBACK), "/gm unmute " + player.name());
        List<String> onPeer = router(true).execute(actor(gm, LOOPBACK), "/gm mute " + otherGm.name() + " 30");

        assertThat(mutedAfterMute).isTrue();
        assertThat(deps.chat().isMuted(player.accountId(), Instant.now())).isFalse();
        assertThat(onPeer).singleElement().asString().startsWith("Denied");
        assertThat(deps.chat().isMuted(otherGm.accountId(), Instant.now())).isFalse();
    }

    @Test
    void shouldAnswerUsageAndUnknownTargets() {
        Character gm = newCharacter();
        grant(gm.accountId(), "GM");

        assertThat(router(true).execute(actor(gm, LOOPBACK), "/gm mute x"))
                .singleElement()
                .asString()
                .startsWith("Usage");
        assertThat(router(true).execute(actor(gm, LOOPBACK), "/gm who NoSuchHero"))
                .singleElement()
                .asString()
                .startsWith("No character");
        assertThat(router(true).execute(actor(gm, LOOPBACK), "/gm inspect " + gm.name()))
                .singleElement()
                .asString()
                .startsWith("items=");
        assertThat(router(true).execute(actor(gm, LOOPBACK), "/gm who " + gm.name()))
                .singleElement()
                .asString()
                .startsWith("id=" + gm.id());
    }

    /** goto dời CHÍNH GM tới mục tiêu, summon dời MỤC TIÊU tới GM — đúng chiều, và có dòng audit OK. */
    @Test
    void gotoAndSummonShouldMoveInOppositeDirections() {
        Character gm = newCharacter();
        Character player = newCharacter();
        grant(gm.accountId(), "GM");

        router(true).execute(actor(gm, LOOPBACK), "/gm goto " + player.name());
        router(true).execute(actor(gm, LOOPBACK), "/gm summon " + player.name());
        List<String> self = router(true).execute(actor(gm, LOOPBACK), "/gm summon " + gm.name());

        assertThat(RELOCATIONS)
                .filteredOn(move -> move[0] == gm.id() || move[0] == player.id())
                .containsExactly(new long[] {gm.id(), player.id()}, new long[] {player.id(), gm.id()});
        assertThat(self).singleElement().asString().startsWith("Already there");
        assertThat(count(COUNT_AUDIT_OUTCOME, gm.accountId(), "OK")).isEqualTo(2L);
    }

    private static GmCommandRouter router(boolean enabled) {
        return new GmCommandRouter(deps, new GmConfig(enabled, GmConfig.parseCidrs(null)), Clock.systemUTC());
    }

    private static GmCommandRouter.Actor actor(Character character, InetSocketAddress remote) {
        return new GmCommandRouter.Actor(character.accountId(), character.id(), remote);
    }

    private static Character newCharacter() {
        int n = NEXT.incrementAndGet();
        long accountId = facades.accountFacade().login("gmtest" + n).accountId();
        return facades.characterFacade()
                .createCharacter(accountId, "GmHero" + n, UUID.randomUUID().toString());
    }

    private static void grant(long accountId, String role) {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(GRANT)) {
            statement.setLong(1, accountId);
            statement.setString(2, role);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only cấp quyền GM", e);
        }
    }

    private static long count(String sql, Object... params) {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only: " + sql, e);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
