package dev.kage.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.account.api.SecurityPinFacade;
import dev.kage.account.domain.Account;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.market.application.BuyOrderUseCase;
import dev.kage.market.domain.BuyOrder;
import dev.kage.market.domain.BuyOrderOutcome;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.account.PostgresSecurityPinAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.market.JdbcMarketTransactionRunner;
import dev.kage.server.database.market.PostgresBuyOrderAdapter;
import dev.kage.server.database.market.PostgresMarketGoldAdapter;
import dev.kage.server.database.market.PostgresMarketInventoryAdapter;
import dev.kage.server.database.market.PostgresMarketRecordAdapter;
import dev.kage.server.database.shop.PostgresMerchantAdapter;
import dev.kage.server.database.world.PostgresSpiritFieldAdapter;
import dev.kage.shop.api.TravelingMerchantFacade;
import dev.kage.shop.domain.MerchantBuyOutcome;
import dev.kage.shop.domain.MerchantStock;
import dev.kage.shop.domain.MerchantVisit;
import dev.kage.world.api.SpiritFieldFacade;
import dev.kage.world.domain.SpiritFieldPort;

/** Lô 23 (ADR-115…126) trên PostgreSQL THẬT — một container cho cả lô. */
@Testcontainers
class Lot23IntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final Instant NOW = Instant.parse("2026-10-30T05:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

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
    }

    static long account(String username) {
        PostgresAccountAdapter accountAdapter = new PostgresAccountAdapter();
        JdbcAccountTransactionRunner accountTx = new JdbcAccountTransactionRunner(dataSource);
        Account[] holder = new Account[1];
        accountTx.inTransaction(tx -> holder[0] = accountAdapter.findOrCreate(tx, username));
        return holder[0].id();
    }

    static long character(long accountId, String name) {
        PostgresCharacterAdapter adapter = new PostgresCharacterAdapter(dataSource);
        JdbcCharacterTransactionRunner tx = new JdbcCharacterTransactionRunner(dataSource);
        Character[] holder = new Character[1];
        tx.inTransaction(t -> holder[0] = adapter.create(
                t,
                new NewCharacter(accountId, name, Faction.HOA, UUID.randomUUID().toString())));
        return holder[0].id();
    }

    @Test
    void securityPinShouldRoundTripLockAndClear() {
        long accountId = account("pintester");
        SecurityPinFacade pins = new SecurityPinFacade(new PostgresSecurityPinAdapter(dataSource));

        assertThat(pins.status(accountId, NOW).hasPin()).isFalse();
        assertThat(pins.set(accountId, "", "2468", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.OK);
        assertThat(pins.verify(accountId, "2468", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.OK);
        for (int i = 0; i < 4; i++) {
            assertThat(pins.verify(accountId, "0000", NOW).outcome()).isEqualTo(SecurityPinFacade.Outcome.WRONG_PIN);
        }
        SecurityPinFacade.Result locked = pins.verify(accountId, "0000", NOW);
        assertThat(locked.outcome()).isEqualTo(SecurityPinFacade.Outcome.LOCKED);
        assertThat(pins.status(accountId, NOW).lockedUntil()).isEqualTo(locked.lockedUntil());
        String stored = JdbcOperations.queryOne(
                        dataSource,
                        "SELECT pin_hash FROM account_security_pin WHERE account_id = ?",
                        rs -> rs.getString(1),
                        accountId)
                .orElseThrow();
        assertThat(stored).doesNotContain("2468");

        assertThat(pins.clear(accountId)).isTrue();
        assertThat(pins.status(accountId, NOW).hasPin()).isFalse();
    }

    static long scalar(String sql, Object... params) {
        return JdbcOperations.queryOne(dataSource, sql, rs -> rs.getLong(1), params)
                .orElseThrow();
    }

    static void setGold(long characterId, long gold) {
        JdbcOperations.update(
                dataSource, "UPDATE character_stat SET gold = ? WHERE character_id = ?", gold, characterId);
    }

    static long giveItem(long characterId, String code, int quantity, int slot) {
        return JdbcOperations.queryOne(dataSource, """
                        INSERT INTO inventory_item (character_id, item_template_id, quantity, slot_index, enhance_level)
                        SELECT ?, id, ?, ?, 0 FROM item_template WHERE code = ? RETURNING id
                        """, rs -> rs.getLong(1), characterId, quantity, slot, code)
                .orElseThrow();
    }

    static long templateOf(String code) {
        return scalar("SELECT id FROM item_template WHERE code = ?", code);
    }

    static long countOf(long characterId, long templateId) {
        return scalar(
                "SELECT COALESCE(SUM(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                templateId);
    }

    private static BuyOrderUseCase buyOrders(Instant now) {
        return new BuyOrderUseCase(
                new BuyOrderUseCase.Ports(
                        new PostgresBuyOrderAdapter(dataSource),
                        new PostgresMarketInventoryAdapter(),
                        new PostgresMarketGoldAdapter(),
                        new PostgresMarketRecordAdapter()),
                new JdbcMarketTransactionRunner(dataSource),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void buyOrderShouldEscrowFillPartlyCancelAndRefundOnExpiry() {
        long buyer = character(account("buyorderbuyer"), "BuyerA1");
        long seller = character(account("buyorderseller"), "SellerA1");
        setGold(buyer, 1_000L);
        setGold(seller, 0L);
        long pill = templateOf("minor_healing_pill");
        long stack = giveItem(seller, "minor_healing_pill", 5, 40);
        BuyOrderUseCase orders = buyOrders(NOW);

        assertThat(orders.place(buyer, pill, 10, 200L)).isEqualTo(BuyOrderOutcome.INSUFFICIENT_GOLD);
        assertThat(orders.place(buyer, 999_999L, 1, 10L)).isEqualTo(BuyOrderOutcome.UNKNOWN_ITEM);
        assertThat(orders.place(buyer, pill, 4, 100L)).isEqualTo(BuyOrderOutcome.OK);
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", buyer))
                .isEqualTo(600L);
        long orderId = orders.mine(buyer).getFirst().id();

        assertThat(orders.sell(buyer, orderId, stack, 1).outcome()).isEqualTo(BuyOrderOutcome.OWN_ORDER);
        assertThat(orders.sell(seller, orderId, stack, 5).outcome()).isEqualTo(BuyOrderOutcome.INVALID_QUANTITY);
        BuyOrderUseCase.SellResult sold = orders.sell(seller, orderId, stack, 3);
        assertThat(sold.outcome()).isEqualTo(BuyOrderOutcome.OK);
        assertThat(sold.fill().orElseThrow().proceeds()).isEqualTo(285L); // 300 − phí 5%
        assertThat(countOf(buyer, pill)).isEqualTo(3L);
        assertThat(countOf(seller, pill)).isEqualTo(2L);
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", seller))
                .isEqualTo(285L);
        BuyOrder open = orders.mine(buyer).getFirst();
        assertThat(open.remaining()).isEqualTo(1);

        assertThat(orders.cancel(seller, orderId)).isEqualTo(BuyOrderOutcome.NOT_FOUND);
        assertThat(orders.cancel(buyer, orderId)).isEqualTo(BuyOrderOutcome.OK);
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", buyer))
                .isEqualTo(700L);
        assertThat(orders.mine(buyer)).isEmpty();

        assertThat(orders.place(buyer, pill, 2, 50L)).isEqualTo(BuyOrderOutcome.OK);
        long second = orders.mine(buyer).getFirst().id();
        BuyOrderUseCase later = buyOrders(NOW.plus(Duration.ofDays(3)));
        List<BuyOrder> due = later.expireAndListUnrefunded(100);
        assertThat(due).extracting(BuyOrder::id).contains(second);
        assertThat(due.stream()
                        .filter(o -> o.id() == second)
                        .findFirst()
                        .orElseThrow()
                        .escrowRemaining())
                .isEqualTo(100L);
        later.markRefunded(second);
        assertThat(later.expireAndListUnrefunded(100)).extracting(BuyOrder::id).doesNotContain(second);
        assertThat(later.browse(Optional.of(pill))).extracting(BuyOrder::id).doesNotContain(second);
    }

    @Test
    void travelingMerchantShouldPlanOncePerDaySellSharedStockAndAnnounceOnce() {
        PostgresMerchantAdapter port = new PostgresMerchantAdapter(dataSource);
        TravelingMerchantFacade merchant = new TravelingMerchantFacade(port);
        Instant day = Instant.parse("2026-11-04T17:00:00Z"); // 00:00 5/11 giờ VN — ngày riêng của test này
        merchant.ensurePlanned(day.plusSeconds(60), List.of("sect_gate", "spirit_vein"), new SplittableRandom(7));
        merchant.ensurePlanned(day.plusSeconds(120), List.of("sect_gate"), new SplittableRandom(8));
        assertThat(port.countVisitsStartingBetween(day, day.plus(Duration.ofDays(1))))
                .isBetween(2, 3);

        // Một lần ghé thủ công, kho 1 cái — kiểm "chung cả server" và các nhánh từ chối.
        Instant start = Instant.parse("2026-11-10T03:00:00Z");
        long pill = templateOf("revive_pill");
        port.insertVisit(
                "sect_gate", start, start.plus(Duration.ofMinutes(30)), List.of(new MerchantStock(pill, 220L, 1)));
        Instant during = start.plus(Duration.ofMinutes(5));
        MerchantVisit visit = merchant.active(during).orElseThrow();
        assertThat(visit.stock()).containsExactly(new MerchantStock(pill, 220L, 1));
        assertThat(merchant.claimArrivals(during)).extracting(MerchantVisit::id).contains(visit.id());
        assertThat(merchant.claimArrivals(during)).extracting(MerchantVisit::id).doesNotContain(visit.id());

        long poor = character(account("merchantpoor"), "MerchPoor");
        long rich = character(account("merchantrich"), "MerchRich");
        long other = character(account("merchantother"), "MerchOthr");
        setGold(poor, 100L);
        setGold(rich, 1_000L);
        setGold(other, 1_000L);
        assertThat(merchant.buy(poor, visit.id(), pill, during)).isEqualTo(MerchantBuyOutcome.INSUFFICIENT_GOLD);
        assertThat(countOf(poor, pill)).isZero();
        assertThat(merchant.buy(rich, visit.id(), pill, start.minusSeconds(1)))
                .isEqualTo(MerchantBuyOutcome.NOT_PRESENT);
        assertThat(merchant.buy(rich, visit.id(), pill, during)).isEqualTo(MerchantBuyOutcome.OK);
        assertThat(countOf(rich, pill)).isEqualTo(1L);
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", rich))
                .isEqualTo(780L);
        assertThat(merchant.buy(other, visit.id(), pill, during)).isEqualTo(MerchantBuyOutcome.SOLD_OUT);
        assertThat(merchant.active(start.plus(Duration.ofMinutes(31)))).isEmpty();
    }

    @Test
    void spiritFieldShouldPlantRipenWaterHarvestAndUnlock() {
        long owner = character(account("fieldowner"), "FieldOwnr");
        long friend = character(account("fieldfriend"), "FieldFrnd");
        long seed = templateOf("spirit_herb_seed");
        long herb = templateOf("spirit_herb");
        giveItem(owner, "spirit_herb_seed", 2, 40);
        SpiritFieldFacade field = new SpiritFieldFacade(new PostgresSpiritFieldAdapter(dataSource));

        assertThat(field.field(owner).plots()).isEqualTo(4);
        assertThat(field.plant(owner, 0, seed, NOW)).isEqualTo(SpiritFieldPort.Outcome.OK);
        assertThat(field.plant(owner, 0, seed, NOW)).isEqualTo(SpiritFieldPort.Outcome.PLOT_OCCUPIED);
        assertThat(field.plant(owner, 4, seed, NOW)).isEqualTo(SpiritFieldPort.Outcome.INVALID_PLOT);
        assertThat(field.plant(owner, 1, herb, NOW)).isEqualTo(SpiritFieldPort.Outcome.UNKNOWN_SEED);
        assertThat(field.plant(owner, 1, seed, NOW)).isEqualTo(SpiritFieldPort.Outcome.OK);
        assertThat(field.plant(owner, 2, seed, NOW)).isEqualTo(SpiritFieldPort.Outcome.NO_SEED);
        assertThat(countOf(owner, seed)).isZero();

        Instant half = NOW.plus(Duration.ofMinutes(30));
        assertThat(field.harvest(owner, 0, half)).isEqualTo(SpiritFieldPort.Outcome.NOT_RIPE);
        assertThat(field.water(friend, owner, 0, half)).isEqualTo(SpiritFieldPort.Outcome.OK);
        assertThat(field.water(friend, owner, 0, half)).isEqualTo(SpiritFieldPort.Outcome.ALREADY_WATERED);
        assertThat(field.water(friend, owner, 3, half)).isEqualTo(SpiritFieldPort.Outcome.PLOT_EMPTY);

        Instant ripe = NOW.plus(Duration.ofMinutes(61));
        assertThat(field.harvest(owner, 1, ripe)).isEqualTo(SpiritFieldPort.Outcome.OK);
        assertThat(countOf(owner, herb)).isEqualTo(3L); // không ai tưới ⇒ đúng sản lượng gốc
        assertThat(field.harvest(owner, 0, ripe)).isEqualTo(SpiritFieldPort.Outcome.OK);
        assertThat(countOf(owner, herb)).isBetween(6L, 7L); // 3 × 1,1 ⇒ 3 hoặc 4
        assertThat(field.harvest(owner, 0, ripe)).isEqualTo(SpiritFieldPort.Outcome.PLOT_EMPTY);
        assertThat(field.field(owner).planted()).isEmpty();

        setGold(owner, 999L);
        assertThat(field.unlockPlot(owner)).isEqualTo(SpiritFieldPort.Outcome.INSUFFICIENT_GOLD);
        setGold(owner, 1_000L);
        assertThat(field.unlockPlot(owner)).isEqualTo(SpiritFieldPort.Outcome.OK);
        assertThat(field.field(owner).plots()).isEqualTo(5);
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", owner))
                .isZero();
    }
}
