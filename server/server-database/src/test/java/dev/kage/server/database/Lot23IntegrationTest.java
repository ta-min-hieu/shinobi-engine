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
import dev.kage.character.api.TribulationFacade;
import dev.kage.character.application.GrantExperienceUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.TribulationRules;
import dev.kage.crafting.api.CraftingFacade;
import dev.kage.crafting.application.CraftItemUseCase;
import dev.kage.market.application.BuyOrderUseCase;
import dev.kage.market.domain.BuyOrder;
import dev.kage.market.domain.BuyOrderOutcome;
import dev.kage.pvp.application.TournamentDirector;
import dev.kage.pvp.domain.TournamentPort;
import dev.kage.pvp.domain.TournamentRules;
import dev.kage.server.database.account.JdbcAccountTransactionRunner;
import dev.kage.server.database.account.PostgresAccountAdapter;
import dev.kage.server.database.account.PostgresSecurityPinAdapter;
import dev.kage.server.database.character.JdbcCharacterTransactionRunner;
import dev.kage.server.database.character.PostgresCharacterAdapter;
import dev.kage.server.database.character.PostgresTribulationAdapter;
import dev.kage.server.database.crafting.JdbcCraftingTransactionRunner;
import dev.kage.server.database.crafting.PostgresCraftingWorkshopAdapter;
import dev.kage.server.database.crafting.PostgresRecipeAdapter;
import dev.kage.server.database.market.JdbcMarketTransactionRunner;
import dev.kage.server.database.market.PostgresBuyOrderAdapter;
import dev.kage.server.database.market.PostgresMarketGoldAdapter;
import dev.kage.server.database.market.PostgresMarketInventoryAdapter;
import dev.kage.server.database.market.PostgresMarketRecordAdapter;
import dev.kage.server.database.pvp.PostgresTournamentAdapter;
import dev.kage.server.database.shop.PostgresMerchantAdapter;
import dev.kage.server.database.world.PostgresArtifactAdapter;
import dev.kage.server.database.world.PostgresEncounterAdapter;
import dev.kage.server.database.world.PostgresSpiritFieldAdapter;
import dev.kage.server.database.world.PostgresTamingAdapter;
import dev.kage.shop.api.TravelingMerchantFacade;
import dev.kage.shop.domain.MerchantBuyOutcome;
import dev.kage.shop.domain.MerchantStock;
import dev.kage.shop.domain.MerchantVisit;
import dev.kage.world.api.ArtifactFacade;
import dev.kage.world.api.EncounterFacade;
import dev.kage.world.api.SpiritFieldFacade;
import dev.kage.world.api.TamingFacade;
import dev.kage.world.domain.EncounterPort;
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
    void forgeShouldMakeOneItemPerRunWithQualityLinesAndStayApartFromAlchemy() {
        long smith = character(account("forgesmith"), "ForgeSmth");
        CraftingFacade crafting = new CraftingFacade(new CraftItemUseCase(
                new PostgresRecipeAdapter(dataSource),
                new PostgresCraftingWorkshopAdapter(),
                new JdbcCraftingTransactionRunner(dataSource)));
        String recipe = "forge_spirit_iron_sword";
        long sword = templateOf("spirit_iron_sword");
        int runs = 10;
        giveItem(smith, "spirit_ore", 8 * runs, 40);
        giveItem(smith, "demon_core", 2 * runs, 41);
        setGold(smith, 600L * runs);

        assertThat(crafting.forgeRecipes()).extracting(r -> r.code()).contains(recipe, "forge_spirit_iron_robe");
        assertThat(crafting.recipes()).extracting(r -> r.code()).doesNotContain(recipe);
        assertThat(crafting.craft(smith, 99, recipe)).isEqualTo(CraftItemUseCase.Outcome.UNKNOWN_RECIPE);
        assertThat(crafting.forge(smith, 19, recipe).outcome()).isEqualTo(CraftItemUseCase.Outcome.LEVEL_TOO_LOW);

        for (int i = 0; i < runs; i++) {
            CraftItemUseCase.ForgeResult result = crafting.forge(smith, 20, recipe);
            assertThat(result.outcome()).isEqualTo(CraftItemUseCase.Outcome.OK);
            long newest = scalar(
                    "SELECT MAX(id) FROM inventory_item WHERE character_id = ? AND item_template_id = ?", smith, sword);
            assertThat(scalar("SELECT COUNT(*) FROM item_refinement WHERE inventory_item_id = ?", newest))
                    .isEqualTo(result.quality().orElseThrow().refinementLines());
        }
        assertThat(countOf(smith, sword)).isEqualTo(runs);
        assertThat(countOf(smith, templateOf("spirit_ore"))).isZero();
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", smith))
                .isZero();
        assertThat(scalar("SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'FORGE_ITEM'", smith))
                .isEqualTo(runs);
        assertThat(crafting.forge(smith, 20, recipe).outcome()).isEqualTo(CraftItemUseCase.Outcome.MISSING_MATERIALS);
    }

    @Test
    void encounterShouldOpenOneAtATimeCapThreePerDayAndPayOutOnce() {
        long hero = character(account("encounterhero"), "EncHero01");
        long other = character(account("encounterother"), "EncOther1");
        EncounterFacade encounters = new EncounterFacade(new PostgresEncounterAdapter(dataSource));
        Instant day = Instant.parse("2026-11-20T03:00:00Z"); // 10:00 giờ VN — ngày riêng của test này
        setGold(hero, 0L);

        EncounterPort.Offer first = encounters.start(hero, day).orElseThrow();
        assertThat(first.options()).isNotEmpty();
        assertThat(encounters.start(hero, day.plusSeconds(5)))
                .as("đang có một kỳ ngộ chờ")
                .isEmpty();
        assertThat(encounters.choose(other, first.instanceId(), 0, day).outcome())
                .as("kỳ ngộ của người khác")
                .isEqualTo(EncounterPort.Outcome.NOT_FOUND);
        assertThat(encounters.choose(hero, first.instanceId(), 9, day).outcome())
                .isEqualTo(EncounterPort.Outcome.INVALID_OPTION);
        EncounterPort.Resolution paid = encounters.choose(hero, first.instanceId(), 0, day.plusSeconds(10));
        assertThat(paid.outcome()).isEqualTo(EncounterPort.Outcome.OK);
        assertThat(paid.resultKey()).startsWith("encounter.");
        assertThat(scalar("SELECT gold FROM character_stat WHERE character_id = ?", hero))
                .isEqualTo(paid.gold());
        if (paid.itemTemplateId() != 0) {
            assertThat(countOf(hero, paid.itemTemplateId())).isEqualTo(paid.quantity());
        }
        assertThat(encounters
                        .choose(hero, first.instanceId(), 0, day.plusSeconds(11))
                        .outcome())
                .as("chọn lần hai không được thưởng lại")
                .isEqualTo(EncounterPort.Outcome.NOT_FOUND);

        EncounterPort.Offer second = encounters.start(hero, day.plusSeconds(20)).orElseThrow();
        Instant afterExpiry = day.plusSeconds(20).plus(Duration.ofMinutes(11));
        assertThat(encounters.choose(hero, second.instanceId(), 0, afterExpiry).outcome())
                .as("quá 10 phút thì tan")
                .isEqualTo(EncounterPort.Outcome.NOT_FOUND);
        assertThat(encounters.start(hero, afterExpiry)).isPresent();
        assertThat(encounters.start(hero, afterExpiry.plus(Duration.ofMinutes(11))))
                .as("trần 3 lần một ngày")
                .isEmpty();
        assertThat(encounters.start(hero, day.plus(Duration.ofDays(1))))
                .as("sang ngày mới")
                .isPresent();
        assertThat(scalar(
                        "SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'ENCOUNTER_CHOICE'", hero))
                .isEqualTo(1L);
    }

    @Test
    void tamingShouldListTameableSpeciesScaleChanceByLevelAndGrantPets() {
        long tamer = character(account("tamerone"), "TamerOne1");
        TamingFacade taming = new TamingFacade(new PostgresTamingAdapter(dataSource));
        long stonePet = templateOf("stone_spirit_pet");

        assertThat(taming.tameable("stone_ogre")).isPresent();
        assertThat(taming.tameable("sample_boss"))
                .as("boss không thu phục được")
                .isEmpty();
        assertThat(taming.chancesFor(30)).containsEntry("stone_ogre", 40).containsEntry("wasteland_beast", 30);
        assertThat(taming.chancesFor(25).get("wasteland_beast"))
                .as("quái Lv29 hơn 4 cấp ⇒ −20%")
                .isEqualTo(10);
        assertThat(TamingFacade.lookup(taming.chancesFor(1), "training_dummy")).isEmpty();

        assertThat(taming.grantPet(tamer, stonePet, "stone_ogre")).isTrue();
        assertThat(countOf(tamer, stonePet)).isEqualTo(1L);
        assertThat(scalar("SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'TAME_MONSTER'", tamer))
                .isEqualTo(1L);
    }

    @Test
    void tournamentShouldRunAKnockoutOnRealTablesAndCrownTheLastOneStanding() {
        long[] ids = {
            character(account("tourneya"), "TourneyA1"),
            character(account("tourneyb"), "TourneyB1"),
            character(account("tourneyc"), "TourneyC1")
        };
        // Chủ nhật 08/11/2026: 10:00 và 20:00 giờ VN — tuần riêng của test này.
        Instant morning = Instant.parse("2026-11-08T03:00:00Z");
        Instant start = Instant.parse("2026-11-08T13:00:00Z");
        TournamentDirector director = new TournamentDirector(
                new PostgresTournamentAdapter(dataSource), TournamentRules.DEFAULT_SCHEDULE, 11L);
        for (long id : ids) {
            assertThat(director.register(id, 30, morning)).isEqualTo(TournamentPort.RegisterOutcome.OK);
        }
        assertThat(director.register(ids[0], 30, morning)).isEqualTo(TournamentPort.RegisterOutcome.ALREADY_REGISTERED);
        List<long[]> duels = new java.util.ArrayList<>();
        long[] champion = new long[1];
        TournamentDirector.Arena arena = everyoneOnline(duels);
        TournamentDirector.Announcer announcer = championInto(champion);

        director.tick(start, arena, announcer);
        director.tick(start.plusSeconds(15), arena, announcer);
        assertThat(duels).hasSize(1);
        director.onDuelSettled(duels.getFirst()[0], duels.getFirst()[1]);
        director.tick(start.plusSeconds(30), arena, announcer);
        director.tick(start.plusSeconds(45), arena, announcer);
        assertThat(duels).hasSize(2);
        director.onDuelSettled(duels.getLast()[1], duels.getLast()[0]);
        director.tick(start.plusSeconds(60), arena, announcer);

        assertThat(champion[0]).isEqualTo(duels.getLast()[1]);
        TournamentPort.Tournament done = director.current(start);
        assertThat(done.status()).isEqualTo(TournamentPort.Status.FINISHED);
        assertThat(done.championId()).isEqualTo(champion[0]);
        assertThat(scalar(
                        "SELECT COUNT(*) FROM tournament_entry WHERE tournament_id = ? AND eliminated_round IS NULL",
                        done.id()))
                .isEqualTo(1L);
    }

    /** Đấu trường giả: ai cũng online, mọi trận mở được — ghi lại các cặp. */
    private static TournamentDirector.Arena everyoneOnline(List<long[]> duels) {
        return new TournamentDirector.Arena() {
            @Override
            public boolean online(long characterId) {
                return true;
            }

            @Override
            public boolean startDuel(long aId, long bId) {
                duels.add(new long[] {aId, bId});
                return true;
            }

            @Override
            public void forfeit(long characterId) {}
        };
    }

    private static TournamentDirector.Announcer championInto(long[] champion) {
        return new TournamentDirector.Announcer() {
            @Override
            public void matchAssigned(long characterId, long opponentId, int round) {}

            @Override
            public void champion(long characterId) {
                champion[0] = characterId;
            }
        };
    }

    @Test
    void tribulationShouldCapTheLevelUntilPassedAndPenalizeWithoutDroppingBelowTheCap() {
        long cultivator = character(account("tribulationone"), "TribuOne1");
        TribulationFacade tribulation = new TribulationFacade(new PostgresTribulationAdapter(dataSource));
        GrantExperienceUseCase experience =
                new GrantExperienceUseCase(new PostgresCharacterAdapter(dataSource), tribulation::levelCap);
        long beyondCap = TribulationRules.expToReach(20);

        GrantExperienceUseCase.Result capped = experience.execute(cultivator, beyondCap);
        assertThat(capped.progress().level()).as("chưa độ kiếp ⇒ đứng ở tầng 9").isEqualTo(18);
        assertThat(capped.progress().totalExp()).isEqualTo(beyondCap);
        assertThat(TribulationRules.ready(tribulation.passed(cultivator), beyondCap))
                .isTrue();

        tribulation.penalize(cultivator, 18);
        assertThat(scalar("SELECT exp FROM character WHERE id = ?", cultivator))
                .isEqualTo(beyondCap - TribulationRules.failPenalty(18));
        tribulation.penalize(cultivator, 18);
        tribulation.penalize(cultivator, 18);
        tribulation.penalize(cultivator, 18);
        tribulation.penalize(cultivator, 18);
        tribulation.penalize(cultivator, 18);
        assertThat(scalar("SELECT exp FROM character WHERE id = ?", cultivator))
                .as("không tụt xuống dưới đầu cấp 18")
                .isGreaterThanOrEqualTo(TribulationRules.expToReach(18));

        experience.execute(cultivator, beyondCap);
        assertThat(tribulation.advance(cultivator, 0)).isTrue();
        assertThat(tribulation.advance(cultivator, 0))
                .as("compare-and-set: lượt thứ hai trượt")
                .isFalse();
        GrantExperienceUseCase.Result broken = experience.syncAfterExternalGrant(cultivator);
        assertThat(broken.progress().level()).as("qua kiếp ⇒ lên cấp ngay").isGreaterThan(18);
        assertThat(broken.leveledUp()).isTrue();
        assertThat(tribulation.levelCap(cultivator)).isEqualTo(36);
    }

    @Test
    void artifactsShouldCarryOneSkillEachAndBeForgeable() {
        ArtifactFacade artifacts = new ArtifactFacade(new PostgresArtifactAdapter(dataSource));
        long bell = templateOf("golden_bell_artifact");

        assertThat(artifacts.skillOf(bell)).hasValueSatisfying(skill -> {
            assertThat(skill.effect()).isEqualTo("SHIELD");
            assertThat(skill.cooldownSeconds()).isEqualTo(60);
        });
        assertThat(artifacts.skillOf(templateOf("spring_mirror_artifact")).map(s -> s.effect()))
                .hasValue("HEAL");
        assertThat(artifacts.skillOf(templateOf("wind_fan_artifact")).map(s -> s.effect()))
                .hasValue("SPEED");
        assertThat(artifacts.skillOf(templateOf("spirit_ore")))
                .as("món thường không có chiêu")
                .isEmpty();
        assertThat(scalar(
                        "SELECT COUNT(*) FROM crafting_recipe WHERE kind = 'FORGE' AND output_item_template_id = ?",
                        bell))
                .isEqualTo(1L);
        assertThat(artifacts.use(1L, Optional.of(bell), NOW)).isInstanceOf(ArtifactFacade.Use.Ready.class);
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
