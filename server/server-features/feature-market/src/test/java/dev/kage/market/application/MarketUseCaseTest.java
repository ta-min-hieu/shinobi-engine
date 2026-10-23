package dev.kage.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kage.market.domain.ListingDraft;
import dev.kage.market.domain.ListingRequest;
import dev.kage.market.domain.MarketGoldPort;
import dev.kage.market.domain.MarketInventoryPort;
import dev.kage.market.domain.MarketListing;
import dev.kage.market.domain.MarketOutcome;
import dev.kage.market.domain.MarketPolicy;
import dev.kage.market.domain.MarketPort;
import dev.kage.market.domain.MarketRecordPort;
import dev.kage.market.domain.Transaction;
import dev.kage.market.domain.TransactionRunner;

/**
 * Luật phường thị (ADR-063): phí 5% biến mất, nhánh từ chối không đổi gì (rollback trọn, kể cả khoá chống
 * trùng), không mua của chính mình, trần dòng đang treo. Fake ghi nhật ký thay đổi và HOÀN TÁC khi
 * transaction ném — đúng hành vi rollback của Postgres.
 */
class MarketUseCaseTest {

    private static final long SELLER = 1L;
    private static final long BUYER = 2L;
    private static final long KUNAI = 50L;
    private static final long KUNAI_ROW = 900L;
    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    private final World world = new World();
    private final MarketUseCase market = new MarketUseCase(
            new MarketUseCase.Ports(world, world, world, world), world, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void feeShouldBeFivePercentRoundedDown_withAFloorOfOne() {
        assertThat(MarketPolicy.feeOf(1000)).isEqualTo(50);
        assertThat(MarketPolicy.feeOf(10)).isEqualTo(1);
        assertThat(MarketPolicy.proceedsOf(1999)).isEqualTo(1999 - 99);
    }

    @Test
    void listThenBuy_shouldMoveItemAndGold_andBurnTheFee() {
        world.bag.put(KUNAI_ROW, 10);
        world.gold.put(BUYER, 5_000L);

        assertThat(market.create(new ListingRequest(SELLER, KUNAI_ROW, 4, 1_000, "k1")))
                .isEqualTo(MarketOutcome.OK);
        long listingId = world.listings.keySet().iterator().next();
        MarketUseCase.BuyResult bought = market.buy(BUYER, listingId, "k2");

        assertThat(bought.outcome()).isEqualTo(MarketOutcome.OK);
        assertThat(world.bag.get(KUNAI_ROW)).isEqualTo(6);
        assertThat(world.given).containsExactly(BUYER + ":" + KUNAI + "x4");
        assertThat(world.gold.get(BUYER)).isEqualTo(4_000L);
        assertThat(world.gold.get(SELLER)).isEqualTo(950L);
        assertThat(bought.sale()).map(MarketUseCase.Sale::proceeds).contains(950L);
    }

    @Test
    void refusalsShouldChangeNothing_andLeaveTheKeyReusable() {
        world.bag.put(KUNAI_ROW, 10);
        world.gold.put(BUYER, 10L);
        market.create(new ListingRequest(SELLER, KUNAI_ROW, 4, 1_000, "k1"));
        long listingId = world.listings.keySet().iterator().next();

        assertThat(market.buy(BUYER, listingId, "k2").outcome()).isEqualTo(MarketOutcome.INSUFFICIENT_GOLD);
        assertThat(world.gold).containsEntry(BUYER, 10L).doesNotContainKey(SELLER);
        assertThat(world.keys).as("khoá của lần bị từ chối không bị tiêu").containsExactly("k1");
        assertThat(market.buy(SELLER, listingId, "k3").outcome()).isEqualTo(MarketOutcome.OWN_LISTING);

        world.gold.put(BUYER, 5_000L);
        assertThat(market.buy(BUYER, listingId, "k2").outcome()).isEqualTo(MarketOutcome.OK);
        assertThat(market.buy(BUYER, listingId, "k4").outcome()).as("đã bán").isEqualTo(MarketOutcome.NOT_FOUND);
    }

    @Test
    void buyShouldTouchGoldRowsInAscendingCharacterId_whicheverSideIsLower() {
        // Hai người mua chéo dòng của nhau cùng lúc: mỗi bên chỉ khoá túi đồ CỦA MÌNH, nên vòng khoá duy nhất
        // là hai dòng vàng. Trừ-người-mua-rồi-cộng-người-bán khoá A→B ở bên này, B→A ở bên kia ⇒ deadlock.
        world.bag.put(KUNAI_ROW, 10);
        world.gold.put(BUYER, 5_000L);
        market.create(new ListingRequest(SELLER, KUNAI_ROW, 1, 1_000, "k1"));
        market.buy(BUYER, world.listings.keySet().iterator().next(), "k2");

        assertThat(world.goldTouched).as("người bán id nhỏ hơn").containsExactly(SELLER, BUYER);

        long highSeller = BUYER + 1;
        world.goldTouched.clear();
        world.bagOwner = highSeller;
        market.create(new ListingRequest(highSeller, KUNAI_ROW, 1, 1_000, "k3"));
        market.buy(BUYER, world.listings.keySet().iterator().next(), "k4");

        assertThat(world.goldTouched).as("người bán id lớn hơn").containsExactly(BUYER, highSeller);
        assertThat(world.gold).containsEntry(BUYER, 3_000L).containsEntry(highSeller, 950L);
    }

    @Test
    void sameKeyTwice_shouldListOnce() {
        world.bag.put(KUNAI_ROW, 10);

        market.create(new ListingRequest(SELLER, KUNAI_ROW, 2, 100, "same"));

        assertThat(market.create(new ListingRequest(SELLER, KUNAI_ROW, 2, 100, "same")))
                .isEqualTo(MarketOutcome.DUPLICATE_REQUEST);
        assertThat(world.bag.get(KUNAI_ROW)).isEqualTo(8);
    }

    @Test
    void shouldCapActiveListings_andRejectBadPrices() {
        world.bag.put(KUNAI_ROW, 99);
        for (int i = 0; i < MarketPolicy.MAX_ACTIVE_LISTINGS; i++) {
            assertThat(market.create(new ListingRequest(SELLER, KUNAI_ROW, 1, 100, "k" + i)))
                    .isEqualTo(MarketOutcome.OK);
        }

        assertThat(market.create(new ListingRequest(SELLER, KUNAI_ROW, 1, 100, "over")))
                .isEqualTo(MarketOutcome.TOO_MANY_LISTINGS);
        assertThat(market.create(new ListingRequest(SELLER, KUNAI_ROW, 1, 0, "zero")))
                .isEqualTo(MarketOutcome.INVALID_PRICE);
        assertThat(market.create(new ListingRequest(SELLER, KUNAI_ROW, 1, MarketPolicy.MAX_PRICE + 1, "huge")))
                .isEqualTo(MarketOutcome.INVALID_PRICE);
        assertThat(world.bag.get(KUNAI_ROW)).isEqualTo(99 - MarketPolicy.MAX_ACTIVE_LISTINGS);
    }

    /** Một "DB" trong bộ nhớ có rollback: ảnh chụp trước transaction, ném thì khôi phục. */
    private static final class World
            implements MarketPort,
                    MarketInventoryPort,
                    MarketGoldPort,
                    MarketRecordPort,
                    TransactionRunner,
                    Transaction {
        final Map<Long, Integer> bag = new HashMap<>();
        final Map<Long, Long> gold = new HashMap<>();
        final Map<Long, MarketListing> listings = new HashMap<>();
        final Set<String> keys = new HashSet<>();
        final List<String> given = new ArrayList<>();
        /** Thứ tự các dòng vàng bị chạm — Postgres khoá dòng {@code character_stat} theo đúng thứ tự này. */
        final List<Long> goldTouched = new ArrayList<>();
        /** Chủ của các dòng trong {@link #bag}. */
        long bagOwner = SELLER;

        long nextId = 1;

        // PMD AvoidCatchingGenericException: đúng ý — fake phải hoàn tác với MỌI lỗi, như Postgres rollback.
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        @Override
        public void inTransaction(java.util.function.Consumer<Transaction> action) {
            Map<Long, Integer> bagBefore = new HashMap<>(bag);
            Map<Long, Long> goldBefore = new HashMap<>(gold);
            Map<Long, MarketListing> listingsBefore = new HashMap<>(listings);
            Set<String> keysBefore = new HashSet<>(keys);
            List<String> givenBefore = new ArrayList<>(given);
            try {
                action.accept(this);
            } catch (RuntimeException e) {
                bag.clear();
                bag.putAll(bagBefore);
                gold.clear();
                gold.putAll(goldBefore);
                listings.clear();
                listings.putAll(listingsBefore);
                keys.clear();
                keys.addAll(keysBefore);
                given.clear();
                given.addAll(givenBefore);
                throw e;
            }
        }

        @Override
        public Optional<MarketListing> lockActive(Transaction tx, long listingId, Instant now) {
            return Optional.ofNullable(listings.get(listingId));
        }

        @Override
        public int countActive(Transaction tx, long sellerId) {
            return (int) listings.values().stream()
                    .filter(l -> l.sellerId() == sellerId)
                    .count();
        }

        @Override
        public long insert(Transaction tx, ListingDraft draft) {
            long id = nextId++;
            listings.put(
                    id,
                    new MarketListing(
                            id,
                            draft.sellerId(),
                            "S",
                            draft.itemTemplateId(),
                            draft.quantity(),
                            draft.price(),
                            draft.expiresAt()));
            return id;
        }

        @Override
        public void markSold(Transaction tx, long listingId, long buyerId, Instant at) {
            listings.remove(listingId);
        }

        @Override
        public void markCancelled(Transaction tx, long listingId) {
            listings.remove(listingId);
        }

        @Override
        public List<MarketListing> browse(Optional<Long> itemTemplateId, Instant now, int limit) {
            return List.copyOf(listings.values());
        }

        @Override
        public List<MarketListing> listActiveBySeller(long sellerId, Instant now) {
            return List.of();
        }

        @Override
        public int expireDue(Instant now) {
            return 0;
        }

        @Override
        public List<MarketListing> unreturnedExpired(int limit) {
            return List.of();
        }

        @Override
        public void markReturned(long listingId) {
            // không có hết hạn trong các ca này
        }

        @Override
        public void lock(Transaction tx, long characterId) {
            // một luồng — khoá không có gì để làm
        }

        @Override
        public TakeResult take(Transaction tx, long characterId, long inventoryItemId, int quantity) {
            Integer have = bag.get(inventoryItemId);
            if (characterId != bagOwner || have == null) {
                return new TakeResult.Refused(MarketOutcome.NOT_FOUND);
            }
            if (quantity > have) {
                return new TakeResult.Refused(MarketOutcome.INVALID_QUANTITY);
            }
            bag.put(inventoryItemId, have - quantity);
            return new TakeResult.Taken(KUNAI);
        }

        @Override
        public boolean give(Transaction tx, long characterId, long itemTemplateId, int quantity) {
            given.add(characterId + ":" + itemTemplateId + "x" + quantity);
            return true;
        }

        @Override
        public boolean deduct(Transaction tx, long characterId, long amount) {
            goldTouched.add(characterId);
            long have = gold.getOrDefault(characterId, 0L);
            if (have < amount) {
                return false;
            }
            gold.put(characterId, have - amount);
            return true;
        }

        @Override
        public void credit(Transaction tx, long characterId, long amount) {
            goldTouched.add(characterId);
            gold.merge(characterId, amount, Long::sum);
        }

        @Override
        public void recordKey(Transaction tx, String idempotencyKey) {
            if (!keys.add(idempotencyKey)) {
                throw new DuplicateRequestException(null);
            }
        }

        @Override
        public void audit(Transaction tx, long characterId, String action, String detailJson) {
            // không kiểm audit ở tầng này — có test Postgres
        }
    }
}
