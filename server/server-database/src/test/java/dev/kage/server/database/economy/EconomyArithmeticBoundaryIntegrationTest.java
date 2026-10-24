package dev.kage.server.database.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.mail.api.MailFacade;
import dev.kage.mail.domain.Postage;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.shop.api.ShopFacade;
import dev.kage.trade.api.TradeFacade;

/**
 * BIÊN SỐ HỌC của kinh tế trên Postgres thật, qua đúng Facade mà gateway gọi (rà 2026-10-10).
 *
 * <p>Khác {@link EconomyConservationStressIntegrationTest} (ngẫu nhiên, đồng thời, số "bình
 * thường"): ở đây tất định, một luồng, và mỗi tham số đứng ĐÚNG trên mép — vàng vừa đủ / thiếu một
 * / thừa một, số lượng bằng {@code max_stack} / hơn một, đúng số đang có / hơn một,
 * {@code Integer.MAX_VALUE}, {@code Long.MAX_VALUE}. Mỗi ca khẳng định kết quả VÀ giá trị cuối
 * trong DB; ca bị từ chối phải để nguyên từng dòng (so ảnh chụp).
 *
 * <p>Số lượng/vàng {@code <= 0} không có ở đây: gateway chặn ở biên dây
 * ({@code EconomyCommandShape}, đã có {@code GatewayOpcodeFuzzTest} giữ).
 */
@Testcontainers
class EconomyArithmeticBoundaryIntegrationTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int BAG_CAPACITY = 20;
    private static final long PLENTY = 1_000_000_000L;
    private static final List<String> BUY_CODES = List.of("kunai", "minor_healing_pill", "wooden_sword");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;
    static EconomyBoundaryFixture fixture;
    static EconomyStressFacades facades;
    static long kunai;
    static long sword;

    @BeforeAll
    static void migrate() {
        dataSource = PostgresDataSourceFactory.create(new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        FlywayMigrator.migrate(dataSource);
        fixture = new EconomyBoundaryFixture(dataSource);
        facades = EconomyStressFacades.wire(dataSource);
        kunai = fixture.templateId("kunai");
        sword = fixture.templateId("wooden_sword");
    }

    @AfterAll
    static void shutdown() {
        PostgresDataSourceFactory.close(dataSource);
    }

    // ---------------------------------------------------------------- cửa hàng

    @Test
    void shouldChargeAndGrantExactly_whenBuyingIntoAnEmptyBagAtEveryGoldAndQuantityEdge() {
        for (String code : BUY_CODES) {
            long templateId = fixture.templateId(code);
            int maxStack = fixture.maxStack(templateId);
            int bagful = BAG_CAPACITY * maxStack;
            for (int quantity : new int[] {1, maxStack, maxStack + 1, bagful, bagful + 1}) {
                for (long goldOffset = -1; goldOffset <= 1; goldOffset++) {
                    assertBuyIntoEmptyBag(templateId, quantity, goldOffset);
                }
            }
        }
    }

    private static void assertBuyIntoEmptyBag(long templateId, int quantity, long goldOffset) {
        int maxStack = fixture.maxStack(templateId);
        long cost = fixture.buyPrice(templateId) * quantity;
        long startGold = cost + goldOffset;
        long buyer = fixture.newCharacter(startGold);
        int rowsNeeded = (quantity + maxStack - 1) / maxStack;
        ShopFacade.BuyResult expected = goldOffset < 0
                ? ShopFacade.BuyResult.INSUFFICIENT_GOLD
                : rowsNeeded > BAG_CAPACITY ? ShopFacade.BuyResult.INVENTORY_FULL : ShopFacade.BuyResult.OK;

        ShopFacade.BuyResult result =
                facades.shop().buyItem(buyer, templateId, quantity, EconomyBoundaryFixture.newKey());

        String scenario = "mua %d món loại %d, vàng = giá %+d".formatted(quantity, templateId, goldOffset);
        assertThat(result).as(scenario).isEqualTo(expected);
        boolean bought = expected == ShopFacade.BuyResult.OK;
        assertThat(fixture.gold(buyer)).as(scenario + " — vàng cuối").isEqualTo(bought ? goldOffset : startGold);
        assertThat(fixture.totalQuantity(buyer, templateId))
                .as(scenario + " — số món cuối")
                .isEqualTo(bought ? quantity : 0);
        assertThat(fixture.rowQuantities(buyer, templateId))
                .as(scenario + " — từng dòng")
                .hasSize(bought ? rowsNeeded : 0)
                .allMatch(rowQuantity -> rowQuantity > 0 && rowQuantity <= maxStack);
    }

    @Test
    void shouldMergeOnlyWhenTheWholePurchaseFitsTheStack_evenWithAFullBag() {
        int maxStack = fixture.maxStack(kunai);
        int held = 60;
        int room = maxStack - held;
        long price = fixture.buyPrice(kunai);

        long fits = fixture.newCharacter(PLENTY);
        fixture.addRow(fits, kunai, held);
        fixture.addRows(fits, sword, 1, BAG_CAPACITY - 1);
        assertThat(facades.shop().buyItem(fits, kunai, room, EconomyBoundaryFixture.newKey()))
                .isEqualTo(ShopFacade.BuyResult.OK);
        assertThat(fixture.rowQuantities(fits, kunai)).containsExactly(maxStack);
        assertThat(fixture.gold(fits)).isEqualTo(PLENTY - price * room);

        long oneTooMany = fixture.newCharacter(PLENTY);
        fixture.addRow(oneTooMany, kunai, held);
        fixture.addRows(oneTooMany, sword, 1, BAG_CAPACITY - 1);
        String before = fixture.snapshot(oneTooMany);
        assertThat(facades.shop().buyItem(oneTooMany, kunai, room + 1, EconomyBoundaryFixture.newKey()))
                .isEqualTo(ShopFacade.BuyResult.INVENTORY_FULL);
        assertThat(fixture.snapshot(oneTooMany)).isEqualTo(before);
    }

    @Test
    void shouldCreditExactly_whenSellingAtEveryQuantityEdge() {
        int owned = 40;
        long price = fixture.sellPrice(kunai);
        for (int quantity : new int[] {1, owned - 1, owned, owned + 1, Integer.MAX_VALUE}) {
            long seller = fixture.newCharacter(0L);
            long itemId = fixture.addRow(seller, kunai, owned);
            boolean sellable = quantity <= owned;

            ShopFacade.SellResult result =
                    facades.shop().sellItem(seller, itemId, quantity, EconomyBoundaryFixture.newKey());

            String scenario = "bán %d trên %d".formatted(quantity, owned);
            assertThat(result)
                    .as(scenario)
                    .isEqualTo(sellable ? ShopFacade.SellResult.OK : ShopFacade.SellResult.INSUFFICIENT_QUANTITY);
            assertThat(fixture.gold(seller)).as(scenario + " — vàng cuối").isEqualTo(sellable ? price * quantity : 0L);
            assertThat(fixture.totalQuantity(seller, kunai))
                    .as(scenario + " — còn lại")
                    .isEqualTo(sellable ? owned - quantity : owned);
            assertThat(fixture.rowCount(seller)).as(scenario + " — số dòng").isEqualTo(quantity == owned ? 0 : 1);
        }
    }

    /**
     * Trần vàng {@link GoldLedger#MAX_GOLD} (quyết định chủ dự án 2026-10-11; trước đó mép trên là
     * {@code Long.MAX_VALUE} của cột và vượt nó là lỗi 500). Chạm đúng trần thì được; vượt một đồng thì
     * {@link GoldCapExceededException} và cả giao dịch rollback — món hàng KHÔNG mất, vàng KHÔNG đổi.
     * Gateway đổi exception này thành {@code error.gold_cap_reached}.
     */
    @Test
    void shouldKeepTheItemAndTheGold_whenASaleWouldPushGoldPastTheColumnLimit() {
        long credit = fixture.sellPrice(kunai);

        long atTheEdge = fixture.newCharacter(GoldLedger.MAX_GOLD - credit);
        long edgeItem = fixture.addRow(atTheEdge, kunai, 1);
        assertThat(facades.shop().sellItem(atTheEdge, edgeItem, 1, EconomyBoundaryFixture.newKey()))
                .isEqualTo(ShopFacade.SellResult.OK);
        assertThat(fixture.gold(atTheEdge)).isEqualTo(GoldLedger.MAX_GOLD);

        long pastTheEdge = fixture.newCharacter(GoldLedger.MAX_GOLD - credit + 1);
        long item = fixture.addRow(pastTheEdge, kunai, 1);
        String before = fixture.snapshot(pastTheEdge);
        assertThatThrownBy(() -> facades.shop().sellItem(pastTheEdge, item, 1, EconomyBoundaryFixture.newKey()))
                .isInstanceOf(GoldCapExceededException.class);
        assertThat(fixture.snapshot(pastTheEdge)).isEqualTo(before);
    }

    // ---------------------------------------------------------------- thư

    @Test
    void shouldMoveExactlyTheAttachedGold_whenMailingAtEveryGoldEdge() {
        long balance = 500L;
        // 486 + phí 14 = 500 đúng biên; 487 + 14 = 501 thiếu một (ADR-076).
        for (long attached : new long[] {1L, 486L, 487L, balance - 1, balance, balance + 1, Long.MAX_VALUE}) {
            long sender = fixture.newCharacter(balance);
            long recipient = fixture.newCharacter(0L);
            long postage = Postage.feeFor(attached, 0);
            boolean affordable = attached <= balance && attached + postage <= balance;

            MailFacade.SendOutcome sent = facades.mail()
                    .sendMail(new MailFacade.SendMailRequest(sender, recipient, "gold", "", attached, List.of()));

            String scenario = "gửi %d vàng trên %d".formatted(attached, balance);
            assertThat(sent.result())
                    .as(scenario)
                    .isEqualTo(affordable ? MailFacade.SendResult.OK : MailFacade.SendResult.INSUFFICIENT_GOLD);
            assertThat(fixture.gold(sender))
                    .as(scenario + " — người gửi")
                    .isEqualTo(affordable ? balance - attached - postage : balance);
            if (affordable) {
                assertThat(facades.mail().claimMail(sent.mailId(), recipient, EconomyBoundaryFixture.newKey()))
                        .as(scenario + " — nhận")
                        .isEqualTo(MailFacade.ClaimResult.OK);
            }
            assertThat(fixture.gold(recipient)).as(scenario + " — người nhận").isEqualTo(affordable ? attached : 0L);
        }
    }

    @Test
    void shouldEscrowExactlyTheAttachedQuantity_whenMailingAtEveryQuantityEdge() {
        int maxStack = fixture.maxStack(kunai);
        int spare = 51;
        int owned = maxStack + spare;
        for (int quantity : new int[] {1, owned - 1, owned, owned + 1, Integer.MAX_VALUE}) {
            long sender = fixture.newCharacter(Postage.BASE_FEE);
            fixture.addRow(sender, kunai, maxStack);
            fixture.addRow(sender, kunai, spare);
            long recipient = fixture.newCharacter(0L);
            boolean owns = quantity <= owned;

            MailFacade.SendOutcome sent = facades.mail()
                    .sendMail(new MailFacade.SendMailRequest(
                            sender,
                            recipient,
                            "items",
                            "",
                            0L,
                            EconomyBoundaryFixture.attachments(kunai, quantity, 1)));

            String scenario = "gửi %d phi đao trên %d".formatted(quantity, owned);
            assertThat(sent.result())
                    .as(scenario)
                    .isEqualTo(owns ? MailFacade.SendResult.OK : MailFacade.SendResult.ATTACHMENT_NOT_OWNED);
            assertThat(fixture.totalQuantity(sender, kunai))
                    .as(scenario + " — người gửi còn")
                    .isEqualTo(owns ? owned - quantity : owned);
            if (owns) {
                assertThat(facades.mail().claimMail(sent.mailId(), recipient, EconomyBoundaryFixture.newKey()))
                        .as(scenario + " — nhận")
                        .isEqualTo(MailFacade.ClaimResult.OK);
            }
            assertThat(fixture.totalQuantity(recipient, kunai))
                    .as(scenario + " — người nhận có")
                    .isEqualTo(owns ? quantity : 0);
        }
    }

    /** Hai dòng đính kèm cùng loại, tổng tràn {@code int}: không dòng nào được coi là "đủ". */
    @Test
    void shouldRefuseAndTouchNothing_whenAttachmentQuantitiesOverflowIntTogether() {
        long sender = fixture.newCharacter(0L);
        fixture.addRow(sender, kunai, fixture.maxStack(kunai));
        long recipient = fixture.newCharacter(0L);
        String before = fixture.snapshot(sender);
        long mailsBefore = fixture.mailCount();

        MailFacade.SendOutcome sent = facades.mail()
                .sendMail(new MailFacade.SendMailRequest(
                        sender,
                        recipient,
                        "overflow",
                        "",
                        0L,
                        EconomyBoundaryFixture.attachments(kunai, Integer.MAX_VALUE, 2)));

        assertThat(sent.result()).isNotEqualTo(MailFacade.SendResult.OK);
        assertThat(fixture.snapshot(sender)).isEqualTo(before);
        assertThat(fixture.mailCount()).isEqualTo(mailsBefore);
    }

    /**
     * Thư mà KHÔNG túi nào nhận nổi (rà 2026-10-10). Người gửi có 1050 phi đao (11 ô) và đính kèm
     * thành 21 dòng × 50. Lúc nhận, mỗi dòng đính kèm được cấp riêng và luật gộp "không xé lẻ" chỉ
     * gộp khi CẢ dòng vừa một chồng: 50 + 50 > 99 nên dòng nào cũng mở ô mới ⇒ cần 21 ô > sức chứa
     * 20. Túi RỖNG vẫn bị {@code INVENTORY_FULL}, mà thư còn đồ thì không xoá được
     * ({@code MUST_CLAIM_FIRST}) ⇒ 1050 phi đao và mọi vàng đi kèm kẹt vĩnh viễn, lá thư nằm mãi
     * trong hộp thư người nhận.
     */
    @Test
    void shouldStayClaimableIntoAnEmptyBag_whenTheSameItemIsAttachedInManySmallLines() {
        int perLine = 50;
        int lines = BAG_CAPACITY + 1;
        int total = perLine * lines;
        int maxStack = fixture.maxStack(kunai);
        long attachedGold = 300L;
        long sender = fixture.newCharacter(attachedGold + Postage.feeFor(attachedGold, lines));
        fixture.addRows(sender, kunai, maxStack, total / maxStack);
        fixture.addRow(sender, kunai, total % maxStack);
        long recipient = fixture.newCharacter(0L);

        MailFacade.SendOutcome sent = facades.mail()
                .sendMail(new MailFacade.SendMailRequest(
                        sender,
                        recipient,
                        "many lines",
                        "",
                        attachedGold,
                        EconomyBoundaryFixture.attachments(kunai, perLine, lines)));
        assertThat(sent.result()).isEqualTo(MailFacade.SendResult.OK);
        assertThat(fixture.totalQuantity(sender, kunai)).isZero();

        MailFacade.ClaimResult claimed =
                facades.mail().claimMail(sent.mailId(), recipient, EconomyBoundaryFixture.newKey());

        assertThat(claimed).isEqualTo(MailFacade.ClaimResult.OK);
        assertThat(fixture.totalQuantity(recipient, kunai)).isEqualTo(total);
        assertThat(fixture.gold(recipient)).isEqualTo(attachedGold);
        assertThat(fixture.rowQuantities(recipient, kunai))
                .hasSize((total + maxStack - 1) / maxStack)
                .allMatch(rowQuantity -> rowQuantity > 0 && rowQuantity <= maxStack);
    }

    /**
     * Mặt kia của bản vá trên: dồn theo loại KHÔNG được làm lần nhận nào tốn ô hơn cách cấp từng
     * dòng trước đây. Túi 19/20 ô có sẵn chồng 30 phi đao, thư hai dòng × 60: dòng đầu vẫn gộp vào
     * chồng sẵn có (90), chỉ dòng sau mở MỘT ô — dồn cả 120 lại rồi mới cấp thì cần hai ô và hỏng.
     */
    @Test
    void shouldStillMergeEachLineIntoAnExistingStackFirst_whenClaimingIntoANearlyFullBag() {
        int held = 30;
        int perLine = 60;
        int maxStack = fixture.maxStack(kunai);
        long sender = fixture.newCharacter(Postage.BASE_FEE);
        fixture.addRow(sender, kunai, maxStack);
        fixture.addRow(sender, kunai, 2 * perLine - maxStack);
        long recipient = fixture.newCharacter(0L);
        fixture.addRow(recipient, kunai, held);
        fixture.addRows(recipient, sword, 1, BAG_CAPACITY - 2);

        MailFacade.SendOutcome sent = facades.mail()
                .sendMail(new MailFacade.SendMailRequest(
                        sender, recipient, "two lines", "", 0L, EconomyBoundaryFixture.attachments(kunai, perLine, 2)));
        MailFacade.ClaimResult claimed =
                facades.mail().claimMail(sent.mailId(), recipient, EconomyBoundaryFixture.newKey());

        assertThat(claimed).isEqualTo(MailFacade.ClaimResult.OK);
        assertThat(fixture.rowQuantities(recipient, kunai)).containsExactly(held + perLine, perLine);
        assertThat(fixture.rowCount(recipient)).isEqualTo(BAG_CAPACITY);
    }

    // ---------------------------------------------------------------- giao dịch

    @Test
    void shouldSwapExactlyTheOfferedGold_whenTradingAtEveryGoldEdge() {
        long balance = 700L;
        for (long offered : new long[] {1L, balance - 1, balance, balance + 1, Long.MAX_VALUE}) {
            long giver = fixture.newCharacter(balance);
            long taker = fixture.newCharacter(balance);
            long sessionId = EconomyBoundaryFixture.openTrade(facades.trade(), giver, taker);
            facades.trade().setOffer(sessionId, giver, offered, List.of());
            facades.trade().setOffer(sessionId, taker, balance, List.of());
            boolean affordable = offered <= balance;

            facades.trade().confirm(sessionId, giver, EconomyBoundaryFixture.newKey());
            TradeFacade.ConfirmResult result =
                    facades.trade().confirm(sessionId, taker, EconomyBoundaryFixture.newKey());

            String scenario = "chào %d vàng trên %d".formatted(offered, balance);
            assertThat(result)
                    .as(scenario)
                    .isEqualTo(
                            affordable ? TradeFacade.ConfirmResult.EXECUTED : TradeFacade.ConfirmResult.OFFER_INVALID);
            assertThat(fixture.gold(giver))
                    .as(scenario + " — bên chào")
                    .isEqualTo(affordable ? 2 * balance - offered : balance);
            assertThat(fixture.gold(taker)).as(scenario + " — bên kia").isEqualTo(affordable ? offered : balance);
        }
    }

    @Test
    void shouldMoveExactlyTheOfferedQuantity_whenTradingAtEveryQuantityEdge() {
        int owned = 40;
        for (int quantity : new int[] {1, owned - 1, owned, owned + 1, Integer.MAX_VALUE}) {
            long giver = fixture.newCharacter(0L);
            long itemId = fixture.addRow(giver, kunai, owned);
            long taker = fixture.newCharacter(0L);
            boolean owns = quantity <= owned;

            TradeFacade.ConfirmResult result =
                    tradeItems(giver, taker, List.of(new TradeFacade.OfferItemInput(itemId, quantity)));

            String scenario = "chào %d trên %d".formatted(quantity, owned);
            assertThat(result)
                    .as(scenario)
                    .isEqualTo(owns ? TradeFacade.ConfirmResult.EXECUTED : TradeFacade.ConfirmResult.OFFER_INVALID);
            assertThat(fixture.totalQuantity(giver, kunai))
                    .as(scenario + " — bên chào còn")
                    .isEqualTo(owns ? owned - quantity : owned);
            assertThat(fixture.totalQuantity(taker, kunai))
                    .as(scenario + " — bên kia có")
                    .isEqualTo(owns ? quantity : 0);
        }
    }

    /** Cùng một dòng túi chào HAI lần: tổng đúng bằng số đang có thì đi, hơn một cái thì hỏng sạch. */
    @Test
    void shouldCountTheSameRowOfferedTwiceAgainstItsRealQuantity() {
        int owned = 40;
        int firstLine = 25;
        for (int secondLine : new int[] {owned - firstLine, owned - firstLine + 1}) {
            long giver = fixture.newCharacter(0L);
            long itemId = fixture.addRow(giver, kunai, owned);
            long taker = fixture.newCharacter(0L);
            boolean owns = firstLine + secondLine <= owned;

            TradeFacade.ConfirmResult result = tradeItems(
                    giver,
                    taker,
                    List.of(
                            new TradeFacade.OfferItemInput(itemId, firstLine),
                            new TradeFacade.OfferItemInput(itemId, secondLine)));

            String scenario = "chào %d + %d trên %d".formatted(firstLine, secondLine, owned);
            assertThat(result)
                    .as(scenario)
                    .isEqualTo(owns ? TradeFacade.ConfirmResult.EXECUTED : TradeFacade.ConfirmResult.OFFER_INVALID);
            assertThat(fixture.totalQuantity(giver, kunai))
                    .as(scenario + " — bên chào còn")
                    .isEqualTo(owns ? 0 : owned);
            assertThat(fixture.totalQuantity(taker, kunai))
                    .as(scenario + " — bên kia có")
                    .isEqualTo(owns ? owned : 0);
        }
    }

    /** Bên nhận đã ở đúng trần vàng: giao dịch hỏng TRỌN, không ai mất đồng nào. */
    @Test
    void shouldLeaveBothSidesUntouched_whenTradedGoldWouldOverflowTheReceiver() {
        long giver = fixture.newCharacter(10L);
        long taker = fixture.newCharacter(GoldLedger.MAX_GOLD);
        long sessionId = EconomyBoundaryFixture.openTrade(facades.trade(), giver, taker);
        facades.trade().setOffer(sessionId, giver, 1L, List.of());
        facades.trade().confirm(sessionId, giver, EconomyBoundaryFixture.newKey());

        assertThatThrownBy(() -> facades.trade().confirm(sessionId, taker, EconomyBoundaryFixture.newKey()))
                .isInstanceOf(GoldCapExceededException.class);

        assertThat(fixture.gold(giver)).isEqualTo(10L);
        assertThat(fixture.gold(taker)).isEqualTo(GoldLedger.MAX_GOLD);
        facades.trade().cancel(sessionId, giver);
    }

    private static TradeFacade.ConfirmResult tradeItems(
            long giver, long taker, List<TradeFacade.OfferItemInput> items) {
        long sessionId = EconomyBoundaryFixture.openTrade(facades.trade(), giver, taker);
        facades.trade().setOffer(sessionId, giver, 0L, items);
        facades.trade().confirm(sessionId, giver, EconomyBoundaryFixture.newKey());
        TradeFacade.ConfirmResult result = facades.trade().confirm(sessionId, taker, EconomyBoundaryFixture.newKey());
        if (result != TradeFacade.ConfirmResult.EXECUTED) {
            facades.trade().cancel(sessionId, giver);
        }
        return result;
    }

    // ---------------------------------------------------------------- cường hoá

    @Test
    void shouldChargeTheWholePurse_whenEnhancingWithExactlyTheCost_andNothingWhenOneShort() {
        long cost = EnhancementTable.goldCost(0);

        long exact = fixture.newCharacter(cost);
        long exactItem = fixture.addRow(exact, sword, 1);
        InventoryFacade.EnhanceResult paid =
                facades.inventory().enhanceItem(exact, exactItem, false, EconomyBoundaryFixture.newKey());
        assertThat(paid).isIn(InventoryFacade.EnhanceResult.SUCCESS, InventoryFacade.EnhanceResult.FAILED_NO_DROP);
        assertThat(fixture.gold(exact)).isZero();
        assertThat(fixture.enhanceLevel(exactItem)).isEqualTo(paid == InventoryFacade.EnhanceResult.SUCCESS ? 1 : 0);

        long oneShort = fixture.newCharacter(cost - 1);
        long shortItem = fixture.addRow(oneShort, sword, 1);
        String before = fixture.snapshot(oneShort);
        assertThat(facades.inventory().enhanceItem(oneShort, shortItem, false, EconomyBoundaryFixture.newKey()))
                .isEqualTo(InventoryFacade.EnhanceResult.INSUFFICIENT_GOLD);
        assertThat(fixture.snapshot(oneShort)).isEqualTo(before);
    }
}
